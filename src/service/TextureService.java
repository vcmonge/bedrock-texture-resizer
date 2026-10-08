package service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javax.imageio.ImageIO;
import model.TextureInfo;
import util.Constants;
import util.TGAHandler;

/**
 * Servicio para cargar y gestionar texturas del sistema de archivos.
 * Implementa carga paralela para optimizar el rendimiento con muchos archivos.
 * 
 * Responsabilidades:
 * - Escanear directorios de texturas
 * - Cargar información de cada textura
 * - Detectar texturas asociadas MER (_mer, _mers) y normales (_normal, _n)
 * - Generar miniaturas para preview
 * 
 * Principio de Responsabilidad Única: Solo gestiona la carga de texturas.
 * 
 * @author vmonge
 */
public class TextureService {
    
    // ==================== CAMPOS ====================
    
    /** Procesador de imágenes para redimensionamiento */
    private final ImageProcessor imageProcessor;
    
    /** Pool de hilos para carga paralela */
    private final ExecutorService executorService;
    
    /** Extensiones soportadas para búsqueda */
    private final List<String> supportedExtensions;
    
    // ==================== CONSTRUCTORES ====================
    
    /**
     * Crea un servicio con la configuración por defecto.
     */
    public TextureService() {
        this(new ImageProcessor());
    }
    
    /**
     * Crea un servicio con procesador personalizado.
     * 
     * @param imageProcessor Procesador de imágenes a usar
     */
    public TextureService(ImageProcessor imageProcessor) {
        this.imageProcessor = imageProcessor;
        this.executorService = Executors.newFixedThreadPool(Constants.THREAD_POOL_SIZE);
        this.supportedExtensions = Arrays.asList(Constants.SUPPORTED_EXTENSIONS);
    }
    
    // ==================== CARGA DE TEXTURAS ====================
    
    /**
     * Carga todas las texturas de bloques desde el directorio raíz.
     * 
     * @param rootDirectory Directorio raíz del resource pack
     * @param progressCallback Callback para reportar progreso (puede ser null)
     * @return Lista de texturas de bloques encontradas
     * @throws IOException Si hay error de lectura
     */
    public List<TextureInfo> loadBlockTextures(File rootDirectory, 
            Consumer<String> progressCallback) throws IOException {
        
        File blocksDir = new File(rootDirectory, Constants.BLOCKS_PATH);
        return loadTexturesFromDirectory(blocksDir, progressCallback);
    }
    
    /**
     * Carga todas las texturas de items desde el directorio raíz.
     * 
     * @param rootDirectory Directorio raíz del resource pack
     * @param progressCallback Callback para reportar progreso (puede ser null)
     * @return Lista de texturas de items encontradas
     * @throws IOException Si hay error de lectura
     */
    public List<TextureInfo> loadItemTextures(File rootDirectory, 
            Consumer<String> progressCallback) throws IOException {
        
        File itemsDir = new File(rootDirectory, Constants.ITEMS_PATH);
        return loadTexturesFromDirectory(itemsDir, progressCallback);
    }
    
    /**
     * Carga todas las texturas de entorno desde el directorio raíz.
     * 
     * @param rootDirectory Directorio raíz del resource pack
     * @param progressCallback Callback para reportar progreso (puede ser null)
     * @return Lista de texturas de entorno encontradas
     * @throws IOException Si hay error de lectura
     */
    public List<TextureInfo> loadEnvironmentTextures(File rootDirectory, 
            Consumer<String> progressCallback) throws IOException {
        
        File environmentDir = new File(rootDirectory, Constants.ENVIRONMENT_PATH);
        return loadTexturesFromDirectory(environmentDir, progressCallback);
    }
    
