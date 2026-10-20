package com.aquasentinel.presentation.controller;

import com.aquasentinel.app.AppBootstrap;
import com.aquasentinel.application.dto.ReportSnapshot;
import com.aquasentinel.application.port.EvaluationRepository;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.evaluation.RuleEvaluationResult;
import com.aquasentinel.infrastructure.csv.CsvReportExporter;
import com.aquasentinel.infrastructure.pdf.PdfReportExporter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

public class ReportsController {
    private static final Logger log = LoggerFactory.getLogger(ReportsController.class);

    @FXML
    private TableView<EvaluationRepository.StoredEvaluation> tableEvaluations;
    @FXML
    private TableColumn<EvaluationRepository.StoredEvaluation, String> colEvalId;
    @FXML
    private TableColumn<EvaluationRepository.StoredEvaluation, String> colSampleCode;
    @FXML
    private TableColumn<EvaluationRepository.StoredEvaluation, String> colOutcome;
    @FXML
    private TableColumn<EvaluationRepository.StoredEvaluation, String> colEvalDate;

    @FXML
    private Label lblSnapshotSource;
    @FXML
    private Label lblSnapshotSample;
    @FXML
    private Label lblSnapshotProfile;
    @FXML
    private Label lblSnapshotOutcome;
    @FXML
    private Label lblSnapshotDigest;

    @FXML
    private TableView<RuleEvaluationResult> tableRules;
    @FXML
    private TableColumn<RuleEvaluationResult, String> colRuleParam;
    @FXML
    private TableColumn<RuleEvaluationResult, String> colRuleObserved;
    @FXML
    private TableColumn<RuleEvaluationResult, String> colRuleLimits;
    @FXML
    private TableColumn<RuleEvaluationResult, String> colRulePass;

    @FXML
    private Button btnExportPdf;
    @FXML
    private Button btnExportCsv;

    private AppBootstrap bootstrap;
    private Session session;
    private ReportSnapshot currentSnapshot;
    private final ObservableList<EvaluationRepository.StoredEvaluation> evalList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colEvalId.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().id().toString().substring(0, 8)));
        colSampleCode.setCellValueFactory(c -> {
            String code = "Sample";
            try {
                if (bootstrap != null) {
                    var s = bootstrap.getSampleRepository().findById(c.getValue().sampleId());
                    if (s.isPresent()) code = s.get().getCode();
                }
            } catch (Exception ignored) {}
            return new SimpleStringProperty(code);
        });
        colOutcome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().outcome().name()));
        colEvalDate.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().evaluatedAt() != null ? c.getValue().evaluatedAt().toString().substring(0, 19).replace("T", " ") : ""
        ));

        tableEvaluations.setItems(evalList);

        tableEvaluations.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadSnapshot(newVal.id());
            }
        });

        colRuleParam.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().parameterCode()));
        colRuleObserved.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().observedValue() != null ? c.getValue().observedValue().toString() : "MISSING"
        ));
        colRuleLimits.setCellValueFactory(c -> {
            String lower = c.getValue().lowerLimit() != null ? (c.getValue().lowerInclusive() ? ">= " : "> ") + c.getValue().lowerLimit() : "";
            String upper = c.getValue().upperLimit() != null ? (c.getValue().upperInclusive() ? "<= " : "< ") + c.getValue().upperLimit() : "";
            String lim = (lower + " " + upper).trim();
            return new SimpleStringProperty(lim.isEmpty() ? "No bound" : lim);
        });
        colRulePass.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().outcome().name()));
    }

    public void init(AppBootstrap bootstrap, Session session) {
        this.bootstrap = bootstrap;
        this.session = session;
        refreshEvaluations();
    }

    public void refreshEvaluations() {
        if (bootstrap == null) return;
        List<EvaluationRepository.StoredEvaluation> list = bootstrap.getEvaluationRepository().findAll();
        evalList.setAll(list);
        if (!list.isEmpty()) {
            tableEvaluations.getSelectionModel().selectFirst();
            loadSnapshot(list.getFirst().id());
        }
    }

    private void loadSnapshot(UUID evalId) {
        if (bootstrap == null || session == null) return;
        try {
            currentSnapshot = bootstrap.getReportService().createSnapshot(session, evalId);
            lblSnapshotSource.setText("Water Source: " + currentSnapshot.sourceName() + " (" + currentSnapshot.sourceCode() + ")");
            lblSnapshotSample.setText("Sample: " + currentSnapshot.sampleCode() + " | Collected: " + currentSnapshot.collectedAt().toString().substring(0, 16).replace("T", " "));
            lblSnapshotProfile.setText("Profile: " + currentSnapshot.profileName() + " v" + currentSnapshot.profileVersion());
            lblSnapshotOutcome.setText("Outcome: " + currentSnapshot.outcome());
            lblSnapshotDigest.setText("SHA-256 Digest: " + currentSnapshot.inputDigest().substring(0, 24) + "...");

            tableRules.setItems(FXCollections.observableArrayList(currentSnapshot.ruleResults()));
            btnExportPdf.setDisable(false);
            btnExportCsv.setDisable(false);
        } catch (Exception e) {
            log.error("Could not load report snapshot: {}", e.getMessage());
        }
    }

    @FXML
    public void handleExportPdf() {
        if (currentSnapshot == null) return;
        try {
            Path exportDir = Paths.get(System.getProperty("user.home"), "AquaSentinel", "reports");
            Files.createDirectories(exportDir);
            String filename = "Report_" + currentSnapshot.sampleCode() + "_" + System.currentTimeMillis() + ".pdf";
            File targetFile = exportDir.resolve(filename).toFile();

            PdfReportExporter exporter = new PdfReportExporter();
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                exporter.export(currentSnapshot, fos);
            }

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("PDF Report Exported");
            alert.setHeaderText("PDF report successfully written to disk.");
            alert.setContentText("File saved at:\n" + targetFile.getAbsolutePath() + "\n\nWould you like to open the PDF file now?");
            ButtonType btnOpen = new ButtonType("Open PDF", ButtonBar.ButtonData.OK_DONE);
            alert.getButtonTypes().setAll(btnOpen, ButtonType.CLOSE);

            alert.showAndWait().ifPresent(res -> {
                if (res == btnOpen) {
                    try {
                        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(targetFile);
                    } catch (Exception ex) {
                        log.warn("Cannot open PDF viewer: {}", ex.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Alert err = new Alert(Alert.AlertType.ERROR, "Export error: " + e.getMessage(), ButtonType.OK);
            err.showAndWait();
        }
    }

    @FXML
    public void handleExportCsv() {
        if (currentSnapshot == null) return;
        try {
            Path exportDir = Paths.get(System.getProperty("user.home"), "AquaSentinel", "reports");
            Files.createDirectories(exportDir);
            String filename = "Report_" + currentSnapshot.sampleCode() + "_" + System.currentTimeMillis() + ".csv";
            File targetFile = exportDir.resolve(filename).toFile();

            CsvReportExporter exporter = new CsvReportExporter();
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                exporter.export(currentSnapshot, fos);
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("CSV Report Exported");
            alert.setHeaderText("CSV export complete with spreadsheet formula escaping.");
            alert.setContentText("File saved at:\n" + targetFile.getAbsolutePath());
            alert.showAndWait();
        } catch (Exception e) {
            Alert err = new Alert(Alert.AlertType.ERROR, "Export error: " + e.getMessage(), ButtonType.OK);
            err.showAndWait();
        }
    }
}
