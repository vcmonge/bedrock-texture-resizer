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
 * Carga (en paralelo) y procesa las texturas del resource pack:
 * - Escanea los directorios de texturas
 * - Lee las dimensiones de cada textura
 * - Detecta sus texturas MER (_mer, _mers) y normales (_normal, _n)
 * - Genera miniaturas de preview
 * 
 * @author vmonge
 */
public class TextureService {
    
    // ==================== CAMPOS ====================
    
    /** Procesador usado para redimensionar. */
    private final ImageProcessor imageProcessor;
    
    /** Pool de hilos para carga y procesamiento. */
    private final ExecutorService executorService;
    
    /** Extensiones de imagen buscadas. */
    private final List<String> supportedExtensions;
    
    // ==================== CONSTRUCTORES ====================
    
    /** Configuración por defecto. */
    public TextureService() {
        this(new ImageProcessor());
    }
    
    /** @param imageProcessor Procesador de imágenes a usar */
    public TextureService(ImageProcessor imageProcessor) {
        this.imageProcessor = imageProcessor;
        this.executorService = Executors.newFixedThreadPool(Constants.THREAD_POOL_SIZE);
        this.supportedExtensions = Arrays.asList(Constants.SUPPORTED_EXTENSIONS);
    }
    
    // ==================== CARGA DE TEXTURAS ====================
    
    /**
     * @param rootDirectory Directorio raíz del resource pack
     * @param progressCallback Callback de progreso (puede ser null)
     * @return Texturas de bloques encontradas
     * @throws IOException Si falla la lectura
     */
    public List<TextureInfo> loadBlockTextures(File rootDirectory, 
            Consumer<String> progressCallback) throws IOException {
        
        File blocksDir = new File(rootDirectory, Constants.BLOCKS_PATH);
        return loadTexturesFromDirectory(blocksDir, progressCallback);
    }
    
    /**
     * @param rootDirectory Directorio raíz del resource pack
     * @param progressCallback Callback de progreso (puede ser null)
     * @return Texturas de items encontradas
     * @throws IOException Si falla la lectura
     */
    public List<TextureInfo> loadItemTextures(File rootDirectory, 
            Consumer<String> progressCallback) throws IOException {
        
        File itemsDir = new File(rootDirectory, Constants.ITEMS_PATH);
        return loadTexturesFromDirectory(itemsDir, progressCallback);
    }
    
    /**
     * @param rootDirectory Directorio raíz del resource pack
     * @param progressCallback Callback de progreso (puede ser null)
     * @return Texturas de entorno encontradas
     * @throws IOException Si falla la lectura
     */
    public List<TextureInfo> loadEnvironmentTextures(File rootDirectory, 
            Consumer<String> progressCallback) throws IOException {
        
        File environmentDir = new File(rootDirectory, Constants.ENVIRONMENT_PATH);
        return loadTexturesFromDirectory(environmentDir, progressCallback);
    }
    
    /**
     * @param rootDirectory Directorio raíz del resource pack
     * @param progressCallback Callback de progreso (puede ser null)
     * @return Texturas de entidades encontradas
     * @throws IOException Si falla la lectura
     */
    public List<TextureInfo> loadEntityTextures(File rootDirectory, 
            Consumer<String> progressCallback) throws IOException {
        
        File entityDir = new File(rootDirectory, Constants.ENTITY_PATH);
        return loadTexturesFromDirectory(entityDir, progressCallback);
    }
    
    /**
     * Carga las texturas base (sin sufijo MER o normal) de un directorio.
     * 
     * @param directory Directorio a escanear
     * @param progressCallback Callback de progreso (puede ser null)
     * @return Texturas encontradas
     * @throws IOException Si falla la lectura
     */
    public List<TextureInfo> loadTexturesFromDirectory(File directory, 
            Consumer<String> progressCallback) throws IOException {
        
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            reportProgress(progressCallback, "Invalid directory: " + 
                    (directory != null ? directory.getAbsolutePath() : "null"));
            return new ArrayList<>();
        }
        
        reportProgress(progressCallback, "Scanning: " + directory.getAbsolutePath());
        
