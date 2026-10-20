package com.aquasentinel.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AquaSentinelApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(AquaSentinelApp.class);

    private AppBootstrap bootstrap;

    @Override
    public void init() throws Exception {
        AppContext context = AppContext.fromArgs(getParameters().getRaw().toArray(new String[0]));
        bootstrap = new AppBootstrap(context);
        bootstrap.initialize();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        log.info("Starting AquaSentinel UI...");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/app-shell.fxml"));
        Parent root = loader.load();

        com.aquasentinel.presentation.controller.AppShellController shellController = loader.getController();
        if (shellController != null) {
            shellController.initApp(bootstrap);
        }

        Scene scene = new Scene(root, 1440, 900);
        primaryStage.setTitle("AquaSentinel — Water Quality Monitoring Management System");
        try {
            primaryStage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/icons/app-icon.png")));
        } catch (Exception e) {
            log.warn("Could not load window icon", e);
        }
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(720);
        primaryStage.setScene(scene);
        primaryStage.show();
        primaryStage.toFront();
        primaryStage.requestFocus();
        log.info("AquaSentinel UI successfully launched.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
