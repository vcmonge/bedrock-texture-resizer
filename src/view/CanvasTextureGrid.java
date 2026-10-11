package view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Bounds;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import model.TextureInfo;

/**
 * Grid virtualizado que dibuja todas las texturas en un único Canvas en vez
 * de crear nodos por textura.
 *
 * Con miles de texturas, ~7 nodos por TextureCard suman decenas de miles de
 * nodos y ralentizan toda la app (CSS, layout, picking, cache de Prism). Con
 * un solo nodo dibujado vía GraphicsContext, el resto de controles responde
 * al instante.
 *
 * El Canvas mide lo que el viewport del ScrollPane, no el contenido total,
 * para no superar el tamaño máximo de Canvas en GPU. Al hacer scroll se
 * reposiciona sobre el área visible y redibuja la franja de cards que toca.
 *
 * @author vmonge
 */
public class CanvasTextureGrid extends Pane {

    // ==================== CONSTANTES DE LAYOUT ====================
    
    /** Ancho de cada card. */
    private static final double CARD_W = 156;
    
    /** Alto de cada card. */
    private static final double CARD_H = 196;
    
    /** Espacio horizontal entre cards. */
    private static final double H_GAP = 12;
    
    /** Espacio vertical entre cards. */
    private static final double V_GAP = 12;
    
    /** Padding alrededor del grid. */
    private static final double PADDING = 16;
    
    /** Tamaño visible de la miniatura dentro de la card. */
    private static final double THUMB_SIZE = 104;
    
    /** Alto del botón Resize. */
    private static final double BUTTON_H = 32;
    
    /** Margen interno de la card. */
    private static final double CARD_PAD = 10;

    /** Editorial card radius. */
    private static final double CARD_RADIUS = 16;

    /** Circular selection indicator size. */
    private static final double SELECTION_INDICATOR_SIZE = 20;
    
    // ==================== COLORES ====================
    
    private static final Color BG_NORMAL = DesignTokens.SURFACE_CARD;
    private static final Color BG_HOVER = DesignTokens.SURFACE_CARD;
    private static final Color BG_SELECTED = DesignTokens.SURFACE_STRONG;
    private static final Color BORDER_NORMAL = DesignTokens.HAIRLINE;
    private static final Color BORDER_HOVER = DesignTokens.HAIRLINE_STRONG;
    private static final Color BORDER_SELECTED = DesignTokens.INK;
    private static final Color TEXT_NAME = DesignTokens.INK;
    private static final Color TEXT_INFO = DesignTokens.MUTED;
    private static final Color BUTTON_BG = DesignTokens.SURFACE_CARD;
    private static final Color BUTTON_BG_HOVER = DesignTokens.PRIMARY;
    private static final Color BUTTON_BORDER = DesignTokens.HAIRLINE_STRONG;
    private static final Color BUTTON_TEXT = DesignTokens.INK;
    private static final Color BUTTON_TEXT_HOVER = DesignTokens.ON_PRIMARY;
    private static final Color PLACEHOLDER_BG = DesignTokens.SURFACE_STRONG;
    private static final Color PLACEHOLDER_BORDER = DesignTokens.HAIRLINE;
    private static final Color PLACEHOLDER_TEXT = DesignTokens.MUTED_SOFT;

    private static final DropShadow CARD_HOVER_SHADOW = new DropShadow(
            BlurType.GAUSSIAN, DesignTokens.SHADOW_SOFT, 16, 0, 0, 4);
    
    // ==================== FUENTES ====================
    
    private static final Font FONT_NAME = Font.font(
            DesignTokens.BODY_FONT, FontWeight.MEDIUM, 12);
    private static final Font FONT_INFO = Font.font(
            DesignTokens.BODY_FONT, FontWeight.NORMAL, 11);
    private static final Font FONT_BUTTON = Font.font(
            DesignTokens.BODY_FONT, FontWeight.MEDIUM, 12);
    private static final Font FONT_SELECTION = Font.font(
            DesignTokens.BODY_FONT, FontWeight.BOLD, 12);
    private static final Font FONT_PLACEHOLDER = Font.font(
            DesignTokens.DISPLAY_FONT, FontWeight.NORMAL, 24);
    
