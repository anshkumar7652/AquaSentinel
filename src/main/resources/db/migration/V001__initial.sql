-- V001__initial.sql
-- AquaSentinel baseline database schema

-- Schema migrations ledger
CREATE TABLE IF NOT EXISTS schema_migrations (
    version INTEGER PRIMARY KEY,
    checksum TEXT NOT NULL,
    applied_at TEXT NOT NULL
);

-- Users & authentication
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT UNIQUE NOT NULL COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    kdf_algorithm TEXT NOT NULL,
    kdf_iterations INTEGER NOT NULL,
    role TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1
);

-- Water sources
CREATE TABLE IF NOT EXISTS sources (
    id TEXT PRIMARY KEY,
    code TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    source_type TEXT NOT NULL,
    intended_use TEXT NOT NULL,
    location_text TEXT NOT NULL,
    latitude REAL,
    longitude REAL,
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1
);

-- Inspections
CREATE TABLE IF NOT EXISTS inspections (
    id TEXT PRIMARY KEY,
    source_id TEXT NOT NULL,
    assigned_user_id TEXT NOT NULL,
    due_date TEXT NOT NULL,
    status TEXT NOT NULL,
    no_sample_reason TEXT,
    notes TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY(source_id) REFERENCES sources(id),
    FOREIGN KEY(assigned_user_id) REFERENCES users(id)
);

-- Import batches
CREATE TABLE IF NOT EXISTS import_batches (
    id TEXT PRIMARY KEY,
    file_sha256 TEXT UNIQUE NOT NULL,
    filename TEXT NOT NULL,
    publisher TEXT NOT NULL,
    dataset_title TEXT NOT NULL,
    source_url TEXT NOT NULL,
    accessed_on TEXT NOT NULL,
    license_status TEXT NOT NULL,
    transformations TEXT NOT NULL,
    provenance_kind TEXT NOT NULL,
    imported_by TEXT NOT NULL,
    imported_at TEXT NOT NULL,
    row_count INTEGER NOT NULL,
    FOREIGN KEY(imported_by) REFERENCES users(id)
);

-- Samples
CREATE TABLE IF NOT EXISTS samples (
    id TEXT PRIMARY KEY,
    code TEXT UNIQUE NOT NULL,
    inspection_id TEXT NOT NULL,
    status TEXT NOT NULL,
    collected_at TEXT,
    received_at TEXT,
    collected_by TEXT,
    origin TEXT NOT NULL,
    import_batch_id TEXT,
    supersedes_sample_id TEXT UNIQUE,
    created_at TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY(inspection_id) REFERENCES inspections(id),
    FOREIGN KEY(collected_by) REFERENCES users(id),
    FOREIGN KEY(import_batch_id) REFERENCES import_batches(id),
    FOREIGN KEY(supersedes_sample_id) REFERENCES samples(id)
);

-- Sample event log / chain of custody
CREATE TABLE IF NOT EXISTS sample_events (
    id TEXT PRIMARY KEY,
    sample_id TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    note TEXT,
    FOREIGN KEY(sample_id) REFERENCES samples(id),
    FOREIGN KEY(actor_id) REFERENCES users(id)
);

-- Parameter definitions catalog
CREATE TABLE IF NOT EXISTS parameters (
    id TEXT PRIMARY KEY,
    code TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    canonical_unit TEXT NOT NULL,
    input_min_decimal TEXT,
    input_max_decimal TEXT
);

-- Analytical readings
CREATE TABLE IF NOT EXISTS readings (
    id TEXT PRIMARY KEY,
    sample_id TEXT NOT NULL,
    parameter_id TEXT NOT NULL,
    value_decimal TEXT NOT NULL,
    unit TEXT NOT NULL,
    entered_by TEXT NOT NULL,
    entered_at TEXT NOT NULL,
    provenance_kind TEXT NOT NULL,
    original_row INTEGER,
    UNIQUE(sample_id, parameter_id),
    FOREIGN KEY(sample_id) REFERENCES samples(id),
    FOREIGN KEY(parameter_id) REFERENCES parameters(id),
    FOREIGN KEY(entered_by) REFERENCES users(id)
);

-- Profile versions
CREATE TABLE IF NOT EXISTS profile_versions (
    id TEXT PRIMARY KEY,
    profile_key TEXT NOT NULL,
    version_number INTEGER NOT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL,
    intended_use TEXT NOT NULL,
    allowed_source_types_json TEXT NOT NULL,
    record_kind TEXT NOT NULL,
    provenance_kind TEXT NOT NULL,
    reference_text TEXT NOT NULL,
    reference_url TEXT,
    published_at TEXT,
    UNIQUE(profile_key, version_number)
);

-- Profile threshold rules
CREATE TABLE IF NOT EXISTS profile_rules (
    id TEXT PRIMARY KEY,
    profile_version_id TEXT NOT NULL,
    parameter_id TEXT NOT NULL,
    lower_decimal TEXT,
    upper_decimal TEXT,
    lower_inclusive INTEGER NOT NULL DEFAULT 1,
    upper_inclusive INTEGER NOT NULL DEFAULT 1,
    required INTEGER NOT NULL DEFAULT 1,
    unit TEXT NOT NULL,
    UNIQUE(profile_version_id, parameter_id),
    FOREIGN KEY(profile_version_id) REFERENCES profile_versions(id),
    FOREIGN KEY(parameter_id) REFERENCES parameters(id)
);

