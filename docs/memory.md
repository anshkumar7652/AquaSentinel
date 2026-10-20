# AquaSentinel — Project Context & Implementation Memory

**Document Version:** 1.0  
**Last Updated:** 2026-10-20  
**Context Purpose:** Read this file when starting a new chat session to immediately resume work without re-explaining the architecture, requirements, environment, or progress.

---

## 1. Project Overview & Core Constraints

- **Project Name:** AquaSentinel
- **Topic:** Capstone Problem 205: *Water Quality Monitoring Management System* (SDG 6).
- **Core Documents:**
  - PRD: [docs/Prd.md](file:///c:/ANSH(DRIVE-D)/Workspace-01/Projects/oots-project/docs/Prd.md)
  - Architecture: [docs/Achitecture.md](file:///c:/ANSH(DRIVE-D)/Workspace-01/Projects/oots-project/docs/Achitecture.md)
  - Presentation / UI Spec: [docs/design/design.md](file:///c:/ANSH(DRIVE-D)/Workspace-01/Projects/oots-project/docs/design/design.md)
  - Reference Dashboard Mockup: [docs/design/reference-dashboard.jpg](file:///c:/ANSH(DRIVE-D)/Workspace-01/Projects/oots-project/docs/design/reference-dashboard.jpg)
- **Technical Stack:**
  - **Java:** JDK 26 installed on host; project compiles with `--release 21` (Java 21 source/bytecode compatibility).
  - **UI:** JavaFX 21 (`javafx-controls`, `javafx-fxml`) + FXML + Vanilla JavaFX CSS (No WebView, no React, no Web wrappers).
  - **Build Tool:** Apache Maven 3.9.9 via committed wrapper (`.\mvnw.cmd` on Windows).
  - **Database:** SQLite via Xerial JDBC (`sqlite-jdbc` 3.46.1.0).
  - **Data/Docs:** Apache Commons CSV (RFC 4180), Jackson Databind, Apache PDFBox.
  - **Logging & Testing:** SLF4J + Logback Classic, JUnit 5 Jupiter, Mockito.

---

## 2. Fundamental Architectural & Scientific Rules

1. **Safety Claims Boundary (Non-Negotiable):**
   - The application evaluates samples strictly against a specific selected evaluation profile version (e.g., demo profiles).
   - Valid outcomes: `WITHIN_PROFILE`, `EXCEEDS_PROFILE`, `INCOMPLETE`, `NOT_EVALUABLE`.
   - **Never** state "Safe to drink" or claim universal potability.
   - All fixtures and default data must display: *"Educational demo data — not a water-safety certification."*
2. **Strict Layered OOP Architecture:**
   - **Domain:** Pure Java (`BigDecimal` values, domain records/entities, pure evaluation logic). Absolutely no JavaFX or JDBC imports.
   - **Application Services:** Authorization, session validation, business transactions, audit logging.
   - **Repository Ports & Infrastructure:** Repositories are interfaces in `application/port/` implemented with raw prepared JDBC in `infrastructure/repository/` (no ORM).
   - **Presentation:** JavaFX controllers receive immutable DTOs from services. Controllers **never** execute SQL or direct database queries.
3. **Database & Transaction Policies:**
   - SQLite connections must have `PRAGMA foreign_keys = ON`, `busy_timeout = 5000`, and `journal_mode = WAL`.
   - Single-writer / multi-reader pattern. All mutations for a workflow (e.g., sample evaluation + alert creation + audit event) commit in a single database transaction.
   - Numbers and bounds stored as `TEXT` to preserve exact `BigDecimal` decimal representations without floating-point precision loss.
4. **Resolved Mockup vs. PRD Contradictions:**
   - When the AI mockup disagrees with `Prd.md` or `design.md`, **the written specs prevail**:
     - No "Overall Quality Index".
     - No "Real-time" / IoT indicators (offline-first local app).
     - No embedded web map widget (coordinates displayed as text).
     - No multi-parameter shared-axis charts (one parameter + unit per chart).
     - Navigation follows PRD (8 sections).

---

## 3. Directory & Package Structure

Root directory: `c:\ANSH(DRIVE-D)\Workspace-01\Projects\oots-project`

```text
oots-project/
├── pom.xml                                   # Root Maven configuration (Java 21 target)
├── mvnw / mvnw.cmd / .mvn/                   # Committed Maven wrapper (3.9.9)
├── .gitignore                                # Ignores target/, *.db, *.db-wal, IDE files
├── README.md                                 # Project instructions & command reference
├── docs/
│   ├── Prd.md                                # Product Requirements Document
│   ├── Achitecture.md                        # Architecture & schema specification
│   ├── memory.md                             # THIS context memory file
│   └── design/
│       ├── design.md                         # Presentation tokens & screen rules
│       ├── reference-dashboard.jpg           # Approved visual direction
│       └── AquaSentinel desktop mockup.jpg   # Original mockup asset
├── data/
│   ├── templates/                            # CSV import templates
│   └── demo/                                 # Demo dataset fixtures
└── src/
    ├── main/
    │   ├── java/com/aquasentinel/
    │   │   ├── app/                          # AquaSentinelApp, AppBootstrap, AppContext
    │   │   ├── domain/                       # model/, enums/, evaluation/, exception/
    │   │   ├── application/                  # service/, dto/, security/, port/
    │   │   ├── infrastructure/
    │   │   │   ├── db/                       # ConnectionFactory, SchemaMigrator
    │   │   │   ├── repository/               # JDBC implementations
    │   │   │   ├── csv/, pdf/, security/     # PBKDF2PasswordHasher, exporters
    │   │   └── presentation/                 # controller/, component/, navigation/
    │   └── resources/
    │       ├── fxml/                         # app-shell.fxml, dashboard.fxml
    │       ├── css/                          # app.css (Teal #0F766E palette, design tokens)
    │       ├── db/migration/                 # V001__initial.sql
    │       └── logback.xml
    └── test/
        ├── java/com/aquasentinel/            # SchemaMigratorTest, unit & integration tests
        └── resources/fixtures/
```

---

## 4. Current Progress & Milestones

| Milestone | Status | Description |
|-----------|--------|-------------|
| **Milestone 1: Application Shell & Bootstrap** | **COMPLETED** | Maven Wrapper configured, SQLite WAL + FK connection factory, `V001__initial.sql` (15 tables) migration ledger with SHA-256 drift checks, `app.css` design tokens, `app-shell.fxml` 8-item sidebar, `dashboard.fxml`, `AquaSentinelApp` verified running. |
| **Milestone 2: Identity & Sources** | **COMPLETED** | First-run setup form, PBKDF2 password hashing (600k iterations, 16-byte salt), `AuthenticationService`, `Session`, `UserService`, `SourceService` (CRUD/archive), `InspectionService`, RBAC enforcement in services, audit logging, and `sources.fxml` presentation. |
| **Milestone 3: Samples & Readings** | **COMPLETED** | Sample lifecycle (`REGISTERED` → `COLLECTED` → `RECEIVED` → `ANALYSIS_DRAFT` → `SUBMITTED`), `BigDecimal` reading validation against parameter constraints, `V002` baseline parameters seed, submission locking, and `supersedes_sample_id` revision tracking. |
| **Milestone 4: Profiles & Pure Evaluation** | **COMPLETED** | Pure domain `ThresholdEvaluator` engine, inclusive/exclusive bounds, required missingness detection, context checking (`NOT_EVALUABLE`), SHA-256 input digests, `V003` baseline profile seed, atomic evaluation + open alert creation in single transaction, and idempotent re-evaluation. |
| **Milestone 5: CSV Import Wizard** | **COMPLETED** | Normalized long-format CSV parser (RFC 4180), SHA-256 batch duplicate check, staging and validation error summaries, POINT_SAMPLE vs PERIOD_SUMMARY separation, atomic commit, and import template in `data/templates/`. |
| **Milestone 6: Corrective Actions & Dashboard** | **COMPLETED** | Assignable actions lifecycle (`OPEN`, `IN_PROGRESS`, `AWAITING_RETEST`, `CLOSED`), verified follow-up retest passing validation (same source, same profile, subsequent timestamp, no missing required), auto-resolution of alerts, and `DashboardService` live KPI summary and trend series queries. |
| **Milestone 7: Reports & Final Delivery** | **COMPLETED** | `ReportService` snapshot aggregator, `CsvReportExporter` with spreadsheet formula injection protection, `PdfReportExporter` with PDFBox 3.x paginated layout + scientific disclaimers, educational demo fixtures in `data/demo/delhi_monitoring_demo.csv`, and 25 unit/integration tests passing. |
| **Presentation Layer & UI Features** | **COMPLETED** | All 8 navigation sections fully functional with dedicated FXML and controllers: live Dashboard with single-parameter trend switching and sample evaluation inspector; Sources registration and real-time search; Inspections tour scheduling; Samples lifecycle and profile evaluation modal; Data Imports wizard with RFC 4180 validation and batch commit; Alerts & Corrective Actions with verified closure; Reports with PDF/CSV export and auto-open; Admin governance with user management and demo reset. Auto-seeding of baseline demo dataset on initial startup. |

---

## 5. Academic Demonstration Script (5-Minute Walkthrough)

To present the capstone project live or during academic evaluation:

1. **Bootstrap & First Run (`Milestone 1 & 2`):**
   - Run `.\mvnw.cmd javafx:run`
   - Observe the initial setup administrator screen.
   - Enter administrator credentials and log in.
   - Inspect the 8-item navigation sidebar, dark/teal professional palette, and live system readiness metrics.
2. **Sources & Inspections (`Milestone 2`):**
   - View water sources and geographic coordinates (e.g., Haridwar Ghat, River Station).
   - Show field inspection scheduling and officer assignment.
3. **Sample Registration & Reading Entry (`Milestone 3`):**
   - Register a water sample for an inspection.
   - Transition state: `REGISTERED` → `COLLECTED` → `RECEIVED` → `ANALYSIS_DRAFT`.
   - Enter laboratory analytical readings with parameter physical boundary validation.
   - Submit sample (locking it from further manual edits).
4. **Reproducible Threshold Evaluation (`Milestone 4`):**
   - Select evaluation profile (e.g. `SURFACE_WATER_DEMO v1`).
   - Run evaluation. Highlight:
     - Pure domain deterministic evaluation report.
     - SHA-256 cryptographic input digest.
     - Safety disclaimer: *"Educational demo data — not a water-safety certification."*
     - Automatic opening of alert when profile thresholds are exceeded.
5. **CSV Import Wizard (`Milestone 5`):**
   - Load `data/demo/delhi_monitoring_demo.csv`.
   - Review pre-commit preview, duplicate batch SHA-256 detection, and point-sample vs period-summary separation.
   - Atomically commit the batch.
6. **Corrective Action & Retest Workflow (`Milestone 6`):**
   - Acknowledge open alert.
   - Assign corrective action to field officer (`OPEN` → `IN_PROGRESS` → `AWAITING_RETEST`).
   - Demonstrate that closing requires verified follow-up passing retest.
   - Execute passing retest and verify automatic alert resolution and updated dashboard KPIs.
7. **Exporting Reports (`Milestone 7`):**
   - Export CSV report (formula injection safe).
   - Export official PDF evaluation report with PDFBox paginated table and scientific disclaimers.

Run automated tests:
```powershell
.\mvnw.cmd test
```

Full project build and verification:
```powershell
.\mvnw.cmd clean verify
```

Run JavaFX desktop application:
```powershell
.\mvnw.cmd javafx:run
```

Run application with custom local database / data path:
```powershell
.\mvnw.cmd javafx:run -Djavafx.args="--data-dir C:\custom\data\path"
```