    // ==================== ESTADO ====================
    
    /** Canvas donde se dibuja todo el grid. */
    private final Canvas canvas = new Canvas();
    
    /** Todos los items, incluidos los procesados. */
    private List<TextureInfo> items = Collections.emptyList();
    
    /** Items sin procesar: los que se colocan y pintan. */
    private List<TextureInfo> displayList = Collections.emptyList();
    
    /** Texturas seleccionadas (refleja TextureInfo.selected). */
    private final Set<TextureInfo> selectedSet = new LinkedHashSet<>();
    
    /** Item bajo el cursor. */
    private TextureInfo hoveredItem = null;
    
    /** Índice del item con el cursor sobre su botón, o -1. */
    private int hoveredButtonIndex = -1;
    
    /** Columnas según el ancho del viewport. */
    private int cols = 1;
    
    /** Alto de fila (CARD_H + V_GAP). */
    private static final double ROW_H = CARD_H + V_GAP;
    
    /** ScrollPane padre (ver attachToScrollPane). */
    private ScrollPane scrollPane;
    
    /** Modo selección activo. */
    private BooleanProperty selectionModeActive = new SimpleBooleanProperty(false);
    
    /** Se invoca al pulsar el botón Resize de una card. */
    private Consumer<TextureInfo> onProcessCallback;
    
    /** Se invoca al cambiar la selección. */
    private Runnable onSelectionChangedCallback;
    
    // ==================== CONSTRUCTOR ====================
    
    public CanvasTextureGrid() {
        getStyleClass().add("texture-flow");
        getChildren().add(canvas);
        setMinHeight(0);
        
        // El ancho determina columnas, altura total y disposición
        widthProperty().addListener((obs, o, n) -> {
            recalcLayout();
            requestRepaint();
        });
        
        canvas.setOnMouseMoved(e -> handleMouseMoved(e.getX(), e.getY()));
        canvas.setOnMouseExited(e -> {
            if (hoveredItem != null || hoveredButtonIndex != -1) {
                hoveredItem = null;
                hoveredButtonIndex = -1;
                requestRepaint();
            }
        });
        canvas.setOnMouseClicked(e -> handleMouseClicked(e.getX(), e.getY()));
    }
    
    // ==================== API PÚBLICA ====================
    
    /**
     * Asocia el ScrollPane padre, necesario para dibujar solo la franja
     * visible (según viewport y vvalue).
     * 
     * @param sp ScrollPane que contiene este grid como content
     */
    public void attachToScrollPane(ScrollPane sp) {
        this.scrollPane = sp;
        
        sp.viewportBoundsProperty().addListener((obs, o, n) -> {
            recalcLayout();
            requestRepaint();
        });
        sp.vvalueProperty().addListener((obs, o, n) -> requestRepaint());
    }
    
    /**
     * Enlaza el modo selección: si está activo, los clics alternan la
     * selección; al desactivarlo se vacía.
     * 
     * @param prop Propiedad del controller
     */
    public void setSelectionModeActiveProperty(BooleanProperty prop) {
        this.selectionModeActive = prop;
        prop.addListener((obs, o, n) -> {
            if (!n) {
                clearSelection();
            } else {
                requestRepaint();
            }
        });
    }
    
    /** @param cb Se invoca al pulsar el botón Resize de una card */
    public void setOnProcessCallback(Consumer<TextureInfo> cb) {
        this.onProcessCallback = cb;
    }
    
    /** @param cb Se invoca al cambiar la selección */
    public void setOnSelectionChangedCallback(Runnable cb) {
        this.onSelectionChangedCallback = cb;
    }
    
    /**
     * Asigna las texturas a mostrar, recalcula el layout y repinta.
     * 
     * @param list Texturas (puede ser null)
     */
    public void setItems(List<TextureInfo> list) {
        this.items = list != null ? list : Collections.emptyList();
        rebuildDisplayList();
        recalcLayout();
        requestRepaint();
    }
    
