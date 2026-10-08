package service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import util.AlphaMode;
import util.Constants;
import util.ResizeAlgorithm;
import util.TGAHandler;

/**
 * Servicio para procesar y redimensionar imágenes.
 * Despacha al algoritmo seleccionado mediante un switch sobre {@link ResizeAlgorithm}.
 * 
 * Maneja imágenes PNG y TGA, preservando el canal alpha en ambos casos.
 * El canal alpha es crítico para:
 * - Transparencia en items
 * - Información adicional en texturas TGA (mapas de altura, máscaras, etc.)
 * 
 * Para añadir un nuevo algoritmo, implementar su lógica en una clase separada
 * y agregar el case correspondiente en {@link #resize(BufferedImage)}.
 * 
 * @author vmonge
 */
public class ImageProcessor {
    
    // ==================== CAMPOS ====================
    
    /** Factor de escala para reducir la resolución */
    private final int scaleFactor;
    
    /** Algoritmo de redimensionamiento a usar */
    private final ResizeAlgorithm algorithm;
    
    /** Si true, los TGA se procesan con RGB y Alpha como canales independientes */
    private final boolean tgaIndependentAlpha;
    
    /** Modo de procesamiento del canal alfa (adaptativo, binario o continuo) */
    private final AlphaMode alphaMode;
    
    // ==================== CONSTRUCTORES ====================
    
    /**
     * Crea un procesador con la configuración por defecto.
     */
    public ImageProcessor() {
        this(Constants.DEFAULT_SCALE_FACTOR, Constants.DEFAULT_ALGORITHM);
    }
    
    /**
     * Crea un procesador con factor de escala personalizado y algoritmo por defecto.
     * 
     * @param scaleFactor Factor de división de resolución (2 = mitad, 4 = cuarto, etc.)
     */
    public ImageProcessor(int scaleFactor) {
        this(scaleFactor, Constants.DEFAULT_ALGORITHM);
    }
    
    /**
     * Crea un procesador con configuración completa personalizada.
     * 
     * @param scaleFactor Factor de división de resolución
     * @param algorithm Algoritmo de redimensionamiento a usar
     */
    public ImageProcessor(int scaleFactor, ResizeAlgorithm algorithm) {
        this(scaleFactor, algorithm, false);
    }
    
    /**
     * Crea un procesador con configuración completa y control de TGA.
     * Usa el modo de alfa por defecto ({@link Constants#DEFAULT_ALPHA_MODE}).
     * 
     * @param scaleFactor Factor de división de resolución
     * @param algorithm Algoritmo de redimensionamiento a usar
     * @param tgaIndependentAlpha Si true, los TGA se procesan con canales RGB y Alpha independientes
     */
    public ImageProcessor(int scaleFactor, ResizeAlgorithm algorithm, boolean tgaIndependentAlpha) {
        this(scaleFactor, algorithm, tgaIndependentAlpha, Constants.DEFAULT_ALPHA_MODE);
    }
    
    /**
     * Crea un procesador con configuración completa, control de TGA y modo de alfa.
     * 
     * @param scaleFactor Factor de división de resolución
     * @param algorithm Algoritmo de redimensionamiento a usar
     * @param tgaIndependentAlpha Si true, los TGA se procesan con canales RGB y Alpha independientes
     * @param alphaMode Modo de procesamiento del canal alfa (adaptativo, binario o continuo)
     */
    public ImageProcessor(int scaleFactor, ResizeAlgorithm algorithm, 
            boolean tgaIndependentAlpha, AlphaMode alphaMode) {
        if (scaleFactor < 1) {
            throw new IllegalArgumentException("El factor de escala debe ser >= 1");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("El algoritmo no puede ser null");
        }
        if (alphaMode == null) {
            throw new IllegalArgumentException("El modo de alfa no puede ser null");
        }
        this.scaleFactor = scaleFactor;
        this.algorithm = algorithm;
        this.tgaIndependentAlpha = tgaIndependentAlpha;
        this.alphaMode = alphaMode;
    }
    
    // ==================== MÉTODOS DE PROCESAMIENTO ====================
    
    /**
     * Redimensiona una imagen usando el algoritmo configurado.
     * Preserva el canal alpha si la imagen lo tiene.
     * 
     * @param original Imagen original a redimensionar
     * @return Nueva imagen redimensionada
     */
    public BufferedImage resize(BufferedImage original) {
        if (original == null) {
            throw new IllegalArgumentException("La imagen no puede ser null");
        }
        
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();
        
        int newWidth = Math.max(1, originalWidth / scaleFactor);
        int newHeight = Math.max(1, originalHeight / scaleFactor);
        
        if (newWidth == originalWidth && newHeight == originalHeight) {
            return copyImage(original);
        }
        
        switch (algorithm) {
            case AREA:
                return AreaResampler.resize(original, newWidth, newHeight, alphaMode);
            default:
                throw new UnsupportedOperationException(
                        "Algoritmo no implementado: " + algorithm);
        }
    }
    
    // ==================== UTILIDADES INTERNAS ====================
    
