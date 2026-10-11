package util;

/**
 * Modos de tratamiento del canal alfa al redimensionar.
 * 
 * <ul>
 *   <li>{@link #ADAPTIVE} — Por píxel: umbral binario donde la fuente es un
 *       recorte duro (alfa 0 o 255) y alfa continuo donde hay opacidad
 *       parcial. Válido para cualquier textura.</li>
 *   <li>{@link #BINARY} — Alfa ≥ 0.5 → 255, alfa &lt; 0.5 → 0. Para
 *       texturas de recorte (hojas, flores).</li>
 *   <li>{@link #CONTINUOUS} — Conserva el alfa real (0–255). Para texturas
 *       con opacidad parcial (hielo, agua, vidrio).</li>
 * </ul>
 * 
 * @author vmonge
 */
public enum AlphaMode {
    
    /**
     * Si todos los píxeles fuente de un píxel destino tienen alfa 0 o 255, se
     * aplica {@link #BINARY}; si no, {@link #CONTINUOUS}. Bordes de recorte
     * nítidos sin perder la translucidez de hielo, agua o vidrio tintado.
     */
    ADAPTIVE("Adaptive (Default Mode)"),
    
    /** Cada píxel resultante es totalmente opaco o transparente (comportamiento original). */
    BINARY("Binary (hard cut-out)"),
    
    /** Conserva la transparencia intermedia; equivale a Photoshop en texturas semitransparentes. */
    CONTINUOUS("Continuous (preserve opacity)");
    
    // ==================== CAMPOS ====================
    
    /** Nombre mostrado en la UI. */
    private final String displayName;
    
    // ==================== CONSTRUCTOR ====================
    
    /** @param displayName Nombre mostrado en la UI */
    AlphaMode(String displayName) {
        this.displayName = displayName;
    }
    
    // ==================== MÉTODOS ====================
    
    /** @return Nombre mostrado en la UI */
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
