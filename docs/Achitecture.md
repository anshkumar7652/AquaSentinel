# AquaSentinel — Architecture and Implementation Specification

Version: 1.0 | Date: 2026-10-20 | Companion: `Prd.md`

Filename intentionally matches the requested `Achitecture.md`. This document is the implementation contract for Antigravity. In a conflict, the PRD owns behaviour and this document owns mechanisms; surface unresolved conflicts rather than silently changing scope.

## 1. Architectural decisions

- Single-process, offline-first Java 21 desktop application.
- JavaFX 21-compatible release with FXML/CSS; no web server/frontend.
- Maven wrapper; non-modular Maven project initially to reduce reflective-access setup. Use a verified jlink-compatible packaging path only if intentionally adopting modules later.
- SQLite via Xerial JDBC; explicit repositories and prepared SQL, no ORM.
- Apache Commons CSV for RFC-style CSV handling; Jackson for JSON metadata; Apache PDFBox for PDF output; SLF4J/Logback for logging; JUnit 5, Mockito where useful, JaCoCo and Checkstyle for quality.
- JavaFX charts and controls; Java standard cryptography for password derivation, random salts and file hashes.
- Pick current maintained versions compatible with Java 21 at bootstrap, pin exact versions in `pom.xml`, commit Maven wrapper verification configuration, and document the selected versions. Do not invent artifact coordinates or use `LATEST`.
- No dependency is needed for ML, maps, blockchain, remote API ingestion, or distributed authentication.

## 2. Dependency direction

```text
FXML/CSS → Controller → Application service → Domain model / evaluator
                              ↓
                       Repository interfaces
                              ↑
                       JDBC implementations → SQLite
                              ↓
                  Import/report/file adapters
```

Domain contains no JavaFX or JDBC imports. Controllers never execute SQL. Services own authorization, transactions, state transitions, and audit orchestration. Repositories do persistence only. Charts consume immutable DTOs. `AppBootstrap` wires concrete instances using constructor injection; avoid service locators and global mutable sessions.

## 3. Suggested repository structure

```text
aquasentinel/
├── Prd.md
├── Achitecture.md
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/
├── README.md
├── LICENSE
├── .gitignore
├── .github/workflows/ci.yml
├── docs/{data-sources.md,data-dictionary.md,demo.md,decisions.md}
├── data/{README.md,templates/,demo/,adapters/}
└── src/
    ├── main/java/com/aquasentinel/
    │   ├── app/{AquaSentinelApp,AppBootstrap,AppContext}.java
    │   ├── domain/{model,enums,evaluation,exception}/
    │   ├── application/{service,dto,security,port}/
    │   ├── infrastructure/{db,repository,csv,pdf,security,logging}/
    │   └── presentation/{controller,component,navigation,viewmodel}/
    ├── main/resources/
    │   ├── fxml/
    │   ├── css/
    │   ├── fonts/
    │   ├── db/migration/{V001__initial.sql,...}
    │   └── logback.xml
    └── test/{java/com/aquasentinel/,resources/fixtures/}
```

Runtime data lives outside the repository: `%LOCALAPPDATA%/AquaSentinel` on Windows, a platform-appropriate user data directory elsewhere. Allow an explicit `--data-dir` override for tests/demo. Never overwrite a user's database during startup. Bundled fixtures are copied only through an explicit demo setup operation.

## 4. Domain design

Use composition rather than contrived inheritance. A `WaterSource` has a `SourceType`; a `UserAccount` has a `Role`. Do not subclass users or sources just to satisfy an OOP checklist. Meaningful polymorphism lives in evaluators, repository ports, import adapters, and report exporters.

| Model | Responsibility |
|---|---|
| WaterSource | Code, type, intended use, location, archive status |
| Inspection | Assignment, schedule, source, state, completion |
| WaterSample | Immutable identity, collection/receipt metadata, lifecycle, revision linkage |
| ParameterDefinition | Stable code, canonical unit, validation metadata |
| ParameterReading | Exact decimal observation and provenance |
| ProfileVersion | Published immutable context and rules |
| ThresholdRule | Required flag, units, inclusive/exclusive optional bounds |
| Evaluation | Reproducible outcome linked to sample/profile version |
| RuleResult | PASS/FAIL/MISSING and observed/bound snapshots |
| Alert | One violation alert per evaluation |
| CorrectiveAction | Assigned workflow and verified follow-up link |
| ImportBatch | Source file hash, provenance and commit state |
| HistoricalObservation | External period summary, separate from sample workflow |
| AuditEvent | Actor, mutation, timestamp and safe metadata |

