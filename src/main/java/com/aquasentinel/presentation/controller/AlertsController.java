package com.aquasentinel.presentation.controller;

import com.aquasentinel.app.AppBootstrap;
import com.aquasentinel.application.dto.ActionDto;
import com.aquasentinel.application.dto.AlertDto;
import com.aquasentinel.application.port.EvaluationRepository;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.ActionState;
import com.aquasentinel.domain.enums.AlertState;
import com.aquasentinel.domain.enums.EvaluationOutcome;
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
import java.util.UUID;

public class AlertsController {
    private static final Logger log = LoggerFactory.getLogger(AlertsController.class);

    @FXML
    private TableView<AlertDto> tableAlerts;
    @FXML
    private TableColumn<AlertDto, String> colAlertId;
    @FXML
    private TableColumn<AlertDto, String> colAlertSource;
    @FXML
    private TableColumn<AlertDto, String> colAlertState;
    @FXML
    private TableColumn<AlertDto, String> colAlertCreated;

    @FXML
    private TableView<ActionDto> tableActions;
    @FXML
    private TableColumn<ActionDto, String> colActionDesc;
    @FXML
    private TableColumn<ActionDto, String> colActionAssignee;
    @FXML
    private TableColumn<ActionDto, String> colActionDue;
    @FXML
    private TableColumn<ActionDto, String> colActionState;

    @FXML
    private Label lblAlertStatusSummary;

    private AppBootstrap bootstrap;
    private Session session;

