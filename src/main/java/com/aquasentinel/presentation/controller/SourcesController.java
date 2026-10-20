package com.aquasentinel.presentation.controller;

import com.aquasentinel.app.AppBootstrap;
import com.aquasentinel.application.dto.SourceDto;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.SourceType;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SourcesController {
    private static final Logger log = LoggerFactory.getLogger(SourcesController.class);

    @FXML
    private TextField txtSearch;
    @FXML
    private Button btnNewSource;
    @FXML
    private TableView<SourceDto> tableSources;
    @FXML
    private TableColumn<SourceDto, String> colCode;
    @FXML
    private TableColumn<SourceDto, String> colName;
    @FXML
    private TableColumn<SourceDto, String> colType;
    @FXML
    private TableColumn<SourceDto, String> colIntendedUse;
    @FXML
    private TableColumn<SourceDto, String> colLocation;
    @FXML
    private TableColumn<SourceDto, String> colStatus;
    @FXML
    private Label lblSourceCount;

    private AppBootstrap bootstrap;
    private Session session;
    private final ObservableList<SourceDto> masterList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colCode.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().code()));
        colName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name()));
        colType.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().sourceType().name()));
        colIntendedUse.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().intendedUse()));
        colLocation.setCellValueFactory(cell -> {
            SourceDto s = cell.getValue();
            String coords = (s.latitude() != null && s.longitude() != null)
                    ? String.format(" (%.4f, %.4f)", s.latitude(), s.longitude()) : "";
            return new SimpleStringProperty(s.locationText() + coords);
        });
        colStatus.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().active() ? "Active" : "Archived"));

        FilteredList<SourceDto> filteredList = new FilteredList<>(masterList, p -> true);
        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
                filteredList.setPredicate(source -> {
                    if (newVal == null || newVal.isBlank()) return true;
                    String filter = newVal.toLowerCase().trim();
                    return source.code().toLowerCase().contains(filter)
                            || source.name().toLowerCase().contains(filter)
                            || source.locationText().toLowerCase().contains(filter);
                });
                updateCountLabel(filteredList.size());
            });
        }
        tableSources.setItems(filteredList);
    }

    public void init(AppBootstrap bootstrap, Session session) {
        this.bootstrap = bootstrap;
        this.session = session;
        refreshTable();
    }

    public void refreshTable() {
        if (bootstrap == null || session == null) return;
        List<SourceDto> sources = bootstrap.getSourceService().listSources(session, 0, 1000);
        masterList.setAll(sources);
        updateCountLabel(sources.size());
    }

    private void updateCountLabel(int count) {
        if (lblSourceCount != null) {
            lblSourceCount.setText("Showing " + count + " monitored water source(s)");
        }
    }

    @FXML
    public void handleNewSource() {
        if (bootstrap == null || session == null) return;

        Dialog<SourceDto> dialog = new Dialog<>();
        dialog.setTitle("Register Monitored Water Source");
        dialog.setHeaderText("Add a new geographical water sampling point to AquaSentinel.");

        ButtonType btnSave = new ButtonType("Register Source", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSave, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField txtCode = new TextField("SRC-");
        TextField txtName = new TextField();
        ComboBox<SourceType> cmbType = new ComboBox<>(FXCollections.observableArrayList(SourceType.values()));
        cmbType.getSelectionModel().select(SourceType.RIVER);
        TextField txtUse = new TextField("Drinking & Irrigation");
        TextField txtLoc = new TextField();
        TextField txtLat = new TextField("28.6139");
        TextField txtLon = new TextField("77.2090");

        grid.add(new Label("Source Code:"), 0, 0);
        grid.add(txtCode, 1, 0);
        grid.add(new Label("Source Name:"), 0, 1);
        grid.add(txtName, 1, 1);
        grid.add(new Label("Source Type:"), 0, 2);
        grid.add(cmbType, 1, 2);
        grid.add(new Label("Intended Use:"), 0, 3);
        grid.add(txtUse, 1, 3);
        grid.add(new Label("Location Description:"), 0, 4);
        grid.add(txtLoc, 1, 4);
        grid.add(new Label("Latitude:"), 0, 5);
        grid.add(txtLat, 1, 5);
        grid.add(new Label("Longitude:"), 0, 6);
        grid.add(txtLon, 1, 6);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnSave) {
                try {
                    Double lat = txtLat.getText().isBlank() ? null : Double.parseDouble(txtLat.getText().trim());
                    Double lon = txtLon.getText().isBlank() ? null : Double.parseDouble(txtLon.getText().trim());
                    return bootstrap.getSourceService().createSource(
                            session,
                            txtCode.getText().trim().toUpperCase(),
                            txtName.getText().trim(),
                            cmbType.getValue(),
                            txtUse.getText().trim(),
                            txtLoc.getText().trim(),
                            lat, lon
                    );
                } catch (Exception e) {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Registration failed: " + e.getMessage(), ButtonType.OK);
                    err.showAndWait();
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newSource -> {
            refreshTable();
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Source " + newSource.code() + " registered successfully!", ButtonType.OK);
            alert.showAndWait();
        });
    }
}