    /**
     * Carga todas las texturas de entidades desde el directorio raíz.
     * 
     * @param rootDirectory Directorio raíz del resource pack
     * @param progressCallback Callback para reportar progreso (puede ser null)
     * @return Lista de texturas de entidades encontradas
     * @throws IOException Si hay error de lectura
     */
    public List<TextureInfo> loadEntityTextures(File rootDirectory, 
            Consumer<String> progressCallback) throws IOException {
        
        File entityDir = new File(rootDirectory, Constants.ENTITY_PATH);
        return loadTexturesFromDirectory(entityDir, progressCallback);
    }
    
    /**
     * Carga texturas de un directorio específico.
     * Filtra automáticamente las texturas base (sin sufijos asociados MER o normal).
     * 
     * @param directory Directorio a escanear
     * @param progressCallback Callback para reportar progreso
     * @return Lista de texturas encontradas
     * @throws IOException Si hay error de lectura
     */
    public List<TextureInfo> loadTexturesFromDirectory(File directory, 
            Consumer<String> progressCallback) throws IOException {
        
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            reportProgress(progressCallback, "Directorio no válido: " + 
                    (directory != null ? directory.getAbsolutePath() : "null"));
            return new ArrayList<>();
        }
        
        reportProgress(progressCallback, "Escaneando: " + directory.getAbsolutePath());
        
        // Obtener todos los archivos de imagen
        List<File> allImageFiles = scanForImages(directory);
        reportProgress(progressCallback, "Encontrados " + allImageFiles.size() + " archivos de imagen");
        
        // Crear mapa para búsqueda rápida de archivos asociados
        Map<String, File> fileMap = buildFileMap(allImageFiles);
        
        // Filtrar solo texturas base (sin sufijos asociados MER o normal)
        List<File> baseFiles = filterBaseTextures(allImageFiles);
        reportProgress(progressCallback, "Texturas base: " + baseFiles.size());
        
