package util;

/**
 * Algoritmos de redimensionamiento disponibles.
 * 
 * Para añadir uno:
 * 1. Añadir la entrada aquí con su nombre visible
 * 2. Implementarlo en una clase aparte (ver {@link service.AreaResampler})
 * 3. Añadir su case en {@link service.ImageProcessor#resize}
 * 
 * @author vmonge
 */
public enum ResizeAlgorithm {
    
    /**
     * Area Resampling (Pixel Area Relation): cada píxel es un área 1×1 y se
     * usa la intersección exacta, con alfa premultiplicado. Ideal para reducir
     * texturas con bordes semitransparentes.
     */
    AREA("Area Resampling");
    
    // ==================== CAMPOS ====================
    
    /** Nombre mostrado en la UI. */
    private final String displayName;
    
    // ==================== CONSTRUCTOR ====================
    
    /** @param displayName Nombre mostrado en la UI */
    ResizeAlgorithm(String displayName) {
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