        List<File> allImageFiles = scanForImages(directory);
        reportProgress(progressCallback, "Found " + allImageFiles.size() + " image files");
        
        // Mapa para localizar rápido los archivos asociados
        Map<String, File> fileMap = buildFileMap(allImageFiles);
        
        List<File> baseFiles = filterBaseTextures(allImageFiles);
        reportProgress(progressCallback, "Base textures: " + baseFiles.size());
        
        return loadTextureInfoParallel(baseFiles, fileMap, progressCallback);
    }
    
    /**
     * @param directory Directorio a escanear
     * @return Imágenes soportadas encontradas recursivamente
     */
    private List<File> scanForImages(File directory) {
        List<File> images = new ArrayList<>();
        scanDirectoryRecursive(directory, images);
        return images;
    }
    
    /**
     * @param directory Directorio actual
     * @param accumulator Lista donde se acumulan los resultados
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
    
    /** @return true si la extensión del archivo es soportada */
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
     * @param files Archivos a indexar
     * @return Mapa clave ({@link #getFileKey}) → archivo
     */
    private Map<String, File> buildFileMap(List<File> files) {
        Map<String, File> map = new HashMap<>();
        for (File file : files) {
            String key = getFileKey(file);
            map.put(key, file);
        }
        return map;
    }
    
    /** @return Clave única del archivo: ruta absoluta sin extensión */
    private String getFileKey(File file) {
        String path = file.getAbsolutePath();
        int dotIndex = path.lastIndexOf('.');
        return dotIndex > 0 ? path.substring(0, dotIndex) : path;
    }
    
    /**
     * @param files Todos los archivos
     * @return Solo las texturas base (sin sufijo MER o normal)
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
    
    /** @return Nombre del archivo sin extensión */
    private String getBaseName(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }
    
    /**
     * Carga en paralelo la información de las texturas.
     * 
     * @param baseFiles Archivos base a cargar
     * @param fileMap Mapa para buscar archivos asociados
     * @param progressCallback Callback de progreso
     * @return Texturas cargadas (las que fallan se omiten)
     */
    private List<TextureInfo> loadTextureInfoParallel(List<File> baseFiles, 
            Map<String, File> fileMap, Consumer<String> progressCallback) {
        
        List<TextureInfo> result = new ArrayList<>();
        List<Future<TextureInfo>> futures = new ArrayList<>();
        
        for (File baseFile : baseFiles) {
            futures.add(executorService.submit(
                    new TextureLoadTask(baseFile, fileMap, progressCallback)));
        }
        
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
                            String.format("Loaded %d/%d textures", completed, baseFiles.size()));
                }
            } catch (Exception e) {
                // Si una falla, se continúa con las demás
                System.err.println("Error loading texture: " + e.getMessage());
            }
        }
        
        reportProgress(progressCallback, "Load complete: " + result.size() + " textures");
        return result;
    }
    
    // ==================== TAREA DE CARGA ====================
    
    /** Carga una textura en un hilo del pool. */
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
                int[] dimensions = readImageDimensions(baseFile);
                if (dimensions == null) {
                    return null;
                }
                
                TextureInfo info = new TextureInfo(baseFile, dimensions[0], dimensions[1]);
                
                String baseKey = getFileKey(baseFile);
                findAssociatedTextures(info, baseKey, fileMap);
                
                generateThumbnail(info);
                
                return info;
                
            } catch (Exception e) {
                System.err.println("Error processing " + baseFile.getName() + ": " + e.getMessage());
                return null;
            }
        }
    }
    
    /**
     * Lee las dimensiones sin cargar la imagen: del header en TGA, con un
     * ImageReader en el resto.
     * 
     * @param file Archivo de imagen
     * @return [ancho, alto] o null si hay error
     */
    private int[] readImageDimensions(File file) {
        try {
            String extension = getExtension(file).toLowerCase();
            
            if (extension.equals(Constants.TGA_EXTENSION)) {
                return readTGADimensions(file);
            } else {
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
            System.err.println("Error reading dimensions of " + file.getName() + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * @param file Archivo TGA
     * @return [ancho, alto] leídos del header, o null si está incompleto
     * @throws IOException Si falla la lectura
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
     * Asocia a la textura todas sus variantes MER y de normales existentes.
     * 
     * @param info Textura a actualizar
     * @param baseKey Clave de la textura base
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
     * Genera la miniatura de preview.
     * 
     * En vez de mantener la imagen completa (~4 MB para 1024x1024), se reduce a
     * {@link Constants#THUMBNAIL_RENDER_SIZE}: ~25x menos heap en texturas grandes.
     * 
     * - PNG: el decodificador de JavaFX decodifica directamente al tamaño
     *   objetivo, sin BufferedImage intermedio completo.
     * - TGA: se lee con TGAHandler y se reduce con AreaResampler antes de
     *   convertirla a Image de JavaFX.
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
                // Decodifica al tamaño objetivo sin cargar la imagen completa.
                // preserveRatio=true, smooth=false (pixel art).
                String uri = file.toURI().toString();
                thumbnail = new Image(uri, target, target, true, false, false);
                if (thumbnail.isError()) {
                    return;
                }
            }
            
            info.setThumbnailImage(thumbnail);
        } catch (Exception e) {
            System.err.println("Error generating thumbnail for " + 
                    info.getBaseFile().getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Reduce la imagen al tamaño de miniatura preservando el aspecto.
     * 
     * @param original Imagen original
     * @param target Tamaño máximo (ancho o alto) en píxeles
     * @return Imagen reducida, o el original sin copiar si ya cabe
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
    
    /** @return Extensión con punto, o "" si no tiene */
    private String getExtension(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(dotIndex) : "";
    }
    
    // ==================== PROCESAMIENTO DE TEXTURAS ====================
    
    /**
     * Reduce la resolución de la textura base y de sus mapas asociados, y la
     * marca como procesada.
     * 
     * @param textureInfo Textura a procesar
     * @throws IOException Si falla el procesamiento
     */
    public void processTexture(TextureInfo textureInfo) throws IOException {
        imageProcessor.processFile(textureInfo.getBaseFile());
        
        for (File merFile : textureInfo.getMerFiles()) {
            imageProcessor.processDataMap(merFile);
        }
        
        for (File normalFile : textureInfo.getNormalFiles()) {
            imageProcessor.processDataMap(normalFile);
        }
        
        textureInfo.setProcessed(true);
    }
    
    /**
     * Procesa una textura en segundo plano. Los callbacks se ejecutan en el
     * hilo de JavaFX.
     * 
     * @param textureInfo Textura a procesar
     * @param onSuccess Callback de éxito (puede ser null)
     * @param onError Callback de error (puede ser null)
     */
    public void processTextureAsync(TextureInfo textureInfo, 
            Runnable onSuccess, Consumer<Exception> onError) {
        
        executorService.submit(() -> {
            try {
                processTexture(textureInfo);
                
                if (onSuccess != null) {
                    Platform.runLater(onSuccess);
                }
            } catch (Exception e) {
                if (onError != null) {
                    Platform.runLater(() -> onError.accept(e));
                }
            }
        });
    }
    
    // ==================== UTILIDADES ====================
    
    /**
     * Envía el mensaje al callback en el hilo de JavaFX.
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
     * @param rootDirectory Directorio raíz a verificar
     * @return true si contiene al menos un subdirectorio de texturas conocido
     */
    public boolean hasValidTextureDirectories(File rootDirectory) {
        File blocksDir = new File(rootDirectory, Constants.BLOCKS_PATH);
        File itemsDir = new File(rootDirectory, Constants.ITEMS_PATH);
        File environmentDir = new File(rootDirectory, Constants.ENVIRONMENT_PATH);
        File entityDir = new File(rootDirectory, Constants.ENTITY_PATH);
        return blocksDir.exists() || itemsDir.exists() || 
               environmentDir.exists() || entityDir.exists();
    }
    
    /** Libera el pool de hilos; llamar al cerrar la aplicación. */
    public void shutdown() {
        executorService.shutdown();
    }
    
    /** @return Procesador de imágenes */
    public ImageProcessor getImageProcessor() {
        return imageProcessor;
    }
}
