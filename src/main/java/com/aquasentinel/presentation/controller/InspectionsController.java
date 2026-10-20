package com.aquasentinel.presentation.controller;

import com.aquasentinel.app.AppBootstrap;
import com.aquasentinel.application.dto.InspectionDto;
import com.aquasentinel.application.dto.SourceDto;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.InspectionStatus;
import com.aquasentinel.domain.model.UserAccount;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

public class InspectionsController {
    private static final Logger log = LoggerFactory.getLogger(InspectionsController.class);

    @FXML
    private TableView<InspectionDto> tableInspections;
    @FXML
    private TableColumn<InspectionDto, String> colSource;
    @FXML
    private TableColumn<InspectionDto, String> colOfficer;
    @FXML
    private TableColumn<InspectionDto, String> colDueDate;
    @FXML
    private TableColumn<InspectionDto, String> colStatus;
    @FXML
    private TableColumn<InspectionDto, String> colNotes;
    @FXML
    private Label lblInspectionCount;

    private AppBootstrap bootstrap;
    private Session session;
    private final ObservableList<InspectionDto> masterList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colSource.setCellValueFactory(c -> {
            String name = "Source " + c.getValue().sourceId();
            if (bootstrap != null) {
                var s = bootstrap.getSourceRepository().findById(c.getValue().sourceId());
                if (s.isPresent()) name = s.get().getName() + " (" + s.get().getCode() + ")";
            }
            return new SimpleStringProperty(name);
        });
        colOfficer.setCellValueFactory(c -> {
            String officer = "User " + c.getValue().assignedUserId();
            if (bootstrap != null) {
                var u = bootstrap.getUserRepository().findById(c.getValue().assignedUserId());
                if (u.isPresent()) officer = u.get().getUsername();
            }
            return new SimpleStringProperty(officer);
        });
        colDueDate.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().dueDate() != null ? c.getValue().dueDate().toString() : "Not scheduled"
        ));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status().name()));
        colNotes.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().notes() != null ? c.getValue().notes() : ""
        ));

        tableInspections.setItems(masterList);
    }

    public void init(AppBootstrap bootstrap, Session session) {
        this.bootstrap = bootstrap;
        this.session = session;
        refreshTable();
    }

    public void refreshTable() {
        if (bootstrap == null) return;
        List<InspectionDto> list = bootstrap.getInspectionRepository().findAll(0, 1000)
                .stream().map(InspectionDto::from).toList();
        masterList.setAll(list);
        if (lblInspectionCount != null) {
            lblInspectionCount.setText("Total inspections: " + list.size());
        }
    }

    @FXML
    public void handleNewInspection() {
        if (bootstrap == null || session == null) return;

        Dialog<InspectionDto> dialog = new Dialog<>();
        dialog.setTitle("Schedule Field Inspection");
        dialog.setHeaderText("Create a new monitoring inspection tour for a water source.");

        ButtonType btnSave = new ButtonType("Schedule Inspection", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSave, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 10));

        List<SourceDto> sources = bootstrap.getSourceService().listSources(session, 0, 1000);
        ComboBox<SourceDto> cmbSource = new ComboBox<>(FXCollections.observableArrayList(sources));
        cmbSource.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(SourceDto s) {
                return s == null ? "" : s.code() + " — " + s.name();
            }
            @Override
            public SourceDto fromString(String string) { return null; }
        });
        if (!sources.isEmpty()) cmbSource.getSelectionModel().selectFirst();

        List<UserAccount> users = bootstrap.getUserRepository().findAll();
        ComboBox<UserAccount> cmbUser = new ComboBox<>(FXCollections.observableArrayList(users));
        cmbUser.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(UserAccount u) {
                return u == null ? "" : u.getUsername() + " (" + u.getRole() + ")";
            }
            @Override
            public UserAccount fromString(String string) { return null; }
        });
        if (!users.isEmpty()) cmbUser.getSelectionModel().selectFirst();

        DatePicker dpDate = new DatePicker(LocalDate.now().plusDays(2));
        TextField txtNotes = new TextField("Routine parameter sampling");

        grid.add(new Label("Target Source:"), 0, 0);
        grid.add(cmbSource, 1, 0);
        grid.add(new Label("Assigned Officer:"), 0, 1);
        grid.add(cmbUser, 1, 1);
        grid.add(new Label("Scheduled Date:"), 0, 2);
        grid.add(dpDate, 1, 2);
        grid.add(new Label("Inspection Notes:"), 0, 3);
        grid.add(txtNotes, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnSave) {
                try {
                    SourceDto selSrc = cmbSource.getValue();
                    UserAccount selUser = cmbUser.getValue();
                    if (selSrc == null || selUser == null || dpDate.getValue() == null) {
                        throw new IllegalArgumentException("All fields are required.");
                    }
                    return bootstrap.getInspectionService().scheduleInspection(
                            session, selSrc.id(), selUser.getId(), dpDate.getValue(), txtNotes.getText().trim()
                    );
                } catch (Exception e) {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Scheduling failed: " + e.getMessage(), ButtonType.OK);
                    err.showAndWait();
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(res -> {
            refreshTable();
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Inspection tour scheduled successfully!", ButtonType.OK);
            alert.showAndWait();
        });
    }
}
