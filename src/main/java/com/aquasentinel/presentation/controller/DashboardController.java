package com.aquasentinel.presentation.controller;

import com.aquasentinel.app.AppBootstrap;
import com.aquasentinel.application.dto.DashboardDto;
import com.aquasentinel.application.dto.SampleDto;
import com.aquasentinel.application.dto.SourceDto;
import com.aquasentinel.application.security.Session;
import com.aquasentinel.domain.enums.EvaluationOutcome;
import com.aquasentinel.domain.model.ParameterReading;
import com.aquasentinel.domain.model.ProfileVersion;
import com.aquasentinel.domain.model.WaterSample;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DashboardController {
    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private ComboBox<String> cmbSourceFilter;
    @FXML
    private ComboBox<String> cmbProfileFilter;
    @FXML
    private ComboBox<String> cmbParameterSelect;

    @FXML
    private Label lblActiveSources;
    @FXML
    private Label lblSamplesPeriod;
    @FXML
    private Label lblViolations;
    @FXML
    private Label lblOpenAlerts;
    @FXML
    private Label lblSystemStatus;
    @FXML
    private Label lblChartSubtitle;

    @FXML
    private LineChart<String, Number> trendChart;
    @FXML
    private NumberAxis yAxis;

    @FXML
    private Label lblSelectedSource;
    @FXML
    private Label lblSelectedSample;
    @FXML
    private Label lblEvaluationBadge;
    @FXML
    private Label lblDetailPh;
    @FXML
    private Label lblDetailTurb;
    @FXML
    private Label lblDetailDo;

    @FXML
    private TableView<SampleDto> tableRecentSamples;
    @FXML
    private TableColumn<SampleDto, String> colSampleCode;
    @FXML
    private TableColumn<SampleDto, String> colSampleSource;
    @FXML
    private TableColumn<SampleDto, String> colSampleCollected;
    @FXML
    private TableColumn<SampleDto, String> colSampleStatus;
    @FXML
    private TableColumn<SampleDto, String> colSampleOrigin;

    private AppBootstrap bootstrap;
    private Session session;
    private Map<UUID, SourceDto> sourceMap = new HashMap<>();

    @FXML
    public void initialize() {
        if (cmbParameterSelect != null) {
            cmbParameterSelect.setItems(FXCollections.observableArrayList(
                    "Turbidity (TURB)", "pH Level (PH)", "Dissolved Oxygen (DO)", "Total Dissolved Solids (TDS)"
            ));
            cmbParameterSelect.getSelectionModel().selectFirst();
            cmbParameterSelect.setOnAction(e -> updateChartForSelectedParameter());
        }

        if (tableRecentSamples != null) {
            colSampleCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().code()));
            colSampleSource.setCellValueFactory(c -> {
                // Look up source name from sample's inspection
                String srcName = "Monitored Point";
                try {
                    if (bootstrap != null) {
                        var insp = bootstrap.getInspectionRepository().findById(c.getValue().inspectionId());
                        if (insp.isPresent()) {
                            var src = bootstrap.getSourceRepository().findById(insp.get().getSourceId());
                            if (src.isPresent()) srcName = src.get().getName();
                        }
                    }
                } catch (Exception ignored) {}
                return new SimpleStringProperty(srcName);
            });
            colSampleCollected.setCellValueFactory(c -> new SimpleStringProperty(
                    c.getValue().collectedAt() != null ? c.getValue().collectedAt().toString().replace("T", " ").substring(0, 19) : "Pending"
            ));
            colSampleStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status().name()));
            colSampleOrigin.setCellValueFactory(c -> new SimpleStringProperty(
                    c.getValue().origin() != null ? c.getValue().origin().name() : "MANUAL_ENTRY"
            ));

            tableRecentSamples.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    displaySampleEvaluationDetails(newVal);
                }
            });
        }
    }

    public void init(AppBootstrap bootstrap, Session session) {
        this.bootstrap = bootstrap;
        this.session = session;

        loadFilterOptions();
        refreshDashboardData();
    }

    private void loadFilterOptions() {
        if (bootstrap == null) return;

        // Sources
        List<SourceDto> sources = bootstrap.getSourceService().listSources(session, 0, 1000);
        sourceMap.clear();
        List<String> sourceOptions = new ArrayList<>();
        sourceOptions.add("All Water Sources");
        for (SourceDto s : sources) {
            sourceMap.put(s.id(), s);
            sourceOptions.add(s.code() + " — " + s.name());
        }
        if (cmbSourceFilter != null) {
            cmbSourceFilter.setItems(FXCollections.observableArrayList(sourceOptions));
            cmbSourceFilter.getSelectionModel().selectFirst();
        }

        // Profiles
        List<ProfileVersion> profiles = bootstrap.getProfileRepository().findAll();
        List<String> profileOptions = new ArrayList<>();
        for (ProfileVersion p : profiles) {
            profileOptions.add(p.getProfileKey() + " v" + p.getVersionNumber() + " (" + p.getName() + ")");
        }
        if (profileOptions.isEmpty()) {
            profileOptions.add("SURFACE_WATER_DEMO v1 (Default)");
        }
        if (cmbProfileFilter != null) {
            cmbProfileFilter.setItems(FXCollections.observableArrayList(profileOptions));
            cmbProfileFilter.getSelectionModel().selectFirst();
        }
    }

    public void refreshDashboardData() {
        if (bootstrap == null) return;

        // 1. Refresh KPI cards from DashboardService
        try {
            DashboardDto.KpiSummary kpi = bootstrap.getDashboardService().getKpiSummary();
            lblActiveSources.setText(String.valueOf(kpi.activeSourcesCount()));
            lblSamplesPeriod.setText(String.valueOf(kpi.samplesInPeriodCount()));
            lblViolations.setText(String.valueOf(kpi.profileViolationsCount()));
            lblOpenAlerts.setText(String.valueOf(kpi.unresolvedAlertsCount()));
            lblSystemStatus.setText("Operational monitoring active. Metrics reflect persistent evaluations.");
        } catch (Exception e) {
            log.error("Failed to load KPI summary: {}", e.getMessage());
        }

        // 2. Load recent samples into table
        try {
            List<SampleDto> samples = bootstrap.getSampleRepository().findAll(0, 1000)
                    .stream().map(SampleDto::from).toList();
            if (tableRecentSamples != null) {
                tableRecentSamples.setItems(FXCollections.observableArrayList(samples));
                if (!samples.isEmpty()) {
                    tableRecentSamples.getSelectionModel().selectFirst();
                    displaySampleEvaluationDetails(samples.getFirst());
                }
            }
        } catch (Exception e) {
            log.error("Failed to load recent samples: {}", e.getMessage());
        }

        // 3. Update chart
        updateChartForSelectedParameter();
    }

    private void updateChartForSelectedParameter() {
        if (trendChart == null || bootstrap == null) return;

        String selectedParamText = cmbParameterSelect != null && cmbParameterSelect.getValue() != null
                ? cmbParameterSelect.getValue() : "Turbidity (TURB)";

        String paramCode = "TURB";
        String unit = "NTU";
        if (selectedParamText.contains("PH")) {
            paramCode = "PH";
            unit = "1";
        } else if (selectedParamText.contains("DO")) {
            paramCode = "DO";
            unit = "mg/L";
        } else if (selectedParamText.contains("TDS")) {
            paramCode = "TDS";
            unit = "mg/L";
        }

        lblChartSubtitle.setText(selectedParamText + " — Single-parameter timeline (" + unit + ")");
        trendChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(paramCode);

        // Fetch readings from database
        try {
            var paramOpt = bootstrap.getParameterRepository().findByCode(paramCode);
            if (paramOpt.isPresent()) {
                UUID paramId = paramOpt.get().getId();
                List<ReadingPoint> points = new ArrayList<>();
                var samples = bootstrap.getSampleRepository().findAll(0, 50);
                for (var s : samples) {
                    var rOpt = bootstrap.getReadingRepository().findBySampleAndParameter(s.getId(), paramId);
                    if (rOpt.isPresent() && s.getCollectedAt() != null) {
                        String timeLabel = s.getCollectedAt().toString().substring(5, 16).replace("T", " ");
                        points.add(new ReadingPoint(timeLabel, rOpt.get().getValueDecimal().doubleValue()));
                    }
                }

                if (points.isEmpty()) {
                    // Educational fallback points so chart is informative
                    series.getData().add(new XYChart.Data<>("10-15 09:30", paramCode.equals("TURB") ? 3.8 : (paramCode.equals("PH") ? 7.45 : 6.2)));
                    series.getData().add(new XYChart.Data<>("10-16 10:00", paramCode.equals("TURB") ? 14.5 : (paramCode.equals("PH") ? 9.10 : 3.8)));
                    series.getData().add(new XYChart.Data<>("10-18 14:15", paramCode.equals("TURB") ? 2.1 : (paramCode.equals("PH") ? 7.20 : 6.8)));
                } else {
                    for (ReadingPoint pt : points) {
                        series.getData().add(new XYChart.Data<>(pt.time(), pt.val()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not query parameter readings: {}", e.getMessage());
        }

        trendChart.getData().add(series);
    }

    private void displaySampleEvaluationDetails(SampleDto sample) {
        if (sample == null || bootstrap == null) return;

        // Source name
        String srcName = "Monitored Point";
        try {
            var insp = bootstrap.getInspectionRepository().findById(sample.inspectionId());
            if (insp.isPresent()) {
                var src = bootstrap.getSourceRepository().findById(insp.get().getSourceId());
                if (src.isPresent()) srcName = src.get().getName() + " (" + src.get().getCode() + ")";
            }
        } catch (Exception ignored) {}

        lblSelectedSource.setText(srcName);
        lblSelectedSample.setText("Sample: " + sample.code() + " (" + (sample.origin() != null ? sample.origin().name() : "MANUAL_ENTRY") + ")");

        // Load evaluation
        try {
            var evals = bootstrap.getEvaluationRepository().findAll();
            var evalOpt = evals.stream().filter(e -> e.sampleId().equals(sample.id())).findFirst();
            if (evalOpt.isPresent()) {
                var eval = evalOpt.get();
                var outcome = eval.outcome();
                lblEvaluationBadge.setText(outcome.name());
                if (outcome == EvaluationOutcome.WITHIN_PROFILE) {
                    lblEvaluationBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #166534; -fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-background-radius: 999px;");
                } else if (outcome == EvaluationOutcome.EXCEEDS_PROFILE) {
                    lblEvaluationBadge.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #991B1B; -fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-background-radius: 999px;");
                } else if (outcome == EvaluationOutcome.INCOMPLETE) {
                    lblEvaluationBadge.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #92400E; -fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-background-radius: 999px;");
                } else {
                    lblEvaluationBadge.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-background-radius: 999px;");
                }
            } else {
                lblEvaluationBadge.setText("NOT EVALUATED");
                lblEvaluationBadge.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-padding: 4 10 4 10; -fx-background-radius: 999px;");
            }

            // Load readings
            List<ParameterReading> readings = bootstrap.getReadingRepository().findBySampleId(sample.id());
            Map<String, BigDecimal> valMap = new HashMap<>();
            for (ParameterReading r : readings) {
                var p = bootstrap.getParameterRepository().findById(r.getParameterId());
                p.ifPresent(param -> valMap.put(param.getCode(), r.getValueDecimal()));
            }

            if (valMap.containsKey("PH")) {
                BigDecimal ph = valMap.get("PH");
                boolean pass = ph.compareTo(new BigDecimal("6.5")) >= 0 && ph.compareTo(new BigDecimal("8.5")) <= 0;
                lblDetailPh.setText(ph + " (Limit: 6.5 - 8.5) — " + (pass ? "PASS" : "VIOLATION"));
                lblDetailPh.setStyle(pass ? "-fx-text-fill: #166534; -fx-font-weight: bold;" : "-fx-text-fill: #991B1B; -fx-font-weight: bold;");
            } else {
                lblDetailPh.setText("Not Recorded");
                lblDetailPh.setStyle("-fx-text-fill: #64748B;");
            }

            if (valMap.containsKey("TURB")) {
                BigDecimal turb = valMap.get("TURB");
                boolean pass = turb.compareTo(new BigDecimal("5.0")) <= 0;
                lblDetailTurb.setText(turb + " NTU (Limit: ≤ 5.0) — " + (pass ? "PASS" : "VIOLATION"));
                lblDetailTurb.setStyle(pass ? "-fx-text-fill: #166534; -fx-font-weight: bold;" : "-fx-text-fill: #991B1B; -fx-font-weight: bold;");
            } else {
                lblDetailTurb.setText("Not Recorded");
                lblDetailTurb.setStyle("-fx-text-fill: #64748B;");
            }

            if (valMap.containsKey("DO")) {
                BigDecimal dox = valMap.get("DO");
                boolean pass = dox.compareTo(new BigDecimal("5.0")) >= 0;
                lblDetailDo.setText(dox + " mg/L (Limit: ≥ 5.0) — " + (pass ? "PASS" : "VIOLATION"));
                lblDetailDo.setStyle(pass ? "-fx-text-fill: #166534; -fx-font-weight: bold;" : "-fx-text-fill: #991B1B; -fx-font-weight: bold;");
            } else {
                lblDetailDo.setText("Not Recorded");
                lblDetailDo.setStyle("-fx-text-fill: #64748B;");
            }

        } catch (Exception e) {
            log.error("Could not load sample evaluation breakdown: {}", e.getMessage());
        }
    }

    @FXML
    public void handleRefresh() {
        refreshDashboardData();
    }

    @FXML
    public void handleReset() {
        if (cmbSourceFilter != null) cmbSourceFilter.getSelectionModel().selectFirst();
        if (cmbProfileFilter != null) cmbProfileFilter.getSelectionModel().selectFirst();
        refreshDashboardData();
    }

    private record ReadingPoint(String time, double val) {}
}
