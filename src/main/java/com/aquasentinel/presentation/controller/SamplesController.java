package com.aquasentinel.presentation.controller;

import com.aquasentinel.app.AppBootstrap;
import com.aquasentinel.application.dto.EvaluationDto;
import com.aquasentinel.application.dto.InspectionDto;
import com.aquasentinel.application.dto.SampleDto;
import com.aquasentinel.application.dto.SourceDto;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.RecordOrigin;
import com.aquasentinel.domain.enums.SampleStatus;
import com.aquasentinel.domain.model.ProfileVersion;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SamplesController {
    private static final Logger log = LoggerFactory.getLogger(SamplesController.class);

    @FXML
    private TableView<SampleDto> tableSamples;
    @FXML
    private TableColumn<SampleDto, String> colCode;
    @FXML
    private TableColumn<SampleDto, String> colSource;
    @FXML
    private TableColumn<SampleDto, String> colCollected;
    @FXML
    private TableColumn<SampleDto, String> colStatus;
    @FXML
    private TableColumn<SampleDto, String> colOrigin;
    @FXML
    private Label lblSampleCount;

    private AppBootstrap bootstrap;
    private Session session;
    private final ObservableList<SampleDto> masterList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().code()));
        colSource.setCellValueFactory(c -> {
            String name = "Monitored Point";
            try {
                if (bootstrap != null) {
                    var insp = bootstrap.getInspectionRepository().findById(c.getValue().inspectionId());
                    if (insp.isPresent()) {
                        var src = bootstrap.getSourceRepository().findById(insp.get().getSourceId());
                        if (src.isPresent()) name = src.get().getName() + " (" + src.get().getCode() + ")";
                    }
                }
            } catch (Exception ignored) {}
            return new SimpleStringProperty(name);
        });
        colCollected.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().collectedAt() != null ? c.getValue().collectedAt().toString().replace("T", " ").substring(0, 19) : "Pending collection"
        ));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status().name()));
        colOrigin.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().origin() != null ? c.getValue().origin().name() : "MANUAL_ENTRY"
        ));

        tableSamples.setItems(masterList);
    }

    public void init(AppBootstrap bootstrap, Session session) {
        this.bootstrap = bootstrap;
        this.session = session;
        refreshTable();
    }

    public void refreshTable() {
        if (bootstrap == null) return;
        List<SampleDto> list = bootstrap.getSampleRepository().findAll(0, 1000)
                .stream().map(SampleDto::from).toList();
        masterList.setAll(list);
        if (lblSampleCount != null) {
            lblSampleCount.setText("Total water samples: " + list.size());
        }
    }

    @FXML
    public void handleRegisterSample() {
        if (bootstrap == null || session == null) return;

        List<InspectionDto> inspections = bootstrap.getInspectionRepository().findAll(0, 1000)
                .stream().map(InspectionDto::from).toList();
        if (inspections.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please schedule at least one inspection tour before registering samples.", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        Dialog<SampleDto> dialog = new Dialog<>();
        dialog.setTitle("Register New Water Sample");
        dialog.setHeaderText("Create a formal laboratory sample record linked to an inspection.");

        ButtonType btnSave = new ButtonType("Register Sample", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSave, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField txtCode = new TextField("SMP-DEL-0" + (masterList.size() + 1));
        ComboBox<InspectionDto> cmbInsp = new ComboBox<>(FXCollections.observableArrayList(inspections));
        cmbInsp.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(InspectionDto i) {
                if (i == null) return "";
                String sName = "Source " + i.sourceId();
                var s = bootstrap.getSourceRepository().findById(i.sourceId());
                if (s.isPresent()) sName = s.get().getName();
                return sName + " (" + i.dueDate() + ")";
            }
            @Override
            public InspectionDto fromString(String string) { return null; }
        });
        cmbInsp.getSelectionModel().selectFirst();

        ComboBox<RecordOrigin> cmbOrigin = new ComboBox<>(FXCollections.observableArrayList(RecordOrigin.MANUAL_ENTRY, RecordOrigin.HISTORICAL_IMPORT));
        cmbOrigin.getSelectionModel().select(RecordOrigin.MANUAL_ENTRY);

        grid.add(new Label("Sample Code:"), 0, 0);
        grid.add(txtCode, 1, 0);
        grid.add(new Label("Inspection Context:"), 0, 1);
        grid.add(cmbInsp, 1, 1);
        grid.add(new Label("Data Origin:"), 0, 2);
        grid.add(cmbOrigin, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == btnSave) {
                try {
                    return bootstrap.getSampleService().registerSample(
                            session, txtCode.getText().trim().toUpperCase(), cmbInsp.getValue().id(), cmbOrigin.getValue()
                    );
                } catch (Exception e) {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Registration failed: " + e.getMessage(), ButtonType.OK);
                    err.showAndWait();
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(res -> {
            refreshTable();
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Sample " + res.code() + " registered successfully!", ButtonType.OK);
            alert.showAndWait();
        });
    }

    @FXML
    public void handleRecordReadings() {
        SampleDto selected = tableSamples.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please select a sample from the table first.", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Record Laboratory Readings & Submit");
        dialog.setHeaderText("Record physical readings for sample: " + selected.code());

        ButtonType btnSubmit = new ButtonType("Save & Submit", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSubmit, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField txtPh = new TextField("7.35");
        TextField txtTurb = new TextField("3.2");
        TextField txtDo = new TextField("6.5");
        TextField txtTds = new TextField("380.0");

        grid.add(new Label("pH Level (1):"), 0, 0);
        grid.add(txtPh, 1, 0);
        grid.add(new Label("Turbidity (NTU):"), 0, 1);
        grid.add(txtTurb, 1, 1);
        grid.add(new Label("Dissolved Oxygen (mg/L):"), 0, 2);
        grid.add(txtDo, 1, 2);
        grid.add(new Label("Total Dissolved Solids (mg/L):"), 0, 3);
        grid.add(txtTds, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == btnSubmit) {
                try {
                    Instant now = Instant.now();
                    if (selected.status() == SampleStatus.REGISTERED) {
                        bootstrap.getSampleService().recordCollection(session, selected.id(), now.minusSeconds(600), session.userId());
                        bootstrap.getSampleService().recordReceipt(session, selected.id(), now.minusSeconds(300));
                    }

                    Map<String, BigDecimal> readings = new HashMap<>();
                    if (!txtPh.getText().isBlank()) readings.put("PH", new BigDecimal(txtPh.getText().trim()));
                    if (!txtTurb.getText().isBlank()) readings.put("TURB", new BigDecimal(txtTurb.getText().trim()));
                    if (!txtDo.getText().isBlank()) readings.put("DO", new BigDecimal(txtDo.getText().trim()));
                    if (!txtTds.getText().isBlank()) readings.put("TDS", new BigDecimal(txtTds.getText().trim()));

                    bootstrap.getSampleService().enterDraftReadings(session, selected.id(), readings);
                    bootstrap.getSampleService().submitSample(session, selected.id());
                    return true;
                } catch (Exception e) {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Submission failed: " + e.getMessage(), ButtonType.OK);
                    err.showAndWait();
                    return false;
                }
            }
            return false;
        });

        dialog.showAndWait().ifPresent(success -> {
            if (success) {
                refreshTable();
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Readings recorded and sample locked for evaluation!", ButtonType.OK);
                alert.showAndWait();
            }
        });
    }

    @FXML
    public void handleEvaluateSample() {
        SampleDto selected = tableSamples.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please select a submitted sample from the table first.", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        List<ProfileVersion> profiles = bootstrap.getProfileRepository().findAll();
        if (profiles.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "No published evaluation profiles found.", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        ProfileVersion profile = profiles.getFirst();
        try {
            EvaluationDto eval = bootstrap.getEvaluationService().evaluate(session, selected.id(), profile.getId());
            Alert resAlert = new Alert(Alert.AlertType.INFORMATION);
            resAlert.setTitle("Evaluation Result");
            resAlert.setHeaderText("Outcome: " + eval.outcome());
            resAlert.setContentText("Sample " + selected.code() + " evaluated against " + profile.getName() + ".\n\n"
                    + "Outcome: " + eval.outcome() + "\n"
                    + "Input Digest: " + eval.inputDigest().substring(0, 16) + "...\n"
                    + (eval.outcome().name().equals("EXCEEDS_PROFILE") ? "\n⚠️ Profile exceeded! An automated Alert has been raised in Alerts & Actions." : "\n✅ Compliant with profile thresholds."));
            resAlert.showAndWait();
            refreshTable();
        } catch (Exception e) {
            Alert err = new Alert(Alert.AlertType.ERROR, "Evaluation error: " + e.getMessage(), ButtonType.OK);
            err.showAndWait();
        }
    }
}
