package model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.image.Image;

/**
 * Textura base con sus texturas MER (_mer, _mers) y de normales (_normal, _n)
 * asociadas, si existen. Expone propiedades observables de JavaFX para la UI.
 * 
 * @author vmonge
 */
public class TextureInfo {
    
    // ==================== ARCHIVOS ====================
    
    /** Textura base. */
    private final File baseFile;
    
    /** Texturas MER (metallic/emissive/roughness) asociadas. */
    private final List<File> merFiles;
    
    /** Texturas de normales asociadas. */
    private final List<File> normalFiles;
    
    // ==================== PROPIEDADES DE IMAGEN ====================
    
    /** Ancho en píxeles. */
    private final int width;
    
    /** Alto en píxeles. */
    private final int height;
    
    /** Miniatura de preview. */
    private Image thumbnailImage;
    
    // ==================== PROPIEDADES OBSERVABLES ====================
    
    /** Texto de resolución. */
    private final StringProperty resolutionText;
    
    /** Indica si ya fue procesada. */
    private final BooleanProperty processed;
    
    /**
     * Selección (modo selección múltiple). Vive en el modelo para sobrevivir
     * al repintado del CanvasTextureGrid.
     */
    private final BooleanProperty selected;
    
    // ==================== CONSTRUCTOR ====================
    
    /**
     * @param baseFile Textura base (no null)
     * @param width Ancho en píxeles
     * @param height Alto en píxeles
     */
    public TextureInfo(File baseFile, int width, int height) {
        if (baseFile == null) {
            throw new IllegalArgumentException("Base file cannot be null");
        }
        
        this.baseFile = baseFile;
        this.width = width;
        this.height = height;
        this.merFiles = new ArrayList<>();
        this.normalFiles = new ArrayList<>();
        this.thumbnailImage = null;
        
        this.resolutionText = new SimpleStringProperty(buildResolutionText());
        this.processed = new SimpleBooleanProperty(false);
        this.selected = new SimpleBooleanProperty(false);
    }
    
    // ==================== MÉTODOS DE CONSTRUCCIÓN DE TEXTO ====================
    
    /** @return Resolución con marcadores de texturas asociadas: "WIDTHxHEIGHT [M][N]" */
    private String buildResolutionText() {
        StringBuilder sb = new StringBuilder();
        sb.append(width).append("x").append(height);
        
        if (hasMer()) {
            sb.append(" [M]");
        }
        if (hasNormal()) {
            sb.append(" [N]");
        }
        
        return sb.toString();
    }
    
    /** Recalcula el texto de resolución tras cambiar los archivos asociados. */
    public void updateResolutionText() {
        resolutionText.set(buildResolutionText());
    }
    
    // ==================== GETTERS Y SETTERS ====================
    
    /** @return Textura base */
    public File getBaseFile() {
        return baseFile;
    }
    
    /** @return Nombre del archivo sin extensión */
    public String getBaseName() {
        String name = baseFile.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }
    
    /** @return Extensión en minúsculas con el punto (ej: ".png") */
    public String getExtension() {
        String name = baseFile.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(dotIndex).toLowerCase() : "";
    }
    
    /** @return Copia inmutable de las texturas MER asociadas */
    public List<File> getMerFiles() {
        return Collections.unmodifiableList(new ArrayList<>(merFiles));
    }
    
    /**
     * Asocia una textura MER si no lo estaba ya.
     * 
     * @param merFile Archivo MER; null se ignora
     */
    public void addMerFile(File merFile) {
        if (merFile != null && !merFiles.contains(merFile)) {
            merFiles.add(merFile);
            updateResolutionText();
        }
    }
    
    /** @return true si tiene textura MER */
    public boolean hasMer() {
        return !merFiles.isEmpty();
    }
    
    /** @return Copia inmutable de las texturas de normales asociadas */
    public List<File> getNormalFiles() {
        return Collections.unmodifiableList(new ArrayList<>(normalFiles));
    }
    
    /**
     * Asocia una textura de normales si no lo estaba ya.
     * 
     * @param normalFile Archivo de normales; null se ignora
     */
    public void addNormalFile(File normalFile) {
        if (normalFile != null && !normalFiles.contains(normalFile)) {
            normalFiles.add(normalFile);
            updateResolutionText();
        }
    }
    
    /** @return true si tiene textura de normales */
    public boolean hasNormal() {
        return !normalFiles.isEmpty();
    }
    
    /** @return Ancho en píxeles */
    public int getWidth() {
        return width;
    }
    
    /** @return Alto en píxeles */
    public int getHeight() {
        return height;
    }
    
    /** @return Miniatura de preview */
    public Image getThumbnailImage() {
        return thumbnailImage;
    }
    
    /** @param thumbnailImage Miniatura de preview */
    public void setThumbnailImage(Image thumbnailImage) {
        this.thumbnailImage = thumbnailImage;
    }
    
    // ==================== PROPIEDADES OBSERVABLES ====================
    
    /** @return Propiedad del texto de resolución */
    public StringProperty resolutionTextProperty() {
        return resolutionText;
    }
    
    /** @return Texto de resolución actual */
    public String getResolutionText() {
        return resolutionText.get();
    }
    
    /** @return Propiedad de estado procesado */
    public BooleanProperty processedProperty() {
        return processed;
    }
    
    /** @return true si la textura ya fue procesada */
    public boolean isProcessed() {
        return processed.get();
    }
    
    /** @param processed Estado de procesamiento */
    public void setProcessed(boolean processed) {
        this.processed.set(processed);
    }
    
    /** @return Propiedad de selección */
    public BooleanProperty selectedProperty() {
        return selected;
    }
    
    /** @return true si la textura está seleccionada (modo selección múltiple) */
    public boolean isSelected() {
        return selected.get();
    }
    
    /** @param selected true para seleccionar, false para deseleccionar */
    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }
    
    // ==================== MÉTODOS DE UTILIDAD ====================
    
    /** @return Directorio padre del archivo base */
    public File getParentDirectory() {
        return baseFile.getParentFile();
    }
    
    /** @return Ruta absoluta del archivo base */
    public String getFullPath() {
        return baseFile.getAbsolutePath();
    }
    
    @Override
    public String toString() {
        return String.format("TextureInfo[%s, %dx%d, MER=%d, Normal=%d]",
                baseFile.getName(), width, height, merFiles.size(), normalFiles.size());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TextureInfo that = (TextureInfo) obj;
        return baseFile.equals(that.baseFile);
    }
    
    @Override
    public int hashCode() {
        return baseFile.hashCode();
    }
}
