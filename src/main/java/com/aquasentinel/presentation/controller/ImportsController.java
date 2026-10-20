package com.aquasentinel.presentation.controller;

import com.aquasentinel.app.AppBootstrap;
import com.aquasentinel.application.dto.ImportPreviewDto;
import com.aquasentinel.application.dto.ImportPreviewDto.ImportMetadata;
import com.aquasentinel.application.dto.ImportPreviewDto.ParsedImportRow;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.RecordOrigin;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

public class ImportsController {
    private static final Logger log = LoggerFactory.getLogger(ImportsController.class);

    @FXML
    private Label lblSelectedFile;
    @FXML
    private Label lblFileHash;
    @FXML
    private Label lblRowCount;
    @FXML
    private Label lblPointCount;
    @FXML
    private Label lblSummaryCount;
    @FXML
    private Label lblValidationStatus;

    @FXML
    private TextField txtDatasetTitle;
    @FXML
    private TextField txtPublisher;
    @FXML
    private TextField txtSourceUrl;

    @FXML
    private Button btnCommit;

    @FXML
    private TableView<ParsedImportRow> tablePreview;
    @FXML
    private TableColumn<ParsedImportRow, String> colRow;
    @FXML
    private TableColumn<ParsedImportRow, String> colSourceCode;
    @FXML
    private TableColumn<ParsedImportRow, String> colRecordCode;
    @FXML
    private TableColumn<ParsedImportRow, String> colKind;
    @FXML
    private TableColumn<ParsedImportRow, String> colParam;
    @FXML
    private TableColumn<ParsedImportRow, String> colValue;
    @FXML
    private TableColumn<ParsedImportRow, String> colUnit;
    @FXML
    private TableColumn<ParsedImportRow, String> colStatus;

    private AppBootstrap bootstrap;
    private Session session;
    private ImportPreviewDto currentPreview;

    @FXML
    public void initialize() {
        colRow.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().rowNumber())));
        colSourceCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().sourceCode()));
        colRecordCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().recordCode()));
        colKind.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().recordKind().name()));
        colParam.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().parameterCode()));
        colValue.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().value() != null ? c.getValue().value().toString() : ""));
        colUnit.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().unit() != null ? c.getValue().unit() : ""));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().rowError() == null ? "VALID" : c.getValue().rowError()
        ));
    }

    public void init(AppBootstrap bootstrap, Session session) {
        this.bootstrap = bootstrap;
        this.session = session;
    }

    @FXML
    public void handleChooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Water Quality CSV Dataset");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        File file = chooser.showOpenDialog(lblSelectedFile.getScene().getWindow());
        if (file != null) {
            stageAndPreviewFile(file.toPath());
        }
    }

    @FXML
    public void handleLoadDemoFixture() {
        Path demoCsv = Paths.get("data", "demo", "delhi_monitoring_demo.csv");
        if (demoCsv.toFile().exists()) {
            stageAndPreviewFile(demoCsv);
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Demo CSV fixture not found at data/demo/delhi_monitoring_demo.csv", ButtonType.OK);
            alert.showAndWait();
        }
    }

    private void stageAndPreviewFile(Path path) {
        try {
            ImportMetadata meta = new ImportMetadata(
                    txtPublisher.getText().trim().isEmpty() ? "Delhi Jal Board (Educational Demo)" : txtPublisher.getText().trim(),
                    txtDatasetTitle.getText().trim().isEmpty() ? "Delhi Surface Water Monitoring Demo" : txtDatasetTitle.getText().trim(),
                    txtSourceUrl.getText().trim().isEmpty() ? "https://data.gov.in/dataset/water-quality-demo" : txtSourceUrl.getText().trim(),
                    LocalDate.now().toString(),
                    "Public Educational Use",
                    "RFC 4180 parsing with SHA-256 deduplication",
                    RecordOrigin.HISTORICAL_IMPORT
            );

            currentPreview = bootstrap.getImportService().preview(session, path, meta);

            lblSelectedFile.setText("Selected: " + path.getFileName());
            lblFileHash.setText("SHA-256: " + currentPreview.fileSha256().substring(0, 24) + "...");
            lblRowCount.setText(String.valueOf(currentPreview.totalRows()));
            lblPointCount.setText(String.valueOf(currentPreview.pointSampleCount()));
            lblSummaryCount.setText(String.valueOf(currentPreview.periodSummaryCount()));

            if (currentPreview.validToCommit()) {
                lblValidationStatus.setText("READY TO COMMIT (Valid RFC 4180)");
                lblValidationStatus.setStyle("-fx-text-fill: #166534; -fx-font-weight: bold;");
                btnCommit.setDisable(false);
            } else {
                lblValidationStatus.setText("VALIDATION ERRORS FOUND (" + currentPreview.validationErrors().size() + ")");
                lblValidationStatus.setStyle("-fx-text-fill: #991B1B; -fx-font-weight: bold;");
                btnCommit.setDisable(true);
            }

            tablePreview.setItems(FXCollections.observableArrayList(currentPreview.sampleRows()));
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to parse CSV: " + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    @FXML
    public void handleCommit() {
        if (currentPreview == null || !currentPreview.validToCommit()) return;
        try {
            var receipt = bootstrap.getImportService().commit(session, currentPreview.previewId());
            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.setTitle("Import Successful");
            success.setHeaderText("Dataset Committed Successfully");
            success.setContentText("Batch ID: " + receipt.batchId() + "\n"
                    + "Samples Created: " + receipt.samplesCreated() + "\n"
                    + "Summaries Saved: " + receipt.summariesSaved());
            success.showAndWait();
            btnCommit.setDisable(true);
            lblValidationStatus.setText("COMMITTED (Batch " + receipt.batchId().toString().substring(0, 8) + ")");
            lblValidationStatus.setStyle("-fx-text-fill: #1D4ED8; -fx-font-weight: bold;");
        } catch (Exception e) {
            Alert err = new Alert(Alert.AlertType.ERROR, "Commit failed: " + e.getMessage(), ButtonType.OK);
            err.showAndWait();
        }
    }
}
