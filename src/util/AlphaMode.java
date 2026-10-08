package util;

/**
 * Modos de procesamiento del canal alfa durante el redimensionamiento.
 * 
 * <ul>
 *   <li>{@link #ADAPTIVE} — Decide por píxel: umbral binario donde la zona
 *       fuente es un recorte duro (solo alfa 0 o 255) y alfa continuo donde
 *       hay opacidad parcial real. Sirve para cualquier textura.</li>
 *   <li>{@link #BINARY} — Umbral binario: alfa ≥ 0.5 → 255 (opaco),
 *       alfa &lt; 0.5 → 0 (transparente). Ideal para texturas con
 *       transparencia de recorte (hojas, flores, etc.).</li>
 *   <li>{@link #CONTINUOUS} — Preserva el valor real del alfa (0–255).
 *       Ideal para texturas con opacidad parcial (hielo, agua,
 *       cristal, vidrio, etc.).</li>
 * </ul>
 * 
 * @author vmonge
 */
public enum AlphaMode {
    
    /**
     * Alfa adaptativo: cada píxel destino cuyos píxeles fuente tienen todos
     * alfa 0 o 255 se resuelve con el umbral de {@link #BINARY}, y el resto con
     * {@link #CONTINUOUS}. Mantiene nítidos los bordes de recorte sin perder la
     * translucidez de hielo, agua o vidrio tintado.
     */
    ADAPTIVE("Adaptive (Default Mode)"),
    
    /**
     * Alfa binario: cada píxel resultante es 100 % opaco o 100 % transparente.
     * Comportamiento original del algoritmo.
     */
    BINARY("Binary (hard cut-out)"),
    
    /**
     * Alfa continuo: preserva los valores intermedios de transparencia.
     * Produce resultados equivalentes a Photoshop en texturas semitransparentes.
     */
    CONTINUOUS("Continuous (preserve opacity)");
    
    // ==================== CAMPOS ====================
    
    /** Nombre legible para mostrar en la UI */
    private final String displayName;
    
    // ==================== CONSTRUCTOR ====================
    
    /**
     * @param displayName Nombre para mostrar en la interfaz
     */
    AlphaMode(String displayName) {
        this.displayName = displayName;
    }
    
    // ==================== MÉTODOS ====================
    
    /**
     * @return Nombre legible para la interfaz
     */
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
