package view;

import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import controller.FXMLInicioController;

/**
 * Clase principal de Bedrock Texture Resizer: reduce manualmente, textura a
 * textura, la resolución de las texturas de un resource pack.
 * 
 * - Carga texturas de textures/blocks, items, environment y entity
 * - Soporta PNG y TGA (con canal alfa)
 * - Detecta texturas MER (_mer, _mers) y normales (_normal, _n) asociadas
 * - Redimensiona con Area Resampling
 * - Una pestaña por tipo de textura
 * 
 * @author vmonge
 * @version 1.2.0
 */
public class Iron extends Application {
    
    /** Controlador principal, necesario para liberar recursos al cerrar. */
    private FXMLInicioController controller;
    
    /** Configura y muestra la ventana principal. */
    @Override
    public void start(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/FXMLInicio.fxml"));
            Parent vista = loader.load();
            DesignTokens.install(vista);
            
            controller = loader.getController();
            
            Scene escena = new Scene(vista);
            
            stage.setScene(escena);
            stage.setTitle("Bedrock Texture Resizer");
            stage.setMinWidth(1200);
            stage.setMinHeight(600);
            stage.setWidth(1440);
            stage.setHeight(800);
            
            stage.setOnCloseRequest(event -> {
                if (controller != null) {
                    controller.shutdown();
                }
            });
            
            stage.show();
            
        } catch (IOException e) {
            System.err.println("Error loading the interface: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /** Libera los recursos del controlador al cerrar. */
    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    /** @param args Argumentos de línea de comandos (no usados) */
    public static void main(String[] args) {
        launch(args);
    }
}
