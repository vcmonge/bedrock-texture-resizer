package view;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import model.TextureInfo;
import util.Constants;

/**
 * Tarjeta de textura del mosaico, enlazada a su TextureInfo. Muestra:
 * - Miniatura
 * - Resolución con indicadores [M] y [N]
 * - Botón para reducir la textura
 * - CheckBox para selección múltiple
 * 
 * @author vmonge
 */
public class TextureCard extends VBox {
    
    // ==================== COMPONENTES ====================
    
    /** Miniatura. */
    private final ImageView imageView;
    
    /** Resolución e indicadores. */
    private final Label infoLabel;
    
    /** Nombre del archivo. */
    private final Label nameLabel;
    
    /** Botón de procesar. */
    private final Button processButton;
    
    /** CheckBox de selección múltiple. */
    private final CheckBox selectionCheckBox;
    
    /** Miniatura con el checkbox superpuesto. */
    private final StackPane imageContainer;
    
    // ==================== DATOS ====================
    
    /** Textura mostrada. */
    private final TextureInfo textureInfo;
    
    /** Se invoca al pulsar el botón de procesar. */
    private Consumer<TextureInfo> onProcessCallback;
    
    /** Se invoca al cambiar la selección. */
    private Consumer<TextureCard> onSelectionChangedCallback;
    
    /** Modo selección activo. */
    private final BooleanProperty selectionModeActive;
    
    /** Tarjeta seleccionada. */
    private final BooleanProperty selected;
    
    // ==================== ESTILOS ====================
    // Los estilos normal/hover/selected están en fxmlinicio.css (.texture-card,
    // :hover y :selected). Cambiar estilos inline en cada hover causaba
    // tirones con muchas cards.
    
    /** Pseudo-clase CSS :selected. */
    private static final PseudoClass PSEUDO_SELECTED = PseudoClass.getPseudoClass("selected");
    
    // ==================== CONSTRUCTOR ====================
    
    /** @param textureInfo Textura a mostrar (no null) */
    public TextureCard(TextureInfo textureInfo) {
        if (textureInfo == null) {
            throw new IllegalArgumentException("TextureInfo cannot be null");
        }
        
        this.textureInfo = textureInfo;
        this.selectionModeActive = new SimpleBooleanProperty(false);
        this.selected = new SimpleBooleanProperty(false);
        
        this.imageView = createImageView();
        this.selectionCheckBox = createSelectionCheckBox();
        this.imageContainer = createImageContainer();
        this.nameLabel = createNameLabel();
        this.infoLabel = createInfoLabel();
        this.processButton = createProcessButton();
        
        setupLayout();
        
        setupBindings();
        
        applyStyle();
    }
    
    // ==================== CREACIÓN DE COMPONENTES ====================
    
    /** @return ImageView de la miniatura */
    private ImageView createImageView() {
        ImageView iv = new ImageView();
        
        iv.setFitWidth(Constants.THUMBNAIL_SIZE);
        iv.setFitHeight(Constants.THUMBNAIL_SIZE);
        iv.setPreserveRatio(true);
        iv.setSmooth(false); // Nearest neighbor: preview pixelado
        
        if (textureInfo.getThumbnailImage() != null) {
            iv.setImage(textureInfo.getThumbnailImage());
        }
        
        // Cachear el bitmap: el scroll desplaza píxeles ya renderizados
        iv.setCache(true);
        iv.setCacheHint(CacheHint.SPEED);
        
        iv.getStyleClass().add("texture-thumbnail");
        
        return iv;
    }
    
    /** @return CheckBox de selección, enlazado a {@link #selected} */
    private CheckBox createSelectionCheckBox() {
        CheckBox cb = new CheckBox();
        cb.getStyleClass().add("selection-checkbox");
        
        cb.selectedProperty().bindBidirectional(selected);
        
        return cb;
    }
    