    /** @return Copia de las texturas seleccionadas */
    public Set<TextureInfo> getSelectedItems() {
        return new LinkedHashSet<>(selectedSet);
    }
    
    /** @return Número de items seleccionados */
    public int getSelectedCount() {
        return selectedSet.size();
    }
    
    /**
     * Selecciona o deselecciona todos los items visibles (no procesados).
     * 
     * @param selected true para seleccionar, false para deseleccionar
     */
    public void selectAllVisible(boolean selected) {
        boolean changed = false;
        if (selected) {
            for (TextureInfo t : displayList) {
                if (!selectedSet.contains(t)) {
                    selectedSet.add(t);
                    t.setSelected(true);
                    changed = true;
                }
            }
        } else {
            if (!selectedSet.isEmpty()) {
                for (TextureInfo t : selectedSet) {
                    t.setSelected(false);
                }
                selectedSet.clear();
                changed = true;
            }
        }
        if (changed) {
            requestRepaint();
            fireSelectionChanged();
        }
    }
    
    /**
     * Marca la textura como procesada y la oculta.
     * 
     * @param t Textura procesada
     */
    public void markProcessed(TextureInfo t) {
        t.setProcessed(true);
        if (selectedSet.remove(t)) {
            t.setSelected(false);
        }
        rebuildDisplayList();
        recalcLayout();
        requestRepaint();
    }
    
    /** Solicita un repintado (p. ej. al cambiar contadores externos). */
    public void refresh() {
        requestRepaint();
    }
    
    // ==================== LAYOUT ====================
    
    /**
     * Recalcula columnas, alto del Pane y tamaño del Canvas según el ancho
     * disponible y los items visibles.
     */
    private void recalcLayout() {
        double width = getWidth();
        if (width <= 0 && scrollPane != null) {
            Bounds vb = scrollPane.getViewportBounds();
            if (vb != null) width = vb.getWidth();
        }
        if (width <= 0) {
            cols = 1;
            setPrefHeight(0);
            return;
        }
        
        double inner = Math.max(0, width - 2 * PADDING);
        // Cards que caben contando el gap
        cols = Math.max(1, (int) Math.floor((inner + H_GAP) / (CARD_W + H_GAP)));
        
        int n = displayList.size();
        int rows = (n + cols - 1) / cols;
        double totalH = (rows == 0) ? 0 : (PADDING * 2 + rows * CARD_H + (rows - 1) * V_GAP);
        setPrefHeight(totalH);
        setMinHeight(totalH);
        
        // Canvas del tamaño del viewport, no del total: se mueve con el scroll
        double viewportW = width;
        double viewportH = (scrollPane != null && scrollPane.getViewportBounds() != null)
                ? scrollPane.getViewportBounds().getHeight()
                : getHeight();
        if (viewportH <= 0) viewportH = totalH;
        
        canvas.setWidth(viewportW);
        canvas.setHeight(Math.max(1, viewportH));
    }
    
    @Override
    protected void layoutChildren() {
        // Sin super: el Canvas se posiciona a mano
        positionCanvas();
    }
    
    /**
     * Hace coincidir el Canvas con la zona visible del ScrollPane: este
     * desplaza el contenido -vvalue*(contentH-viewportH), así que poner
     * layoutY = vvalue*(contentH-viewportH) mantiene el Canvas en el viewport.
     */
    private void positionCanvas() {
        if (scrollPane == null) {
            canvas.setLayoutX(0);
            canvas.setLayoutY(0);
            return;
        }
        Bounds vb = scrollPane.getViewportBounds();
        if (vb == null) return;
        
        double contentH = getPrefHeight();
        double viewportH = vb.getHeight();
        double range = Math.max(0, contentH - viewportH);
        double offsetY = scrollPane.getVvalue() * range;
        
        canvas.setLayoutX(0);
        canvas.setLayoutY(offsetY);
    }
    