Use Java records for immutable DTOs/value objects. Use `BigDecimal` for measured values and bounds, `Instant` for operational events, `LocalDate` for due dates, `UUID`/TEXT IDs, and enums with persisted stable names. Use an injected `Clock` for tests. Display local time but persist event times in UTC ISO-8601. Use optimistic `version` fields on mutable rows; stale updates fail explicitly.

## 5. Database schema contract

All tables use TEXT UUID primary keys unless stated otherwise. Foreign keys are enabled on every connection. All business mutations and dependent audit events run in a single transaction. References default to restrictive deletion; archive business records instead.

| Table | Required columns and constraints |
|---|---|
| schema_migrations | version INTEGER PK, checksum, applied_at |
| users | id, username UNIQUE COLLATE NOCASE, password_hash, salt, kdf_algorithm, kdf_iterations, role, active, created_at, version |
| sources | id, code UNIQUE, name, source_type, intended_use, location_text, latitude NULL, longitude NULL, active, created_at, version |
| inspections | id, source_id FK, assigned_user_id FK, due_date, status, no_sample_reason NULL, notes, created_at, version |
| samples | id, code UNIQUE, inspection_id FK, status, collected_at NULL, received_at NULL, collected_by FK NULL, origin, import_batch_id FK NULL, supersedes_sample_id FK UNIQUE NULL, created_at, version |
| sample_events | id, sample_id FK, actor_id FK, event_type, occurred_at, note |
| parameters | id, code UNIQUE, name, canonical_unit, input_min_decimal NULL, input_max_decimal NULL |
| readings | id, sample_id FK, parameter_id FK, value_decimal TEXT, unit, entered_by FK, entered_at, provenance_kind, original_row NULL, UNIQUE(sample_id,parameter_id) |
| profile_versions | id, profile_key, version_number INTEGER, name, status, intended_use, allowed_source_types_json, record_kind, provenance_kind, reference_text, reference_url NULL, published_at NULL, UNIQUE(profile_key,version_number) |
| profile_rules | id, profile_version_id FK, parameter_id FK, lower_decimal TEXT NULL, upper_decimal TEXT NULL, lower_inclusive, upper_inclusive, required, unit, UNIQUE(profile_version_id,parameter_id) |
| evaluations | id, sample_id FK, profile_version_id FK, outcome, evaluated_by FK, evaluated_at, input_digest, UNIQUE(sample_id,profile_version_id) |
| rule_results | id, evaluation_id FK, parameter_id FK, result, observed_decimal TEXT NULL, observed_unit NULL, lower_snapshot NULL, upper_snapshot NULL, lower_inclusive, upper_inclusive, required, explanation, UNIQUE(evaluation_id,parameter_id) |
| alerts | id, evaluation_id FK UNIQUE, state, acknowledged_by FK NULL, acknowledged_at NULL, resolved_at NULL, version |
| corrective_actions | id, alert_id FK, assigned_user_id FK, description, due_date, state, completion_notes NULL, followup_evaluation_id FK NULL, closed_at NULL, created_at, version |
| action_notes | id, action_id FK, author_id FK, note, created_at |
| import_batches | id, file_sha256 UNIQUE, filename, publisher, dataset_title, source_url, accessed_on, license_status, transformations, provenance_kind, imported_by FK, imported_at, row_count |
| historical_observations | id, source_id FK, parameter_id FK, period_start, period_end, statistic, value_decimal TEXT, unit, import_batch_id FK, original_row, UNIQUE(import_batch_id,original_row) |
| audit_events | id, actor_id FK NULL, action, entity_type, entity_id, occurred_at, before_json NULL, after_json NULL |