        // Cargar información de cada textura en paralelo
        return loadTextureInfoParallel(baseFiles, fileMap, progressCallback);
    }
    
    /**
     * Escanea recursivamente un directorio buscando archivos de imagen.
     * 
     * @param directory Directorio a escanear
     * @return Lista de archivos de imagen encontrados
     */
    private List<File> scanForImages(File directory) {
        List<File> images = new ArrayList<>();
        scanDirectoryRecursive(directory, images);
        return images;
    }
    
    /**
     * Escaneo recursivo de directorios.
     * 
     * @param directory Directorio actual
     * @param accumulator Lista donde acumular resultados
     */
    private void scanDirectoryRecursive(File directory, List<File> accumulator) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectoryRecursive(file, accumulator);
            } else if (isImageFile(file)) {
                accumulator.add(file);
            }
        }
    }
    
    /**
     * Verifica si un archivo es una imagen soportada.
     * 
     * @param file Archivo a verificar
     * @return true si es una imagen soportada
     */
    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        for (String ext : supportedExtensions) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Construye un mapa de nombre base -> archivo para búsqueda rápida.
     * La clave es la ruta relativa sin extensión.
     * 
     * @param files Lista de archivos
     * @return Mapa de nombres a archivos
     */
    private Map<String, File> buildFileMap(List<File> files) {
        Map<String, File> map = new HashMap<>();
        for (File file : files) {
            String key = getFileKey(file);
            map.put(key, file);
        }
        return map;
    }
    
    /**
     * Genera una clave única para un archivo (ruta sin extensión).
     * 
     * @param file Archivo
     * @return Clave única
     */
    private String getFileKey(File file) {
        String path = file.getAbsolutePath();
        int dotIndex = path.lastIndexOf('.');
        return dotIndex > 0 ? path.substring(0, dotIndex) : path;
    }
    
    /**
     * Filtra los archivos para obtener solo las texturas base.
     * Excluye archivos con cualquiera de los sufijos asociados MER o normal.
     * 
     * @param files Lista completa de archivos
     * @return Lista de texturas base
     */
    private List<File> filterBaseTextures(List<File> files) {
        List<File> baseTextures = new ArrayList<>();
        
        for (File file : files) {
            String baseName = getBaseName(file);
            if (!isAssociatedSuffix(baseName)) {
                baseTextures.add(file);
            }
        }
        
        return baseTextures;
    }

    /**
     * Indica si un nombre sin extensión corresponde a una textura asociada.
     *
     * @param baseName Nombre del archivo sin extensión
     * @return true si termina en un sufijo MER o normal soportado
     */
    private boolean isAssociatedSuffix(String baseName) {
        for (String suffix : Constants.MER_SUFFIXES) {
            if (baseName.endsWith(suffix)) {
                return true;
            }
        }
        for (String suffix : Constants.NORMAL_SUFFIXES) {
            if (baseName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Obtiene el nombre base de un archivo (sin extensión).
     * 
     * @param file Archivo
     * @return Nombre sin extensión
     */
    private String getBaseName(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }
    
    /**
     * Carga la información de texturas en paralelo.
     * 
     * @param baseFiles Archivos base a cargar
     * @param fileMap Mapa para buscar archivos asociados
     * @param progressCallback Callback de progreso
     * @return Lista de TextureInfo cargadas
     */
    private List<TextureInfo> loadTextureInfoParallel(List<File> baseFiles, 
            Map<String, File> fileMap, Consumer<String> progressCallback) {
        
        List<TextureInfo> result = new ArrayList<>();
        List<Future<TextureInfo>> futures = new ArrayList<>();
        
        // Crear tareas para carga paralela
        for (File baseFile : baseFiles) {
            futures.add(executorService.submit(
                    new TextureLoadTask(baseFile, fileMap, progressCallback)));
        }
        
        // Recolectar resultados
        int completed = 0;
        for (Future<TextureInfo> future : futures) {
            try {
                TextureInfo info = future.get();
                if (info != null) {
                    result.add(info);
                }
                completed++;
                
                if (completed % 50 == 0) {
                    reportProgress(progressCallback, 
                            String.format("Cargadas %d/%d texturas", completed, baseFiles.size()));
                }
            } catch (Exception e) {
                // Continuar con las demás si una falla
                System.err.println("Error cargando textura: " + e.getMessage());
            }
        }
        
        reportProgress(progressCallback, "Carga completada: " + result.size() + " texturas");
        return result;
    }
    
    // ==================== TAREA DE CARGA ====================
    
    /**
     * Tarea para cargar una textura en un hilo separado.
     */
    private class TextureLoadTask implements Callable<TextureInfo> {
        
        private final File baseFile;
        private final Map<String, File> fileMap;
        private final Consumer<String> progressCallback;
        
        public TextureLoadTask(File baseFile, Map<String, File> fileMap, 
                Consumer<String> progressCallback) {
            this.baseFile = baseFile;
            this.fileMap = fileMap;
            this.progressCallback = progressCallback;
        }
        
        @Override
        public TextureInfo call() throws Exception {
            try {
                // Leer dimensiones de la imagen
                int[] dimensions = readImageDimensions(baseFile);
                if (dimensions == null) {
                    return null;
                }
                
                // Crear TextureInfo
                TextureInfo info = new TextureInfo(baseFile, dimensions[0], dimensions[1]);
                
                // Buscar texturas asociadas
                String baseKey = getFileKey(baseFile);
                findAssociatedTextures(info, baseKey, fileMap);
                
                // Generar miniatura
                generateThumbnail(info);
                
                return info;
                
            } catch (Exception e) {
                System.err.println("Error procesando " + baseFile.getName() + ": " + e.getMessage());
                return null;
            }
        }
    }
    
    /**
     * Lee las dimensiones de una imagen sin cargarla completamente en memoria.
     * Para PNG usa ImageIO, para TGA lee el header directamente.
     * 
     * @param file Archivo de imagen
     * @return Array [ancho, alto] o null si hay error
     */
    private int[] readImageDimensions(File file) {
        try {
            String extension = getExtension(file).toLowerCase();
            
            if (extension.equals(Constants.TGA_EXTENSION)) {
                // Para TGA, leer dimensiones del header
                return readTGADimensions(file);
            } else {
                // Para PNG y otros, usar ImageIO
                try (javax.imageio.stream.ImageInputStream iis = 
                        ImageIO.createImageInputStream(file)) {
                    
                    java.util.Iterator<javax.imageio.ImageReader> readers = 
                            ImageIO.getImageReaders(iis);
                    
                    if (readers.hasNext()) {
                        javax.imageio.ImageReader reader = readers.next();
                        try {
                            reader.setInput(iis);
                            return new int[] {reader.getWidth(0), reader.getHeight(0)};
                        } finally {
                            reader.dispose();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error leyendo dimensiones de " + file.getName() + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Lee las dimensiones de un archivo TGA desde su header.
     * 
     * @param file Archivo TGA
     * @return Array [ancho, alto]
     * @throws IOException Si hay error de lectura
     */
    private int[] readTGADimensions(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[18];
            if (fis.read(header) == 18) {
                int width = (header[12] & 0xFF) | ((header[13] & 0xFF) << 8);
                int height = (header[14] & 0xFF) | ((header[15] & 0xFF) << 8);
                return new int[] {width, height};
            }
        }
        return null;
    }
    
    /**
     * Busca y asocia todas las variantes MER y de normales soportadas.
     * 
     * @param info TextureInfo a actualizar
     * @param baseKey Clave base del archivo
     * @param fileMap Mapa de archivos
     */
    private void findAssociatedTextures(TextureInfo info, String baseKey, 
            Map<String, File> fileMap) {
        
        for (String suffix : Constants.MER_SUFFIXES) {
            File merFile = fileMap.get(baseKey + suffix);
            if (merFile != null) {
                info.addMerFile(merFile);
            }
        }
        
        for (String suffix : Constants.NORMAL_SUFFIXES) {
            File normalFile = fileMap.get(baseKey + suffix);
            if (normalFile != null) {
                info.addNormalFile(normalFile);
            }
        }
    }
    
    /**
     * Genera una imagen miniatura para preview en la UI.
     * 
     * Optimización clave: en lugar de cargar la imagen completa en memoria
     * (lo cual consume ~4 MB por una textura 1024x1024), se decodifica/reduce
     * al tamaño objetivo definido en {@link Constants#THUMBNAIL_RENDER_SIZE}.
     * Esto reduce el consumo de heap en ~25x para texturas grandes.
     * 
     * - PNG: usa el decodificador nativo de JavaFX con dimensiones objetivo,
     *   evitando crear el BufferedImage completo intermedio.
     * - TGA: como debemos leer el archivo nosotros, reducimos el BufferedImage
     *   con el AreaResampler antes de convertirlo a Image JavaFX.
     * 
     * @param info TextureInfo a actualizar
     */
    private void generateThumbnail(TextureInfo info) {
        try {
            File file = info.getBaseFile();
            String extension = getExtension(file).toLowerCase();
            int target = Constants.THUMBNAIL_RENDER_SIZE;
            
            Image thumbnail;
            if (extension.equals(Constants.TGA_EXTENSION)) {
                BufferedImage original = imageProcessor.readImage(file);
                if (original == null) {
                    return;
                }
                BufferedImage scaled = scaleToThumbnail(original, target);
                thumbnail = SwingFXUtils.toFXImage(scaled, null);
            } else {
                // Decodificación nativa al tamaño objetivo: NO carga la imagen
                // completa en memoria. preserveRatio=true, smooth=false (pixel art).
                String uri = file.toURI().toString();
                thumbnail = new Image(uri, target, target, true, false, false);
                if (thumbnail.isError()) {
                    return;
                }
            }
            
            info.setThumbnailImage(thumbnail);
        } catch (Exception e) {
            System.err.println("Error generando miniatura para " + 
                    info.getBaseFile().getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Reduce un BufferedImage al tamaño de miniatura preservando el aspecto.
     * Devuelve el original sin copia si ya cabe en el tamaño objetivo.
     * 
     * @param original Imagen original
     * @param target Tamaño máximo (ancho o alto) en píxeles
     * @return Imagen reducida (o el original si no requiere escala)
     */
    private BufferedImage scaleToThumbnail(BufferedImage original, int target) {
        int w = original.getWidth();
        int h = original.getHeight();
        if (w <= target && h <= target) {
            return original;
        }
        double ratio = Math.min((double) target / w, (double) target / h);
        int newW = Math.max(1, (int) Math.round(w * ratio));
        int newH = Math.max(1, (int) Math.round(h * ratio));
        return AreaResampler.resize(original, newW, newH);
    }
    
    /**
     * Obtiene la extensión de un archivo.
     * 
     * @param file Archivo
     * @return Extensión con punto o cadena vacía
     */
    private String getExtension(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(dotIndex) : "";
    }
    
    // ==================== PROCESAMIENTO DE TEXTURAS ====================
    
    /**
     * Procesa una textura: reduce resolución de la base y archivos asociados.
     * 
     * @param textureInfo Información de la textura a procesar
     * @throws IOException Si hay error de procesamiento
     */
    public void processTexture(TextureInfo textureInfo) throws IOException {
        // Procesar archivo base
        imageProcessor.processFile(textureInfo.getBaseFile());
        
        // Procesar todas las variantes MER asociadas
        for (File merFile : textureInfo.getMerFiles()) {
            imageProcessor.processDataMap(merFile);
        }
        
        // Procesar todas las variantes de normales asociadas
        for (File normalFile : textureInfo.getNormalFiles()) {
            imageProcessor.processDataMap(normalFile);
        }
        
        // Marcar como procesada
        textureInfo.setProcessed(true);
    }
    
    /**
     * Procesa una textura de forma asíncrona.
     * 
     * @param textureInfo Textura a procesar
     * @param onSuccess Callback en caso de éxito (ejecutado en hilo de JavaFX)
     * @param onError Callback en caso de error (ejecutado en hilo de JavaFX)
     */
    public void processTextureAsync(TextureInfo textureInfo, 
            Runnable onSuccess, Consumer<Exception> onError) {
        
        executorService.submit(() -> {
            try {
                processTexture(textureInfo);
                
                // Notificar éxito en el hilo de JavaFX
                if (onSuccess != null) {
                    Platform.runLater(onSuccess);
                }
            } catch (Exception e) {
                // Notificar error en el hilo de JavaFX
                if (onError != null) {
                    Platform.runLater(() -> onError.accept(e));
                }
            }
        });
    }
    
    // ==================== UTILIDADES ====================
    
    /**
     * Reporta progreso a través del callback si está disponible.
     * 
     * @param callback Callback de progreso (puede ser null)
     * @param message Mensaje a reportar
     */
    private void reportProgress(Consumer<String> callback, String message) {
        if (callback != null) {
            Platform.runLater(() -> callback.accept(message));
        }
    }
    
    /**
     * Verifica si un directorio contiene texturas válidas.
     * 
     * @param rootDirectory Directorio raíz a verificar
     * @return true si tiene subdirectorios de texturas
     */
    public boolean hasValidTextureDirectories(File rootDirectory) {
        File blocksDir = new File(rootDirectory, Constants.BLOCKS_PATH);
        File itemsDir = new File(rootDirectory, Constants.ITEMS_PATH);
        File environmentDir = new File(rootDirectory, Constants.ENVIRONMENT_PATH);
        File entityDir = new File(rootDirectory, Constants.ENTITY_PATH);
        return blocksDir.exists() || itemsDir.exists() || 
               environmentDir.exists() || entityDir.exists();
    }
    
    /**
     * Libera recursos del servicio.
     * Debe llamarse al cerrar la aplicación.
     */
    public void shutdown() {
        executorService.shutdown();
    }
    
    /**
     * @return Procesador de imágenes utilizado
     */
    public ImageProcessor getImageProcessor() {
        return imageProcessor;
    }
}