    private final ObservableList<AlertDto> alertList = FXCollections.observableArrayList();
    private final ObservableList<ActionDto> actionList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colAlertId.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().id().toString().substring(0, 8)));
        colAlertSource.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().sourceName()));
        colAlertState.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().state().name()));
        colAlertCreated.setCellValueFactory(c -> new SimpleStringProperty("Sample: " + c.getValue().sampleCode()));

        tableAlerts.setItems(alertList);

        tableAlerts.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadActionsForAlert(newVal.id());
            } else {
                actionList.clear();
            }
        });

        colActionDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().description()));
        colActionAssignee.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().assignedUsername() != null ? c.getValue().assignedUsername() : "Unassigned"
        ));
        colActionDue.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().dueDate() != null ? c.getValue().dueDate().toString() : ""
        ));
        colActionState.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().state().name()));

        tableActions.setItems(actionList);
    }

    public void init(AppBootstrap bootstrap, Session session) {
        this.bootstrap = bootstrap;
        this.session = session;
        refreshAlerts();
    }

    public void refreshAlerts() {
        if (bootstrap == null) return;
        List<AlertDto> list = bootstrap.getActionService().getAlerts(null);
        alertList.setAll(list);
        if (!list.isEmpty()) {
            tableAlerts.getSelectionModel().selectFirst();
            loadActionsForAlert(list.getFirst().id());
        }
        long openCount = list.stream().filter(a -> a.state() != AlertState.RESOLVED).count();
        lblAlertStatusSummary.setText("Total alerts: " + list.size() + " (" + openCount + " unresolved)");
    }

    private void loadActionsForAlert(UUID alertId) {
        if (bootstrap == null) return;
        List<ActionDto> actions = bootstrap.getActionService().getActionsByAlert(alertId);
        actionList.setAll(actions);
    }

    @FXML
    public void handleAcknowledgeAlert() {
        AlertDto selected = tableAlerts.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            bootstrap.getActionService().acknowledgeAlert(session, selected.id());
            refreshAlerts();
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Alert marked ACKNOWLEDGED.", ButtonType.OK);
            alert.showAndWait();
        } catch (Exception e) {
            Alert err = new Alert(Alert.AlertType.ERROR, "Failed to acknowledge: " + e.getMessage(), ButtonType.OK);
            err.showAndWait();
        }
    }

    @FXML
    public void handleCreateAction() {
        AlertDto selected = tableAlerts.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please select an alert from the table first.", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        Dialog<ActionDto> dialog = new Dialog<>();
        dialog.setTitle("Create Corrective Action");
        dialog.setHeaderText("Assign remediation tasks for Alert " + selected.id().toString().substring(0, 8));

        ButtonType btnSave = new ButtonType("Assign Action", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSave, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField txtDesc = new TextField("Deploy emergency aeration and schedule verification retest");
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

        DatePicker dpDue = new DatePicker(LocalDate.now().plusDays(3));

        grid.add(new Label("Remediation Description:"), 0, 0);
        grid.add(txtDesc, 1, 0);
        grid.add(new Label("Assignee:"), 0, 1);
        grid.add(cmbUser, 1, 1);
        grid.add(new Label("Due Date:"), 0, 2);
        grid.add(dpDue, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == btnSave) {
                try {
                    return bootstrap.getActionService().createAction(
                            session, selected.id(), cmbUser.getValue().getId(), txtDesc.getText().trim(), dpDue.getValue()
                    );
                } catch (Exception e) {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Creation failed: " + e.getMessage(), ButtonType.OK);
                    err.showAndWait();
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(res -> {
            loadActionsForAlert(selected.id());
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Corrective action assigned successfully!", ButtonType.OK);
            alert.showAndWait();
        });
    }

    @FXML
    public void handleAdvanceActionState() {
        ActionDto selected = tableActions.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            ActionState nextState = selected.state() == ActionState.OPEN ? ActionState.IN_PROGRESS : ActionState.AWAITING_RETEST;
            bootstrap.getActionService().updateState(session, selected.id(), nextState);
            loadActionsForAlert(selected.alertId());
        } catch (Exception e) {
            Alert err = new Alert(Alert.AlertType.ERROR, "Update failed: " + e.getMessage(), ButtonType.OK);
            err.showAndWait();
        }
    }

    @FXML
    public void handleCloseAction() {
        ActionDto selected = tableActions.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        // Find passing follow-up evaluations
        List<EvaluationRepository.StoredEvaluation> allEvals = bootstrap.getEvaluationRepository().findAll();
        List<EvaluationRepository.StoredEvaluation> passingEvals = allEvals.stream()
                .filter(ev -> ev.outcome() == EvaluationOutcome.WITHIN_PROFILE)
                .toList();

        if (passingEvals.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "No passing (WITHIN_PROFILE) follow-up evaluations available to verify closure.", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Verify & Close Corrective Action");
        dialog.setHeaderText("Action closure requires mandatory verification against a compliant retest evaluation.");

        ButtonType btnClose = new ButtonType("Verify & Close Action", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnClose, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField txtNotes = new TextField("Remediation completed; follow-up water sample meets profile standards.");
        ComboBox<EvaluationRepository.StoredEvaluation> cmbEval = new ComboBox<>(FXCollections.observableArrayList(passingEvals));
        cmbEval.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(EvaluationRepository.StoredEvaluation ev) {
                return ev == null ? "" : "Eval: " + ev.id().toString().substring(0, 8) + " — " + ev.outcome();
            }
            @Override
            public EvaluationRepository.StoredEvaluation fromString(String string) { return null; }
        });
        cmbEval.getSelectionModel().selectFirst();

        grid.add(new Label("Completion Notes:"), 0, 0);
        grid.add(txtNotes, 1, 0);
        grid.add(new Label("Passing Retest Evaluation:"), 0, 1);
        grid.add(cmbEval, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == btnClose) {
                try {
                    bootstrap.getActionService().closeAction(session, selected.id(), txtNotes.getText().trim(), cmbEval.getValue().id());
                    return true;
                } catch (Exception e) {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Closure rejected: " + e.getMessage(), ButtonType.OK);
                    err.showAndWait();
                    return false;
                }
            }
            return false;
        });

        dialog.showAndWait().ifPresent(ok -> {
            if (ok) {
                refreshAlerts();
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Action verified and closed successfully!", ButtonType.OK);
                alert.showAndWait();
            }
        });
    }
}