    /** @return Miniatura con el checkbox superpuesto arriba a la izquierda */
    private StackPane createImageContainer() {
        StackPane container = new StackPane();
        container.getChildren().addAll(imageView, selectionCheckBox);
        
        StackPane.setAlignment(selectionCheckBox, Pos.TOP_LEFT);
        StackPane.setMargin(selectionCheckBox, new Insets(2));
        
        // Checkbox visible solo en modo selección
        selectionCheckBox.visibleProperty().bind(selectionModeActive);
        selectionCheckBox.managedProperty().bind(selectionModeActive);
        
        return container;
    }
    
    /** @return Etiqueta con el nombre truncado y el completo en el tooltip */
    private Label createNameLabel() {
        Label label = new Label(truncateName(textureInfo.getBaseName(), 18));
        label.getStyleClass().add("texture-card-name");
        label.setMaxWidth(Constants.TEXTURE_CARD_WIDTH - 10);
        
        installLazyTooltip(label, () -> textureInfo.getBaseFile().getName());
        
        return label;
    }
    
    /** @return Etiqueta de resolución */
    private Label createInfoLabel() {
        Label label = new Label(textureInfo.getResolutionText());
        label.getStyleClass().add("texture-card-info");
        
        installLazyTooltip(label, this::buildInfoTooltip);
        
        return label;
    }
    
    /** Texto del tooltip de resolución (se construye en el primer hover). */
    private String buildInfoTooltip() {
        StringBuilder tooltipText = new StringBuilder();
        tooltipText.append("Resolution: ").append(textureInfo.getWidth())
                   .append("x").append(textureInfo.getHeight());
        for (File merFile : textureInfo.getMerFiles()) {
            tooltipText.append("\nMER Texture: ").append(merFile.getName());
        }
        for (File normalFile : textureInfo.getNormalFiles()) {
            tooltipText.append("\nNormal Texture: ").append(normalFile.getName());
        }
        return tooltipText.toString();
    }
    
    /** @return Botón de procesar */
    private Button createProcessButton() {
        Button button = new Button("Resize");
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("texture-card-button");
        
        installLazyTooltip(button, () -> {
            int newWidth = Math.max(1, textureInfo.getWidth() / Constants.DEFAULT_SCALE_FACTOR);
            int newHeight = Math.max(1, textureInfo.getHeight() / Constants.DEFAULT_SCALE_FACTOR);
            return String.format("Resize to %dx%d (factor %d)",
                    newWidth, newHeight, Constants.DEFAULT_SCALE_FACTOR);
        });
        
        button.setOnAction(e -> handleProcessClick());
        
        return button;
    }
    
    /**
     * Crea el Tooltip en el primer hover sobre el nodo, en lugar de cientos al
     * construir el grid (menos nodos y listeners internos de JavaFX).
     * 
     * @param node Nodo al que asociar el tooltip
     * @param textSupplier Proveedor del texto
     */
    private static void installLazyTooltip(javafx.scene.Node node, Supplier<String> textSupplier) {
        node.setOnMouseEntered(new javafx.event.EventHandler<javafx.scene.input.MouseEvent>() {
            @Override
            public void handle(javafx.scene.input.MouseEvent event) {
                Tooltip tooltip = new Tooltip(textSupplier.get());
                Tooltip.install(node, tooltip);
                // Tooltip instalado: el handler ya no hace falta
                node.setOnMouseEntered(null);
            }
        });
    }
    
    // ==================== CONFIGURACIÓN ====================
    
    /** Configura tamaño, espaciado e hijos de la tarjeta. */
    private void setupLayout() {
        setAlignment(Pos.CENTER);
        setSpacing(5);
        setPadding(new Insets(10));
        setPrefWidth(Constants.TEXTURE_CARD_WIDTH);
        setPrefHeight(Constants.TEXTURE_CARD_HEIGHT);
        setMinWidth(Constants.TEXTURE_CARD_WIDTH);
        setMinHeight(Constants.TEXTURE_CARD_HEIGHT);
        
        // Cachear el bitmap de toda la card: el scroll desplaza píxeles ya
        // rasterizados en vez de re-renderizar el VBox y sus hijos cada frame
        setCache(true);
        setCacheHint(CacheHint.SPEED);
        
        getChildren().addAll(imageContainer, nameLabel, infoLabel, processButton);
    }
    
