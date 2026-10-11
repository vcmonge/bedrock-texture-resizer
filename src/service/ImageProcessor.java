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
 * Lee, redimensiona y guarda imágenes PNG y TGA preservando el canal alfa
 * (transparencia de items, datos extra en TGA como mapas de altura o máscaras).
 * 
 * Para añadir un algoritmo, implementarlo en una clase aparte y añadir su case
 * en {@link #resize(BufferedImage)}.
 * 
 * @author vmonge
 */
public class ImageProcessor {
    
    // ==================== CAMPOS ====================
    
    /** Divisor de la resolución. */
    private final int scaleFactor;
    
    /** Algoritmo de redimensionamiento. */
    private final ResizeAlgorithm algorithm;
    
    /** Si true, los TGA se procesan con RGB y alfa independientes. */
    private final boolean tgaIndependentAlpha;
    
    /** Modo de alfa (adaptativo, binario o continuo). */
    private final AlphaMode alphaMode;
    
    // ==================== CONSTRUCTORES ====================
    
    /** Configuración por defecto. */
    public ImageProcessor() {
        this(Constants.DEFAULT_SCALE_FACTOR, Constants.DEFAULT_ALGORITHM);
    }
    
    /** @param scaleFactor Divisor de la resolución (2 = mitad, 4 = cuarto...) */
    public ImageProcessor(int scaleFactor) {
        this(scaleFactor, Constants.DEFAULT_ALGORITHM);
    }
    
    /**
     * @param scaleFactor Divisor de la resolución
     * @param algorithm Algoritmo de redimensionamiento
     */
    public ImageProcessor(int scaleFactor, ResizeAlgorithm algorithm) {
        this(scaleFactor, algorithm, false);
    }
    
    /**
     * Usa el modo de alfa por defecto ({@link Constants#DEFAULT_ALPHA_MODE}).
     * 
     * @param scaleFactor Divisor de la resolución
     * @param algorithm Algoritmo de redimensionamiento
     * @param tgaIndependentAlpha Si true, los TGA se procesan con RGB y alfa independientes
     */
    public ImageProcessor(int scaleFactor, ResizeAlgorithm algorithm, boolean tgaIndependentAlpha) {
        this(scaleFactor, algorithm, tgaIndependentAlpha, Constants.DEFAULT_ALPHA_MODE);
    }
    
    /**
     * @param scaleFactor Divisor de la resolución
     * @param algorithm Algoritmo de redimensionamiento
     * @param tgaIndependentAlpha Si true, los TGA se procesan con RGB y alfa independientes
     * @param alphaMode Modo de alfa (adaptativo, binario o continuo)
     */
    public ImageProcessor(int scaleFactor, ResizeAlgorithm algorithm, 
            boolean tgaIndependentAlpha, AlphaMode alphaMode) {
        if (scaleFactor < 1) {
            throw new IllegalArgumentException("Scale factor must be >= 1");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("Algorithm cannot be null");
        }
        if (alphaMode == null) {
            throw new IllegalArgumentException("Alpha mode cannot be null");
        }
        this.scaleFactor = scaleFactor;
        this.algorithm = algorithm;
        this.tgaIndependentAlpha = tgaIndependentAlpha;
        this.alphaMode = alphaMode;
    }
    
    // ==================== MÉTODOS DE PROCESAMIENTO ====================
    
    /**
     * Redimensiona con el algoritmo configurado, preservando el alfa.
     * 
     * @param original Imagen original
     * @return Nueva imagen (copia si las dimensiones no cambian)
     */
    public BufferedImage resize(BufferedImage original) {
        if (original == null) {
            throw new IllegalArgumentException("Image cannot be null");
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
                        "Algorithm not implemented: " + algorithm);
        }
    }
    
    // ==================== UTILIDADES INTERNAS ====================
    
    /** Copia la imagen en TYPE_INT_ARGB. */
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
     * Lee, redimensiona y guarda una imagen PNG o TGA (según la extensión).
     * 
     * @param inputFile Archivo de entrada
     * @param outputFile Archivo de salida (puede ser el mismo para sobrescribir)
     * @throws IOException Si falla la lectura o escritura
     */
    public void processFile(File inputFile, File outputFile) throws IOException {
        processFile(inputFile, outputFile, false);
    }
    
    /**
     * Procesa in situ un mapa de datos asociado (MER/MERS o normal).
     * 
     * Sus canales son datos, no color + transparencia (en un {@code _mers} el
     * alfa es subsurface), así que los cuatro se promedian por separado con alfa
     * continuo, sea cual sea el modo de alfa elegido.
     * 
     * @param file Mapa a procesar
     * @throws IOException Si falla la lectura o escritura
     */
    public void processDataMap(File file) throws IOException {
        processFile(file, file, true);
    }
    
    private void processFile(File inputFile, File outputFile, boolean dataMap) throws IOException {
        if (!inputFile.exists()) {
            throw new IOException("File does not exist: " + inputFile.getAbsolutePath());
        }
        
        String extension = getExtension(inputFile).toLowerCase();
        
        BufferedImage original;
        if (extension.equals(Constants.TGA_EXTENSION)) {
            original = TGAHandler.read(inputFile);
        } else {
            original = ImageIO.read(inputFile);
        }
        
        if (original == null) {
            throw new IOException("Could not read image: " + inputFile.getAbsolutePath());
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
     * Procesa un archivo sobrescribiéndolo.
     * 
     * @param file Archivo a procesar
     * @throws IOException Si falla la lectura o escritura
     */
    public void processFile(File file) throws IOException {
        processFile(file, file);
    }
    
    /**
     * @param file Imagen PNG o TGA
     * @return Imagen cargada
     * @throws IOException Si falla la lectura
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
     * Guarda como TGA o PNG según la extensión del destino.
     * 
     * @param image Imagen a guardar
     * @param file Archivo de destino
     * @throws IOException Si falla la escritura
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
    
    /** @return Extensión con el punto, o "" si no tiene */
    private String getExtension(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(dotIndex) : "";
    }
    
    /** @return Factor de escala actual */
    public int getScaleFactor() {
        return scaleFactor;
    }
    
    /** @return Algoritmo actual */
    public ResizeAlgorithm getAlgorithm() {
        return algorithm;
    }
    
    /** @return Modo de alfa */
    public AlphaMode getAlphaMode() {
        return alphaMode;
    }
    
    /**
     * @param originalWidth Ancho original
     * @param originalHeight Alto original
     * @return [nuevoAncho, nuevoAlto], cada uno ≥ 1
     */
    public int[] calculateNewDimensions(int originalWidth, int originalHeight) {
        return new int[] {
            Math.max(1, originalWidth / scaleFactor),
            Math.max(1, originalHeight / scaleFactor)
        };
    }
}
