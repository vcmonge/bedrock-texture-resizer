package util;

/**
 * Configuración centralizada de la aplicación.
 * 
 * @author vmonge
 */
public final class Constants {
    
    // ==================== CONFIGURACIÓN DE ALGORITMO ====================
    
    /**
     * Algoritmo de redimensionamiento por defecto.
     * 
     * @see ResizeAlgorithm
     */
    public static final ResizeAlgorithm DEFAULT_ALGORITHM = ResizeAlgorithm.AREA;
    
    /**
     * Factor de reducción por defecto: la resolución se divide entre él
     * (2 reduce 128x128 a 64x64).
     */
    public static final int DEFAULT_SCALE_FACTOR = 2;
    
    /**
     * Si es true, los {@code .tga} redimensionados con AREA usan
     * {@link service.AreaResampler#resizeIndependentAlpha} (RGB y alfa
     * independientes), lo adecuado para la mayoría de TGA de resource packs.
     * Ya no se expone en la UI.
     */
    public static final boolean TGA_INDEPENDENT_ALPHA_PROCESSING = true;
    
    /**
     * Modo de alfa por defecto para PNG. {@link AlphaMode#ADAPTIVE} aplica el
     * umbral binario en bordes de recorte y conserva la opacidad parcial.
     * 
     * @see AlphaMode
     */
    public static final AlphaMode DEFAULT_ALPHA_MODE = AlphaMode.ADAPTIVE;
    
    // ==================== RUTAS DE TEXTURAS ====================
    
    /** Texturas de bloques. */
    public static final String BLOCKS_PATH = "textures/blocks";
    
    /** Texturas de items. */
    public static final String ITEMS_PATH = "textures/items";
    
    /** Texturas de entorno (cielo, nubes...). */
    public static final String ENVIRONMENT_PATH = "textures/environment";
    
    /** Texturas de entidades (mobs, jugadores...). */
    public static final String ENTITY_PATH = "textures/entity";
    
    // ==================== SUFIJOS DE ARCHIVOS ====================
    
    /**
     * Sufijo canónico de mapas metallic/emissive/roughness (MER).
     *
     * @deprecated Usar {@link #MER_SUFFIXES}, que incluye todas las variantes.
     */
    @Deprecated
    public static final String MER_SUFFIX = "_mer";

    /** Sufijos de mapas MER. */
    public static final String[] MER_SUFFIXES = {MER_SUFFIX, "_mers"};
    
    /**
     * Sufijo canónico de mapas de normales.
     *
     * @deprecated Usar {@link #NORMAL_SUFFIXES}, que incluye todas las variantes.
     */
    @Deprecated
    public static final String NORMAL_SUFFIX = "_normal";

    /** Sufijos de mapas de normales. */
    public static final String[] NORMAL_SUFFIXES = {NORMAL_SUFFIX, "_n"};
    
    // ==================== EXTENSIONES SOPORTADAS ====================
    
    /** Extensiones de textura soportadas. */
    public static final String[] SUPPORTED_EXTENSIONS = {".png", ".tga"};
    
    /** Extensión PNG. */
    public static final String PNG_EXTENSION = ".png";
    
    /** Extensión TGA. */
    public static final String TGA_EXTENSION = ".tga";
    
    // ==================== CONFIGURACIÓN DE UI ====================
    
    /** Ancho de la tarjeta de textura. */
    public static final double TEXTURE_CARD_WIDTH = 150;
    
    /** Alto de la tarjeta de textura. */
    public static final double TEXTURE_CARD_HEIGHT = 180;
    
    /** Tamaño visible de la miniatura (píxeles lógicos). */
    public static final double THUMBNAIL_SIZE = 100;
    
    /**
     * Tamaño al que se decodifica la miniatura en memoria: 2x el visible, para
     * nitidez en HiDPI sin cargar la imagen completa. Reduce mucho el heap con
     * muchas texturas grandes (1024+).
     */
    public static final int THUMBNAIL_RENDER_SIZE = 200;
    
    /** Columnas del mosaico (0 = automático). */
    public static final int MOSAIC_COLUMNS = 0;
    
    /** Separación horizontal entre tarjetas. */
    public static final double CARD_SPACING_H = 10;
    
    /** Separación vertical entre tarjetas. */
    public static final double CARD_SPACING_V = 10;
    
    // ==================== CONFIGURACIÓN DE HILOS ====================
    
    /** Hilos para la carga paralela de imágenes (uno por núcleo). */
    public static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    
    // ==================== CONSTRUCTOR PRIVADO ====================
    
    /** No instanciable. */
    private Constants() {
        throw new UnsupportedOperationException("Constants class - do not instantiate");
    }
}