    /** Enlaza la resolución y la visibilidad con TextureInfo. */
    private void setupBindings() {
        infoLabel.textProperty().bind(textureInfo.resolutionTextProperty());
        
        // Ocultar una vez procesada
        this.visibleProperty().bind(textureInfo.processedProperty().not());
        this.managedProperty().bind(textureInfo.processedProperty().not());
    }
    
    /**
     * Aplica la clase CSS y gestiona la selección. Los estilos van por CSS,
     * sin listeners de mouse por card.
     */
    private void applyStyle() {
        getStyleClass().add("texture-card");
        
        // Sincroniza :selected con la propiedad; CSS se encarga del resto
        selected.addListener((obs, oldVal, newVal) -> {
            pseudoClassStateChanged(PSEUDO_SELECTED, newVal);
            if (onSelectionChangedCallback != null) {
                onSelectionChangedCallback.accept(this);
            }
        });
        
        // En modo selección, el clic alterna la selección
        setOnMouseClicked(e -> {
            if (selectionModeActive.get()) {
                selected.set(!selected.get());
            }
        });
    }
    
    // ==================== EVENTOS ====================
    
    /** Deshabilita el botón mientras procesa y avisa al callback. */
    private void handleProcessClick() {
        if (onProcessCallback != null) {
            processButton.setDisable(true);
            processButton.setText("...");
            
            onProcessCallback.accept(textureInfo);
        }
    }
    
    /** @param callback Se invoca con la TextureInfo al pulsar el botón de procesar */
    public void setOnProcessCallback(Consumer<TextureInfo> callback) {
        this.onProcessCallback = callback;
    }
    
    /** @param callback Se invoca al cambiar la selección */
    public void setOnSelectionChangedCallback(Consumer<TextureCard> callback) {
        this.onSelectionChangedCallback = callback;
    }
    
    // ==================== MODO SELECCIÓN ====================
    
    /**
     * Activa o desactiva el modo selección; al desactivarlo se deselecciona.
     * 
     * @param active true para activar
     */
    public void setSelectionModeActive(boolean active) {
        selectionModeActive.set(active);
        
        if (!active) {
            selected.set(false);
        }
    }
    
    /** @return true si el modo de selección está activo */
    public boolean isSelectionModeActive() {
        return selectionModeActive.get();
    }
    
    /** @return Propiedad del modo selección */
    public BooleanProperty selectionModeActiveProperty() {
        return selectionModeActive;
    }
    
    /** @return true si la tarjeta está seleccionada */
    public boolean isSelected() {
        return selected.get();
    }
    
    /** @param selected true para seleccionar, false para deseleccionar */
    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }
    
    /** @return Propiedad de estado seleccionado */
    public BooleanProperty selectedProperty() {
        return selected;
    }
    
    // ==================== UTILIDADES ====================
    
    /** @return El nombre, truncado con "..." si supera maxLength */
    private String truncateName(String name, int maxLength) {
        if (name.length() <= maxLength) {
            return name;
        }
        return name.substring(0, maxLength - 3) + "...";
    }
    
    /** @return Textura mostrada */
    public TextureInfo getTextureInfo() {
        return textureInfo;
    }
    
    /** @return Ancho × alto, para ordenar */
    public int getTotalResolution() {
        return textureInfo.getWidth() * textureInfo.getHeight();
    }
    
    /** Rehabilita el botón de procesar tras un error. */
    public void resetButton() {
        processButton.setDisable(false);
        processButton.setText("Resize");
    }
    
    /** Marca el botón como completado. */
    public void markAsProcessed() {
        processButton.setText("Done");
        if (!processButton.getStyleClass().contains("texture-card-button-done")) {
            processButton.getStyleClass().add("texture-card-button-done");
        }
    }
}
