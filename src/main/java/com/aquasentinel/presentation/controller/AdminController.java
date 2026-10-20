package com.aquasentinel.presentation.controller;

import com.aquasentinel.app.AppBootstrap;
import com.aquasentinel.application.dto.UserDto;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.Role;
import com.aquasentinel.domain.model.ProfileVersion;
import com.aquasentinel.domain.model.ThresholdRule;
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

import java.util.List;

public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @FXML
    private TableView<UserAccount> tableUsers;
    @FXML
    private TableColumn<UserAccount, String> colUserId;
    @FXML
    private TableColumn<UserAccount, String> colUsername;
    @FXML
    private TableColumn<UserAccount, String> colRole;
    @FXML
    private TableColumn<UserAccount, String> colActive;

    @FXML
    private TableView<ProfileVersion> tableProfiles;
    @FXML
    private TableColumn<ProfileVersion, String> colProfileKey;
    @FXML
    private TableColumn<ProfileVersion, String> colProfileVer;
    @FXML
    private TableColumn<ProfileVersion, String> colProfileName;
    @FXML
    private TableColumn<ProfileVersion, String> colProfileStatus;

    @FXML
    private TableView<ThresholdRule> tableRules;
    @FXML
    private TableColumn<ThresholdRule, String> colRuleParam;
    @FXML
    private TableColumn<ThresholdRule, String> colRuleBounds;
    @FXML
    private TableColumn<ThresholdRule, String> colRuleRequired;

    @FXML
    private Label lblDbPath;

    private AppBootstrap bootstrap;
    private Session session;

    private final ObservableList<UserAccount> userList = FXCollections.observableArrayList();
    private final ObservableList<ProfileVersion> profileList = FXCollections.observableArrayList();
    private final ObservableList<ThresholdRule> ruleList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Users Table
        colUserId.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getId().toString().substring(0, 8)));
        colUsername.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        colRole.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRole().name()));
        colActive.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActive() ? "Active" : "Disabled"));
        tableUsers.setItems(userList);

        // Profiles Table
        colProfileKey.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProfileKey()));
        colProfileVer.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getVersionNumber())));
        colProfileName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colProfileStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().name()));
        tableProfiles.setItems(profileList);

        tableProfiles.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadRulesForProfile(newVal);
            }
        });

        // Rules Table
        colRuleParam.setCellValueFactory(c -> {
            String pName = "Param " + c.getValue().getParameterId();
            try {
                if (bootstrap != null) {
                    var p = bootstrap.getParameterRepository().findById(c.getValue().getParameterId());
                    if (p.isPresent()) pName = p.get().getCode() + " (" + p.get().getName() + ")";
                }
            } catch (Exception ignored) {}
            return new SimpleStringProperty(pName);
        });
        colRuleBounds.setCellValueFactory(c -> {
            String lower = c.getValue().getLowerDecimal() != null ? ">= " + c.getValue().getLowerDecimal() : "";
            String upper = c.getValue().getUpperDecimal() != null ? "<= " + c.getValue().getUpperDecimal() : "";
            String res = (lower + " " + upper).trim() + " " + (c.getValue().getUnit() != null ? c.getValue().getUnit() : "");
            return new SimpleStringProperty(res.trim());
        });
        colRuleRequired.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isRequired() ? "Yes (Mandatory)" : "Optional"));
        tableRules.setItems(ruleList);
    }

    public void init(AppBootstrap bootstrap, Session session) {
        this.bootstrap = bootstrap;
        this.session = session;

        if (lblDbPath != null) {
            lblDbPath.setText(bootstrap.getContext().databasePath().toString());
        }

        refreshData();
    }

    public void refreshData() {
        if (bootstrap == null) return;
        userList.setAll(bootstrap.getUserRepository().findAll());
        List<ProfileVersion> profiles = bootstrap.getProfileRepository().findAll();
        profileList.setAll(profiles);
        if (!profiles.isEmpty()) {
            tableProfiles.getSelectionModel().selectFirst();
            loadRulesForProfile(profiles.getFirst());
        }
    }

    private void loadRulesForProfile(ProfileVersion p) {
        if (bootstrap == null || p == null) return;
        ruleList.setAll(bootstrap.getProfileRepository().findRulesByProfileVersionId(p.getId()));
    }

    @FXML
    public void handleAddUser() {
        Dialog<UserDto> dialog = new Dialog<>();
        dialog.setTitle("Create System User");
        dialog.setHeaderText("Create an authorized user with role-based permissions.");

        ButtonType btnSave = new ButtonType("Create User", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSave, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField txtUsername = new TextField();
        PasswordField txtPassword = new PasswordField();
        ComboBox<Role> cmbRole = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        cmbRole.getSelectionModel().select(Role.ANALYST);

        grid.add(new Label("Username:"), 0, 0);
        grid.add(txtUsername, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(txtPassword, 1, 1);
        grid.add(new Label("Role:"), 0, 2);
        grid.add(cmbRole, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == btnSave) {
                try {
                    return bootstrap.getUserService().createUser(
                            session, txtUsername.getText().trim(), txtPassword.getText().toCharArray(), cmbRole.getValue()
                    );
                } catch (Exception e) {
                    Alert err = new Alert(Alert.AlertType.ERROR, "User creation failed: " + e.getMessage(), ButtonType.OK);
                    err.showAndWait();
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(u -> {
            refreshData();
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "User " + u.username() + " created successfully!", ButtonType.OK);
            alert.showAndWait();
        });
    }

    @FXML
    public void handleReloadDemoData() {
        try {
            bootstrap.seedDemoData(session);
            refreshData();
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Educational demo dataset successfully re-seeded!", ButtonType.OK);
            alert.showAndWait();
        } catch (Exception e) {
            Alert err = new Alert(Alert.AlertType.ERROR, "Demo reload error: " + e.getMessage(), ButtonType.OK);
            err.showAndWait();
        }
    }
}
