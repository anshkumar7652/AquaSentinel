package com.aquasentinel.presentation.controller;

import com.aquasentinel.app.AppBootstrap;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.exception.AuthenticationException;
import com.aquasentinel.domain.exception.ValidationException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LoginController {
    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @FXML
    private Label lblTitle;
    @FXML
    private Label lblSubtitle;
    @FXML
    private Label lblError;
    @FXML
    private TextField txtUsername;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private VBox confirmPasswordBox;
    @FXML
    private PasswordField txtConfirmPassword;
    @FXML
    private Button btnAction;
    @FXML
    private VBox demoAccountsBox;

    private AppBootstrap bootstrap;
    private Stage stage;
    private boolean isSetupMode = false;

    public void init(AppBootstrap bootstrap, Stage stage) {
        this.bootstrap = bootstrap;
        this.stage = stage;

        this.isSetupMode = bootstrap.getAuthenticationService().isSetupRequired();
        if (isSetupMode) {
            lblTitle.setText("Initial Admin Setup");
            lblSubtitle.setText("Create the master administrator account for this installation.");
            btnAction.setText("Create Administrator Account");
            confirmPasswordBox.setVisible(true);
            confirmPasswordBox.setManaged(true);
            if (demoAccountsBox != null) {
                demoAccountsBox.setVisible(false);
                demoAccountsBox.setManaged(false);
            }
        } else {
            lblTitle.setText("Sign In to AquaSentinel");
            lblSubtitle.setText("Enter your credentials to access the water quality monitoring system.");
            btnAction.setText("Sign In");
            confirmPasswordBox.setVisible(false);
            confirmPasswordBox.setManaged(false);
            if (demoAccountsBox != null) {
                demoAccountsBox.setVisible(true);
                demoAccountsBox.setManaged(true);
            }
        }

        Platform.runLater(() -> txtUsername.requestFocus());
    }

    @FXML
    public void handleAction() {
        lblError.setText("");
        lblError.setVisible(false);
        lblError.setManaged(false);

        String username = txtUsername.getText() != null ? txtUsername.getText().trim() : "";
        char[] password = txtPassword.getText() != null ? txtPassword.getText().toCharArray() : new char[0];

        if (username.isEmpty()) {
            showError("Please enter your username.");
            return;
        }

        if (password.length == 0) {
            showError("Please enter your password.");
            return;
        }

        try {
            Session session;
            if (isSetupMode) {
                char[] confirmPassword = txtConfirmPassword.getText() != null ? txtConfirmPassword.getText().toCharArray() : new char[0];
                if (password.length < 8) {
                    showError("Password must be at least 8 characters long.");
                    return;
                }
                if (!java.util.Arrays.equals(password, confirmPassword)) {
                    showError("Passwords do not match.");
                    return;
                }
                session = bootstrap.getAuthenticationService().setupInitialAdmin(username, password);
                log.info("Initial admin setup completed by user: {}", username);
            } else {
                session = bootstrap.getAuthenticationService().authenticate(username, password);
                log.info("User {} successfully authenticated.", username);
            }

            navigateToAppShell(session);
        } catch (AuthenticationException | ValidationException ex) {
            log.warn("Authentication failed for user {}: {}", username, ex.getMessage());
            showError(ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error during authentication", ex);
            showError("An unexpected error occurred. Please try again.");
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    @FXML
    public void handleFillAdminDemo() {
        txtUsername.setText("admin");
        txtPassword.setText("Admin123!");
    }

    @FXML
    public void handleFillAnalystDemo() {
        txtUsername.setText("analyst1");
        txtPassword.setText("Analyst123!");
    }

    private void showError(String message) {
        lblError.setText(message);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void navigateToAppShell(Session session) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/app-shell.fxml"));
            Parent root = loader.load();

            AppShellController shellController = loader.getController();
            if (shellController != null) {
                shellController.initApp(bootstrap, session, stage);
            }

            Scene scene = new Scene(root, 1440, 900);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            log.error("Failed to load app-shell.fxml", e);
            showError("Failed to load application workspace.");
        }
    }
}