    /** Excluye los items procesados, conservando el orden. */
    private void rebuildDisplayList() {
        List<TextureInfo> filtered = new ArrayList<>(items.size());
        for (TextureInfo t : items) {
            if (!t.isProcessed()) filtered.add(t);
        }
        this.displayList = filtered;
        // Quitar de la selección los que ya no se ven
        selectedSet.retainAll(filtered);
    }
    
    // ==================== REPAINT ====================
    
    /** Hay un repaint pendiente (agrupa varios cambios en un frame). */
    private boolean repaintRequested = false;
    
    /**
     * Programa un repaint en el siguiente pulso; las llamadas del mismo frame
     * se agrupan en una.
     */
    private void requestRepaint() {
        if (repaintRequested) return;
        repaintRequested = true;
        javafx.application.Platform.runLater(() -> {
            repaintRequested = false;
            paint();
        });
    }
    
    /** Pinta la franja visible del grid. */
    private void paint() {
        positionCanvas();
        
        double viewportW = canvas.getWidth();
        double viewportH = canvas.getHeight();
        if (viewportW <= 0 || viewportH <= 0) return;
        
        GraphicsContext gc = canvas.getGraphicsContext2D();
        // setImageSmoothing(false) solo existe desde JavaFX 12; las miniaturas
        // ya se decodifican con smooth=false en TextureService.generateThumbnail(),
        // así que drawImage conserva el aspecto pixel art.
        gc.clearRect(0, 0, viewportW, viewportH);
        
        if (displayList.isEmpty() || cols <= 0) return;
        
        double offsetY = canvas.getLayoutY();
        
        // Filas que intersectan el viewport
        int firstRow = Math.max(0, (int) Math.floor((offsetY - PADDING) / ROW_H));
        int lastRow = (int) Math.ceil((offsetY + viewportH - PADDING) / ROW_H);
        
        int n = displayList.size();
        int totalRows = (n + cols - 1) / cols;
        lastRow = Math.min(lastRow, totalRows - 1);
        
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (idx >= n) return;
                
                double absX = PADDING + col * (CARD_W + H_GAP);
                double absY = PADDING + row * (CARD_H + V_GAP);
                
                double localX = absX;
                double localY = absY - offsetY;
                
                drawCard(gc, displayList.get(idx), idx, localX, localY);
            }
        }
    }
    
    /** Dibuja una card. */
    private void drawCard(GraphicsContext gc, TextureInfo t, int idx, double x, double y) {
        boolean isHover = (t == hoveredItem);
        boolean isSelected = selectedSet.contains(t);
        boolean isButtonHover = (idx == hoveredButtonIndex);
        
        // Fondo
        Color bg;
        if (isSelected) bg = BG_SELECTED;
        else if (isHover) bg = BG_HOVER;
        else bg = BG_NORMAL;
        gc.save();
        if (isHover) {
            gc.setEffect(CARD_HOVER_SHADOW);
        }
        gc.setFill(bg);
        gc.fillRoundRect(x, y, CARD_W, CARD_H, CARD_RADIUS, CARD_RADIUS);
        gc.restore();
        
        // Borde
        Color border;
        double borderW;
        if (isSelected) {
            border = BORDER_SELECTED;
            borderW = 2;
        } else if (isHover) {
            border = BORDER_HOVER;
            borderW = 1;
        } else {
            border = BORDER_NORMAL;
            borderW = 1;
        }
        gc.setStroke(border);
        gc.setLineWidth(borderW);
        gc.strokeRoundRect(x + 0.5, y + 0.5, CARD_W - 1, CARD_H - 1,
                CARD_RADIUS, CARD_RADIUS);
        
        // Miniatura centrada
        double thumbX = x + (CARD_W - THUMB_SIZE) / 2.0;
        double thumbY = y + CARD_PAD;
        Image thumb = t.getThumbnailImage();
        if (thumb != null && !thumb.isError()) {
            // Ajustar al cuadrado THUMB_SIZE conservando el aspecto
            double iw = thumb.getWidth();
            double ih = thumb.getHeight();
            double scale = (iw <= 0 || ih <= 0) ? 1.0 : Math.min(THUMB_SIZE / iw, THUMB_SIZE / ih);
            double dw = iw * scale;
            double dh = ih * scale;
            double dx = thumbX + (THUMB_SIZE - dw) / 2.0;
            double dy = thumbY + (THUMB_SIZE - dh) / 2.0;
            gc.drawImage(thumb, dx, dy, dw, dh);
        } else {
            // Placeholder
            gc.setFill(PLACEHOLDER_BG);
            gc.fillRect(thumbX, thumbY, THUMB_SIZE, THUMB_SIZE);
            gc.setStroke(PLACEHOLDER_BORDER);
            gc.setLineWidth(1);
            gc.strokeRect(thumbX + 0.5, thumbY + 0.5, THUMB_SIZE - 1, THUMB_SIZE - 1);
            gc.setFill(PLACEHOLDER_TEXT);
            gc.setFont(FONT_PLACEHOLDER);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText("?", thumbX + THUMB_SIZE / 2.0, thumbY + THUMB_SIZE / 2.0);
        }

        if (selectionModeActive.get()) {
            drawSelectionIndicator(gc, isSelected, x, y);
        }
        
        // Nombre (truncado)
        gc.setFill(TEXT_NAME);
        gc.setFont(FONT_NAME);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.TOP);
        String name = truncate(t.getBaseName(), 20);
        gc.fillText(name, x + CARD_W / 2.0, thumbY + THUMB_SIZE + 4);
        
        // Resolución
        gc.setFill(TEXT_INFO);
        gc.setFont(FONT_INFO);
        gc.fillText(t.getResolutionText(), x + CARD_W / 2.0, thumbY + THUMB_SIZE + 4 + 14);
        
        // Botón Resize
        double btnX = x + CARD_PAD;
        double btnY = y + CARD_H - CARD_PAD - BUTTON_H;
        double btnW = CARD_W - 2 * CARD_PAD;
        gc.setFill(isButtonHover ? BUTTON_BG_HOVER : BUTTON_BG);
        gc.fillRoundRect(btnX, btnY, btnW, BUTTON_H, BUTTON_H, BUTTON_H);
        gc.setStroke(isButtonHover ? BUTTON_BG_HOVER : BUTTON_BORDER);
        gc.setLineWidth(1);
        gc.strokeRoundRect(btnX + 0.5, btnY + 0.5, btnW - 1, BUTTON_H - 1,
                BUTTON_H, BUTTON_H);
        gc.setFill(isButtonHover ? BUTTON_TEXT_HOVER : BUTTON_TEXT);
        gc.setFont(FONT_BUTTON);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("Resize", btnX + btnW / 2.0, btnY + BUTTON_H / 2.0);
    }

    /** Draws an accessible selection affordance without adding scene nodes. */
    private void drawSelectionIndicator(GraphicsContext gc, boolean isSelected,
            double cardX, double cardY) {
        double indicatorX = cardX + CARD_W - CARD_PAD - SELECTION_INDICATOR_SIZE;
        double indicatorY = cardY + CARD_PAD;

        gc.setFill(isSelected ? DesignTokens.INK : DesignTokens.SURFACE_CARD);
        gc.fillOval(indicatorX, indicatorY,
                SELECTION_INDICATOR_SIZE, SELECTION_INDICATOR_SIZE);
        gc.setStroke(isSelected ? DesignTokens.INK : DesignTokens.HAIRLINE_STRONG);
        gc.setLineWidth(1);
        gc.strokeOval(indicatorX + 0.5, indicatorY + 0.5,
                SELECTION_INDICATOR_SIZE - 1, SELECTION_INDICATOR_SIZE - 1);

        if (isSelected) {
            gc.setFill(DesignTokens.ON_PRIMARY);
            gc.setFont(FONT_SELECTION);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText("\u2713", indicatorX + SELECTION_INDICATOR_SIZE / 2.0,
                    indicatorY + SELECTION_INDICATOR_SIZE / 2.0);
        }
    }
    
    /** Trunca con "..." si supera maxLen. */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, Math.max(0, maxLen - 3)) + "...";
    }
    
    // ==================== HIT TESTING ====================
    
    /** @return Índice del item en esas coordenadas del Canvas, o -1 si no hay */
    private int indexAt(double canvasX, double canvasY) {
        if (cols <= 0 || displayList.isEmpty()) return -1;
        
        double offsetY = canvas.getLayoutY();
        double absX = canvasX;
        double absY = canvasY + offsetY;
        
        // Quitar padding
        double localX = absX - PADDING;
        double localY = absY - PADDING;
        if (localX < 0 || localY < 0) return -1;
        
        double colStride = CARD_W + H_GAP;
        double rowStride = CARD_H + V_GAP;
        
        int col = (int) Math.floor(localX / colStride);
        int row = (int) Math.floor(localY / rowStride);
        if (col >= cols) return -1;
        
        // ¿Cae el punto en la card o en el gap?
        double cellX = localX - col * colStride;
        double cellY = localY - row * rowStride;
        if (cellX > CARD_W || cellY > CARD_H) return -1;
        
        int idx = row * cols + col;
        if (idx < 0 || idx >= displayList.size()) return -1;
        return idx;
    }
    
    /** @return true si el punto del Canvas cae en el botón Resize de la card idx */
    private boolean isInButtonZone(double canvasX, double canvasY, int idx) {
        if (idx < 0) return false;
        int row = idx / cols;
        int col = idx % cols;
        
        double offsetY = canvas.getLayoutY();
        double cardX = PADDING + col * (CARD_W + H_GAP);
        double cardY = PADDING + row * (CARD_H + V_GAP) - offsetY;
        
        double btnX = cardX + CARD_PAD;
        double btnY = cardY + CARD_H - CARD_PAD - BUTTON_H;
        double btnW = CARD_W - 2 * CARD_PAD;
        
        return canvasX >= btnX && canvasX <= btnX + btnW
                && canvasY >= btnY && canvasY <= btnY + BUTTON_H;
    }
    
    // ==================== EVENTOS ====================
    
    private void handleMouseMoved(double x, double y) {
        int idx = indexAt(x, y);
        TextureInfo newHover = (idx >= 0) ? displayList.get(idx) : null;
        int newButtonHover = (idx >= 0 && isInButtonZone(x, y, idx)) ? idx : -1;
        
        if (newHover != hoveredItem || newButtonHover != hoveredButtonIndex) {
            hoveredItem = newHover;
            hoveredButtonIndex = newButtonHover;
            requestRepaint();
        }
    }
    
    private void handleMouseClicked(double x, double y) {
        int idx = indexAt(x, y);
        if (idx < 0) return;
        TextureInfo t = displayList.get(idx);
        
        if (isInButtonZone(x, y, idx)) {
            if (onProcessCallback != null) {
                onProcessCallback.accept(t);
            }
            return;
        }
        
        if (selectionModeActive != null && selectionModeActive.get()) {
            toggleSelection(t);
        }
    }
    
    private void toggleSelection(TextureInfo t) {
        boolean newState;
        if (selectedSet.remove(t)) {
            newState = false;
        } else {
            selectedSet.add(t);
            newState = true;
        }
        t.setSelected(newState);
        requestRepaint();
        fireSelectionChanged();
    }
    
    /** Vacía el conjunto de selección y notifica. */
    private void clearSelection() {
        if (selectedSet.isEmpty()) return;
        for (TextureInfo t : selectedSet) {
            t.setSelected(false);
        }
        selectedSet.clear();
        requestRepaint();
        fireSelectionChanged();
    }
    
    private void fireSelectionChanged() {
        if (onSelectionChangedCallback != null) {
            onSelectionChangedCallback.run();
        }
    }
}