-- Evaluation results
CREATE TABLE IF NOT EXISTS evaluations (
    id TEXT PRIMARY KEY,
    sample_id TEXT NOT NULL,
    profile_version_id TEXT NOT NULL,
    outcome TEXT NOT NULL,
    evaluated_by TEXT NOT NULL,
    evaluated_at TEXT NOT NULL,
    input_digest TEXT NOT NULL,
    UNIQUE(sample_id, profile_version_id),
    FOREIGN KEY(sample_id) REFERENCES samples(id),
    FOREIGN KEY(profile_version_id) REFERENCES profile_versions(id),
    FOREIGN KEY(evaluated_by) REFERENCES users(id)
);

-- Evaluation rule results
CREATE TABLE IF NOT EXISTS rule_results (
    id TEXT PRIMARY KEY,
    evaluation_id TEXT NOT NULL,
    parameter_id TEXT NOT NULL,
    result TEXT NOT NULL,
    observed_decimal TEXT,
    observed_unit TEXT,
    lower_snapshot TEXT,
    upper_snapshot TEXT,
    lower_inclusive INTEGER NOT NULL,
    upper_inclusive INTEGER NOT NULL,
    required INTEGER NOT NULL,
    explanation TEXT NOT NULL,
    UNIQUE(evaluation_id, parameter_id),
    FOREIGN KEY(evaluation_id) REFERENCES evaluations(id),
    FOREIGN KEY(parameter_id) REFERENCES parameters(id)
);

-- Alerts
CREATE TABLE IF NOT EXISTS alerts (
    id TEXT PRIMARY KEY,
    evaluation_id TEXT UNIQUE NOT NULL,
    state TEXT NOT NULL,
    acknowledged_by TEXT,
    acknowledged_at TEXT,
    resolved_at TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY(evaluation_id) REFERENCES evaluations(id),
    FOREIGN KEY(acknowledged_by) REFERENCES users(id)
);

-- Corrective actions
CREATE TABLE IF NOT EXISTS corrective_actions (
    id TEXT PRIMARY KEY,
    alert_id TEXT NOT NULL,
    assigned_user_id TEXT NOT NULL,
    description TEXT NOT NULL,
    due_date TEXT NOT NULL,
    state TEXT NOT NULL,
    completion_notes TEXT,
    followup_evaluation_id TEXT,
    closed_at TEXT,
    created_at TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY(alert_id) REFERENCES alerts(id),
    FOREIGN KEY(assigned_user_id) REFERENCES users(id),
    FOREIGN KEY(followup_evaluation_id) REFERENCES evaluations(id)
);

-- Corrective action notes
CREATE TABLE IF NOT EXISTS action_notes (
    id TEXT PRIMARY KEY,
    action_id TEXT NOT NULL,
    author_id TEXT NOT NULL,
    note TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY(action_id) REFERENCES corrective_actions(id),
    FOREIGN KEY(author_id) REFERENCES users(id)
);

-- Historical observations (summary data imported from external sources)
CREATE TABLE IF NOT EXISTS historical_observations (
    id TEXT PRIMARY KEY,
    source_id TEXT NOT NULL,
    parameter_id TEXT NOT NULL,
    period_start TEXT NOT NULL,
    period_end TEXT NOT NULL,
    statistic TEXT NOT NULL,
    value_decimal TEXT NOT NULL,
    unit TEXT NOT NULL,
    import_batch_id TEXT NOT NULL,
    original_row INTEGER NOT NULL,
    UNIQUE(import_batch_id, original_row),
    FOREIGN KEY(source_id) REFERENCES sources(id),
    FOREIGN KEY(parameter_id) REFERENCES parameters(id),
    FOREIGN KEY(import_batch_id) REFERENCES import_batches(id)
);

-- Audit events
CREATE TABLE IF NOT EXISTS audit_events (
    id TEXT PRIMARY KEY,
    actor_id TEXT,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    before_json TEXT,
    after_json TEXT,
    FOREIGN KEY(actor_id) REFERENCES users(id)
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_samples_inspection_collected ON samples(inspection_id, collected_at);
CREATE INDEX IF NOT EXISTS idx_readings_param_sample ON readings(parameter_id, sample_id);
CREATE INDEX IF NOT EXISTS idx_inspections_source_due ON inspections(source_id, due_date);
CREATE INDEX IF NOT EXISTS idx_evaluations_sample_evaluated ON evaluations(sample_id, evaluated_at);
CREATE INDEX IF NOT EXISTS idx_alerts_state ON alerts(state);
CREATE INDEX IF NOT EXISTS idx_corrective_actions_state_due ON corrective_actions(state, due_date);
CREATE INDEX IF NOT EXISTS idx_historical_obs_lookup ON historical_observations(source_id, parameter_id, period_start);
CREATE INDEX IF NOT EXISTS idx_audit_events_occurred_at ON audit_events(occurred_at);