Add indexes for samples(inspection_id,collected_at), readings(parameter_id,sample_id), inspections(source_id,due_date), evaluations(sample_id,evaluated_at), alerts(state), corrective_actions(state,due_date), historical_observations(source_id,parameter_id,period_start), audit_events(occurred_at).

Store canonical decimals as TEXT to round-trip BigDecimal without binary floating-point loss. Do exact comparison and aggregation in Java; never sort decimal TEXT lexicographically as numbers. Convert to doubles only at the chart boundary, retaining original decimal labels. Use SQL CHECKs for booleans/enums and schema-compatible structural constraints; services enforce cross-table state invariants.

### Connection and migration policy

Use a connection factory with `foreign_keys=ON`, a bounded `busy_timeout`, and WAL journal mode for the local database. Use one serialized write executor and a small read executor. No connection is shared across threads. All repositories in a service transaction receive the same connection through a unit-of-work object.

Run ordered migration scripts with a checksum ledger. Refuse checksum drift or a database newer than the application; never reset silently. Script execution must correctly handle SQL statements; do not blindly split arbitrary SQL on semicolons. Back up an existing database before migrations via a consistent SQLite snapshot mechanism, not a live copy of only the main file while WAL is active.

## 6. Public application-service contracts

```java
public interface EvaluationService {
    EvaluationDto evaluate(Session session, UUID sampleId, UUID profileVersionId);
}
public interface ImportService {
    ImportPreview preview(Session session, Path csv, ImportMetadata metadata);
    ImportReceipt commit(Session session, UUID previewId);
}
public interface ReportExporter {
    void export(ReportSnapshot snapshot, Path destination) throws ExportException;
}
public interface QualityRule {
    RuleResult evaluate(ReadingValue reading, RuleDefinition rule);
}
public interface TransactionManager {
    <T> T required(TransactionWork<T> work);
}
```

Additional services: `AuthenticationService`, `UserService`, `SourceService`, `InspectionService`, `SampleService`, `ProfileService`, `AlertService`, `ActionService`, `DashboardService`, `ReportService`, `AuditQueryService`.

Session is immutable identity/role context, not a database connection. Service entrypoints require a session and check current user active status where appropriate. Return application DTOs, not live JDBC results or mutable JavaFX collections.

## 7. Evaluation algorithm

1. Authorize analyst/admin and load sample, inspection/source, published profile, and exact readings.
2. Return an existing `(sample_id, profile_version_id)` evaluation if present, after authorization. Sample revisions prevent changed inputs under the same identity.
3. Reject invalid lifecycle state. For profile context mismatch, return a reasoned `NOT_EVALUABLE` response without changing sample state or persisting an evaluation.
4. For each rule, resolve canonical units. Unknown or unsupported units are invalid input, never a passing rule. Missing observation produces `MISSING`. Available observation compares BigDecimal against each non-null bound with its inclusivity flag.
5. Any FAIL → EXCEEDS_PROFILE; else any required MISSING → INCOMPLETE; else WITHIN_PROFILE. Preserve all missing required parameters even when FAIL dominates the headline outcome.
6. Create a canonical input digest over stable parameter ordering, canonical decimal values/units, and profile version. Use it for traceability, not as a host-level tamper-proof guarantee.
7. In one transaction, persist evaluation/rule snapshots, set sample EVALUATED, create one alert only if EXCEEDS_PROFILE, and append audit. A rollback leaves none of these changes committed.

No arbitrary scoring, imputation, or automatic conversion of incompatible record kinds. An INCOMPLETE sample is immutable; enter additional measurements through a revision, then resubmit/evaluate it. Historical profiles and input readings are never mutated in place.

## 8. Import contract

### Normalized long-format UTF-8 CSV

```csv
source_code,record_code,record_kind,observed_at,period_start,period_end,statistic,parameter_code,value,unit
DEMO-R01,DEMO-S001,POINT_SAMPLE,2026-10-01T09:00:00Z,,,,PH,7.2,1
DEMO-R01,DEMO-H001,PERIOD_SUMMARY,,2025-01-01,2025-12-31,MEAN,PH,7.4,1
```

