package com.aquasentinel.presentation.controller;

import com.aquasentinel.app.AppBootstrap;
import com.aquasentinel.application.security.Session;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AppShellController {
    private static final Logger log = LoggerFactory.getLogger(AppShellController.class);

    @FXML
    private Button navDashboard;
    @FXML
    private Button navSources;
    @FXML
    private Button navInspections;
    @FXML
    private Button navSamples;
    @FXML
    private Button navImports;
    @FXML
    private Button navAlerts;
    @FXML
    private Button navReports;
    @FXML
    private Button navAdmin;

    @FXML
    private Label lblHeaderTitle;
    @FXML
    private Label lblHeaderSubtitle;
    @FXML
    private Label lblCurrentUser;
    @FXML
    private Label lblCurrentRole;

    @FXML
    private StackPane workspaceContainer;

    private AppBootstrap bootstrap;
    private Session session;

    @FXML
    public void initialize() {
        // Will be triggered by initApp once bootstrap is injected
    }

    public void initApp(AppBootstrap bootstrap) {
        this.bootstrap = bootstrap;
        this.session = bootstrap.getOrCreateAdminSession();

        if (lblCurrentUser != null && session != null) {
            lblCurrentUser.setText(session.username());
        }
        if (lblCurrentRole != null && session != null) {
            lblCurrentRole.setText("Role: " + session.role());
        }

        handleNavDashboard();
    }

    private void setActiveButton(Button activeButton) {
        Button[] buttons = {navDashboard, navSources, navInspections, navSamples, navImports, navAlerts, navReports, navAdmin};
        for (Button btn : buttons) {
            if (btn != null) btn.getStyleClass().remove("sidebar-btn-active");
        }
        if (activeButton != null && !activeButton.getStyleClass().contains("sidebar-btn-active")) {
            activeButton.getStyleClass().add("sidebar-btn-active");
        }
    }

    @FXML
    public void handleNavDashboard() {
        setActiveButton(navDashboard);
        lblHeaderTitle.setText("Operational Dashboard");
        lblHeaderSubtitle.setText("System summary, recent samples, and monitoring overview");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            Node view = loader.load();
            DashboardController controller = loader.getController();
            if (controller != null && bootstrap != null) {
                controller.init(bootstrap, session);
            }
            workspaceContainer.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Failed to load dashboard.fxml", e);
        }
    }

    @FXML
    public void handleNavSources() {
        setActiveButton(navSources);
        lblHeaderTitle.setText("Water Sources");
        lblHeaderSubtitle.setText("Manage water sampling points, geographic locations, and monitoring status");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/sources.fxml"));
            Node view = loader.load();
            SourcesController controller = loader.getController();
            if (controller != null && bootstrap != null) {
                controller.init(bootstrap, session);
            }
            workspaceContainer.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Failed to load sources.fxml", e);
        }
    }

    @FXML
    public void handleNavInspections() {
        setActiveButton(navInspections);
        lblHeaderTitle.setText("Field Inspections");
        lblHeaderSubtitle.setText("Schedule and manage field inspection tours and sampling assignments");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/inspections.fxml"));
            Node view = loader.load();
            InspectionsController controller = loader.getController();
            if (controller != null && bootstrap != null) {
                controller.init(bootstrap, session);
            }
            workspaceContainer.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Failed to load inspections.fxml", e);
        }
    }

    @FXML
    public void handleNavSamples() {
        setActiveButton(navSamples);
        lblHeaderTitle.setText("Water Samples & Readings");
        lblHeaderSubtitle.setText("Track sample lifecycle, enter physical readings, and evaluate compliance");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/samples.fxml"));
            Node view = loader.load();
            SamplesController controller = loader.getController();
            if (controller != null && bootstrap != null) {
                controller.init(bootstrap, session);
            }
            workspaceContainer.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Failed to load samples.fxml", e);
        }
    }

    @FXML
    public void handleNavImports() {
        setActiveButton(navImports);
        lblHeaderTitle.setText("Data Imports & Provenance");
        lblHeaderSubtitle.setText("Import external CSV datasets with RFC 4180 parsing and SHA-256 deduplication");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/imports.fxml"));
            Node view = loader.load();
            ImportsController controller = loader.getController();
            if (controller != null && bootstrap != null) {
                controller.init(bootstrap, session);
            }
            workspaceContainer.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Failed to load imports.fxml", e);
        }
    }

    @FXML
    public void handleNavAlerts() {
        setActiveButton(navAlerts);
        lblHeaderTitle.setText("Alerts & Corrective Actions");
        lblHeaderSubtitle.setText("Track profile violations, remediation tasks, and verified retest closures");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/alerts.fxml"));
            Node view = loader.load();
            AlertsController controller = loader.getController();
            if (controller != null && bootstrap != null) {
                controller.init(bootstrap, session);
            }
            workspaceContainer.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Failed to load alerts.fxml", e);
        }
    }

    @FXML
    public void handleNavReports() {
        setActiveButton(navReports);
        lblHeaderTitle.setText("Reports & Audits");
        lblHeaderSubtitle.setText("Generate reproducible PDF / CSV quality evaluation reports with standard disclaimers");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/reports.fxml"));
            Node view = loader.load();
            ReportsController controller = loader.getController();
            if (controller != null && bootstrap != null) {
                controller.init(bootstrap, session);
            }
            workspaceContainer.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Failed to load reports.fxml", e);
        }
    }

    @FXML
    public void handleNavAdmin() {
        setActiveButton(navAdmin);
        lblHeaderTitle.setText("Administration & Governance");
        lblHeaderSubtitle.setText("Manage user accounts, RBAC roles, threshold profile versions, and storage");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/admin.fxml"));
            Node view = loader.load();
            AdminController controller = loader.getController();
            if (controller != null && bootstrap != null) {
                controller.init(bootstrap, session);
            }
            workspaceContainer.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Failed to load admin.fxml", e);
        }
    }
}