    /**
     * Crea una copia de la imagen.
     */
    private BufferedImage copyImage(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = original.getRGB(0, 0, w, h, null, 0, w);
        copy.setRGB(0, 0, w, h, pixels, 0, w);
        return copy;
    }
    
    // ==================== MÉTODOS DE ARCHIVO ====================
    
    /**
     * Procesa un archivo de imagen: lee, redimensiona y guarda.
     * Detecta automáticamente el formato (PNG o TGA).
     * 
     * @param inputFile Archivo de entrada
     * @param outputFile Archivo de salida (puede ser el mismo para sobrescribir)
     * @throws IOException Si hay error de lectura/escritura
     */
    public void processFile(File inputFile, File outputFile) throws IOException {
        processFile(inputFile, outputFile, false);
    }
    
    /**
     * Procesa un mapa de datos asociado (MER/MERS o normal) sobrescribiendo el original.
     * 
     * Sus canales son datos, no color + transparencia (en un {@code _mers} el alfa
     * es subsurface), así que los cuatro canales se promedian de forma independiente
     * y con alfa continuo, sin importar el modo de alfa elegido para las texturas.
     * 
     * @param file Mapa a procesar
     * @throws IOException Si hay error de lectura/escritura
     */
    public void processDataMap(File file) throws IOException {
        processFile(file, file, true);
    }
    
    private void processFile(File inputFile, File outputFile, boolean dataMap) throws IOException {
        if (!inputFile.exists()) {
            throw new IOException("El archivo no existe: " + inputFile.getAbsolutePath());
        }
        
        String extension = getExtension(inputFile).toLowerCase();
        
        BufferedImage original;
        if (extension.equals(Constants.TGA_EXTENSION)) {
            original = TGAHandler.read(inputFile);
        } else {
            original = ImageIO.read(inputFile);
        }
        
        if (original == null) {
            throw new IOException("No se pudo leer la imagen: " + inputFile.getAbsolutePath());
        }
        
        BufferedImage resized;
        if (dataMap && algorithm == ResizeAlgorithm.AREA) {
            int[] dims = calculateNewDimensions(original.getWidth(), original.getHeight());
            resized = AreaResampler.resizeIndependentAlpha(original, dims[0], dims[1],
                    AlphaMode.CONTINUOUS);
        } else if (extension.equals(Constants.TGA_EXTENSION) && algorithm == ResizeAlgorithm.AREA && tgaIndependentAlpha) {
            int[] dims = calculateNewDimensions(original.getWidth(), original.getHeight());
            resized = AreaResampler.resizeIndependentAlpha(original, dims[0], dims[1]);
        } else {
            resized = resize(original);
        }
        
        if (extension.equals(Constants.TGA_EXTENSION)) {
            TGAHandler.write(resized, outputFile);
        } else {
            ImageIO.write(resized, "PNG", outputFile);
        }
    }
    
    /**
     * Procesa un archivo sobrescribiendo el original.
     * 
     * @param file Archivo a procesar
     * @throws IOException Si hay error de lectura/escritura
     */
    public void processFile(File file) throws IOException {
        processFile(file, file);
    }
    
    /**
     * Lee una imagen de archivo.
     * 
     * @param file Archivo a leer
     * @return BufferedImage cargada
     * @throws IOException Si hay error de lectura
     */
    public BufferedImage readImage(File file) throws IOException {
        String extension = getExtension(file).toLowerCase();
        
        if (extension.equals(Constants.TGA_EXTENSION)) {
            return TGAHandler.read(file);
        } else {
            return ImageIO.read(file);
        }
    }
    
    /**
     * Guarda una imagen en archivo.
     * 
     * @param image Imagen a guardar
     * @param file Archivo de destino
     * @throws IOException Si hay error de escritura
     */
    public void writeImage(BufferedImage image, File file) throws IOException {
        String extension = getExtension(file).toLowerCase();
        
        if (extension.equals(Constants.TGA_EXTENSION)) {
            TGAHandler.write(image, file);
        } else {
            ImageIO.write(image, "PNG", file);
        }
    }
    
    // ==================== MÉTODOS DE UTILIDAD ====================
    
    /**
     * Obtiene la extensión de un archivo.
     */
    private String getExtension(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(dotIndex) : "";
    }
    
    /**
     * @return Factor de escala actual
     */
    public int getScaleFactor() {
        return scaleFactor;
    }
    
    /**
     * @return Algoritmo de redimensionamiento actual
     */
    public ResizeAlgorithm getAlgorithm() {
        return algorithm;
    }
    
    /**
     * @return Modo de procesamiento del canal alfa
     */
    public AlphaMode getAlphaMode() {
        return alphaMode;
    }
    
    /**
     * Calcula las dimensiones resultantes de redimensionar una imagen.
     * 
     * @param originalWidth Ancho original
     * @param originalHeight Alto original
     * @return Array de 2 elementos: [nuevoAncho, nuevoAlto]
     */
    public int[] calculateNewDimensions(int originalWidth, int originalHeight) {
        return new int[] {
            Math.max(1, originalWidth / scaleFactor),
            Math.max(1, originalHeight / scaleFactor)
        };
    }
}