The rows above are explicitly fictional template examples, not government observations. Required metadata is supplied in the import wizard and saved once per batch. Point rows grouped by `(source_code,record_code)` must share one observation time; parameter codes must be unique within the group. Imported inspection/sample codes use a reserved external prefix plus batch-derived identifiers to avoid collision with manually collected samples. Imported samples enter ANALYSIS_DRAFT, clearly tagged HISTORICAL_IMPORT; they require submission/evaluation before affecting quality counts.

For summary rows require `period_start <= period_end` and statistic MIN/MEAN/MAX. Leave observed_at empty. Different statistics are separate observations, not manufactured readings at period endpoints.

Preview limits: configurable 10 MB/50,000 rows for P0; stream parse, retain error summaries and a paginated preview, and reject overflow before commit. Store the file SHA-256 and recheck file content before commit. Keep staging in an application-owned temporary location, expire on logout/cancel, and delete on exit. Validate against current source/profile/catalog state again at commit. A repeated committed hash is a duplicate error. Errors produce row number, field, code, and plain-language correction. Never guess unknown government column meanings.

CSV export neutralizes leading spreadsheet formula markers in text fields while preserving underlying values. Tests cover quotes, embedded newlines, BOM, non-ASCII text, missing columns, duplicate rows, changed files, and invalid numeric tokens.

## 9. Corrective-action closure

Verify assigned permissions and current state. Require completion notes and a selected follow-up evaluation. Join follow-up sample to its source and confirm same source and profile version, later collection time, WITHIN_PROFILE, and complete required evidence. In a transaction close the action, append audit, and resolve the alert only when it has at least one action and all its actions are closed. A valid retest is evidence, not automatic proof that an environmental intervention caused improvement.

## 10. UI and threading

Use FXML controllers with injected services and view-model DTOs. A controller handles input collection/navigation only. Show a persistent dataset badge (DEMO/HISTORICAL/MANUAL) and profile name/version on result screens.

Use JavaFX Task/Service or CompletableFuture bridged through Platform.runLater. Never call `get()`/`join()` on the application thread. Database/import/report operations run on bounded executors; cancellation is accepted before atomic commit, not halfway through it. Disable duplicate submissions and show contextual failures. Shut down executors and close resources on application exit.

Table pages default to 50 rows with deterministic ordering. Plot at most 1,000 displayed points; aggregate/downsample explicitly with labels if larger. Keep report data unaffected by chart downsampling. Use immutable report snapshots from a single read transaction.

Screens: first-run setup/login; dashboard; source list/detail; inspection list/detail; sample editor/detail; profile editor/version detail; import wizard; alert/action detail; report builder; user administration; audit browser. Build all empty/error states, not only happy-path mockups.

## 11. Local security and logging

Use PBKDF2WithHmacSHA256 with per-user SecureRandom salt of at least 16 bytes and a 256-bit derived key; start at 600,000 iterations, benchmark on the reference laptop, and store algorithm/iterations for future upgrades. Constant-time hash comparison; passwords are never logged. Confirm parameters against current OWASP guidance before claiming production security. This is a local educational application, not a certified authentication product.

No secret keys or default real passwords in source. Demo setup asks for a demo password and labels the account. Restrict file permissions where possible. Prepared SQL only; allowlist sorting/column identifiers. No remote telemetry. Log exceptions with correlation IDs and user-friendly messages; rolling logs exclude sensitive personal fields. The SQLite database is not encrypted in P0; disclose this limitation.

## 12. Reports and backups

`ReportService` creates an immutable snapshot using explicit filters, selected profile/version, snapshot timestamp, measurements, missingness and provenance. `CsvReportExporter` and `PdfReportExporter` implement the same export port. PDFBox layouts must paginate long tables, wrap text, include units/headers, and use an embedded redistributable font with its license. Do not produce clipped screenshots as reports.

Write exports to a sibling temporary file, close, then atomically rename when supported with a safe fallback. Never claim success until completion. Backups use a consistent SQLite backup/snapshot strategy and contain schema/application versions; restore operates with no active DB connections, validates before replacement, and keeps the original until successful validation.

## 13. Test matrix

| Layer | Mandatory cases |
|---|---|
| Domain | Inclusive/exclusive bounds; single bounds; negative/decimal input; required/optional missingness; failed + missing; applicability mismatch |
| Services | All role denial paths; invalid lifecycle; archived source; revision immutability; profile publication; duplicate evaluation; action retest rules |
| Persistence | Migrations/checksum drift; FK enforcement; optimistic conflicts; atomic rollback; restart persistence; uniqueness |
| Import | BOM/Unicode/quotes/newlines; unknown units; missing data; summary separation; oversized file; changed-file rejection; duplicate hash; zero writes on invalid batch |
| Reports | Snapshot filters/counts; provenance/disclaimer; long PDF pagination; formula escaping; unwritable destination; atomic failure |
| UI smoke | Setup/login; create sample; evaluate; import errors; charts; export; keyboard navigation; logout |
| End-to-end | Failure creates alert; same-profile passing retest closes action; history remains; restart persists |

Tests use a fresh temporary on-disk SQLite database per case, fixed Clock, deterministic IDs where useful, and no network. `mvn verify` runs unit/integration tests and quality gates. Headless CI may skip display-dependent smoke tests only if documented; service integration tests remain mandatory. Record actual performance measurements separately from targets.

## 14. Build, packaging, and CI

Developer commands: `./mvnw clean verify` (Windows: `mvnw.cmd clean verify`) and `./mvnw javafx:run`. Ensure the verified JavaFX Maven plugin configuration makes the latter command real.

CI on Java 21 checks compilation, tests, JaCoCo, Checkstyle, and packages a JAR. A JAR is not automatically a self-contained desktop distribution. Add a separate Windows jpackage pipeline after the core app works, including required runtime/modules and JavaFX native libraries; validate the installed application on Windows without an existing JDK. Native packaging is platform-specific. No fabricated “build succeeded” badges or test outputs.

## 15. Antigravity execution instructions

1. Read both documents and enumerate uncertainties before coding. Keep this Java desktop architecture unless the user approves a change.
2. Produce a phase checklist with acceptance tests. Implement one vertical slice at a time, not all files as untested stubs.
3. Resolve real Maven coordinates/compatible versions and commit wrapper/pom first. Run a minimal JavaFX launch and temporary SQLite test before building screens.
4. Implement migrations and pure evaluator tests before dashboard polish.
5. Keep SQL in repositories; authorization/transactions in services; presentation in JavaFX. No hardcoded statistics, silently invented data or standards, placeholder buttons, or swallowed exceptions.
6. Never invent downloaded datasets/API endpoints. Ask for a real source file before implementing a source-specific adapter. Clearly label all fixture values fictional.
7. After each phase run applicable tests; report files changed, exact commands, actual outcomes, remaining failures, and the next phase. Do not say a command ran when it did not.
8. Before delivery, complete the PRD demo, restart persistence check, clean-clone build, and packaging validation. Document any unfinished acceptance criteria honestly.

## 16. Traceability

| PRD requirement | Primary implementation | Test focus |
|---|---|---|
| FR-01 | AuthenticationService / PasswordHasher | Hashing, disabled users, service RBAC |
| FR-02 | SourceService / InspectionService | Archive, assignment, completion |
| FR-03/04 | SampleService / ReadingValidator | Lifecycle, revisions, units |
| FR-05 | ImportService / CommonsCsvAdapter | Staging, atomicity, provenance |
| FR-06 | ProfileService / ThresholdEvaluator / EvaluationService | Versioning, boundaries, idempotency |
| FR-07 | AlertService / ActionService | Retest compatibility, transitions |
| FR-08 | DashboardService / TrendQueryRepository | Latest sample counts, filter correctness |
| FR-09 | ReportService / exporters / AuditRepository | Snapshot consistency, atomic output |

## 17. Reference boundary

The course brief defines Problem 205 as a Java platform for water-quality inspections, sample tracking, analysis records, and compliance reporting [file:3]. The technology choices, schema, limits, fixtures, and evaluation semantics in these documents are proposed specifications. Candidate dataset links in the PRD require verification before use. No official regulatory threshold is asserted in this baseline.
