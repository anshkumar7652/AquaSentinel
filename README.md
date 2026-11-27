# AquaSentinel

AquaSentinel is an offline-first Java 21 desktop application for managing water-quality inspections, sample tracking, analysis records, configurable rule evaluation, corrective actions, and reporting. It addresses capstone **Problem 205: Water Quality Monitoring Management System** under UN SDG 6.

## Safety & Educational Boundary
> **Important Notice:**
> AquaSentinel evaluates sample measurements strictly against a selected, configurable quality evaluation profile (e.g., demo thresholds). It **never** issues an unconditional "safe to drink" certification. All fixture values are educational demo data.

---

## Technical Stack & Architecture
- **Language & Runtime:** Java 21 (tested on JDK 26 runtime with `-release 21`)
- **UI Framework:** JavaFX 21 + FXML + CSS (native desktop application, no WebView or web dependencies)
- **Database:** SQLite via Xerial JDBC (WAL mode, foreign keys enforced)
- **Security & Cryptography:** PBKDF2 with SHA-256 password hashing and secure salting
- **Data & Documents:** Apache Commons CSV, Jackson JSON, Apache PDFBox
- **Testing:** JUnit 5, Mockito (27 unit, integration, and end-to-end tests passing)
- **Distribution & Packaging:** Maven Shade Plugin (Uber-JAR) & JDK `jpackage` (zero-install native Windows `.exe`)

---

## Quickstart & Launch Options

### 1. Developer 1-Click Launcher (Windows)
If you have Java installed locally:
```cmd
.\run-aquasentinel.bat
```
Or via Maven wrapper directly:
```bash
.\mvnw.cmd clean javafx:run
```

Run test suite:
```bash
.\mvnw.cmd clean verify
```

Run with a custom database / data directory:
```bash
.\mvnw.cmd javafx:run -Djavafx.args="--data-dir C:\path\to\data"
```

---

## Zero-Install Portable Windows Distribution (For Non-Technical Users)

For sharing with users who do **not** have Java, Maven, or development tools installed:

### 1. Build the Standalone Package
Double-click [build-portable-app.bat](file:///c:/ANSH(DRIVE-D)/Workspace-01/Projects/oots-project/build-portable-app.bat) or run from terminal:
```cmd
.\build-portable-app.bat
```

### 2. Output Artifacts Produced
The script automatically builds:
1. **Unpacked Native Application:** `dist\AquaSentinel\`
   * Contains `AquaSentinel.exe` and a private, bundled Java runtime (`runtime\`).
2. **Shareable Portable ZIP:** `dist\AquaSentinel-v1.0.0-Portable-Windows.zip` (~48 MB)

### 3. How the End-User Runs It
1. Extract `AquaSentinel-v1.0.0-Portable-Windows.zip`.
2. Double-click `AquaSentinel.exe`.
3. **No prerequisites required:** The user does not need to install Java, Git, or run command-line commands. SQLite database initializes automatically in their local user profile directory.

---

## Application Workflows & Features

### 1. Authentication & Role-Based Access Control (RBAC)
- **First-Run Bootstrap:** On an empty database, launches a first-run administrator setup screen to register the initial superadmin.
- **Login Screen:** Dedicated authentication view with password masking, error feedback, and quick-fill buttons for demo credentials:
  - Administrator: `admin` / `admin123`
  - Analyst: `analyst1` / `analyst123`
- **Session Security:** Session context tracks logged-in user details and roles (`ADMINISTRATOR`, `ANALYST`, `AUDITOR`).
- **Sidebar Session Logout:** Clean logout control in the main shell that terminates user session and returns to login.

### 2. Core Functional Modules
- **Operational Dashboard:** Summary KPI cards, parameter trend line charts, recent sample history, and active critical alerts overview.
- **Water Sources & Sites:** Registration and monitoring site tracking (latitude/longitude coordinates, source types, operational status).
- **Inspections Management:** Scheduled, routine, and emergency sanitary inspection records linked to water sources.
- **Water Samples & Decimal Readings:** Discrete point samples with multi-parameter readings (`BigDecimal` precision) and audit history.
- **Threshold Rule Evaluation Engine:** Configurable profile limits (min/max/recommended ranges). Evaluates status (`WITHIN_PROFILE`, `EXCEEDS_PROFILE`, `INCOMPLETE`, `NOT_EVALUABLE`).
- **Alerts & Corrective Actions Lifecycle:** Automated alert generation on threshold violations with formal corrective action workflows (Open → In Progress → Closed with re-test linkage).
- **CSV Data Import Wizard:** Staged CSV parser with SHA-256 batch deduplication, validation dry-run, and batch rollbacks.
- **PDF & CSV Export:** Clean PDF summary reports with tabular records and parameter compliance snapshots.
- **System Administration:** User management, role assignment, active rule profiles, and demo data reload tools.

---

## Project Structure

```text
oots-project/
├── pom.xml                                   # Root Maven configuration & shade plugin
├── mvnw / mvnw.cmd / .mvn/                   # Committed Maven wrapper
├── run-aquasentinel.bat                      # 1-Click developer launcher
├── build-portable-app.bat                    # 1-Click zero-install standalone bundler (jpackage)
├── dist/                                     # Output folder for native .exe & portable zip
├── src/
│   ├── main/
│   │   ├── java/com/aquasentinel/
│   │   │   ├── app/                          # AquaSentinelApp, AppLauncher, AppBootstrap, AppContext
│   │   │   ├── domain/                       # Pure domain entities, enums, evaluation logic
│   │   │   ├── application/                  # Business services, DTOs, security, ports
│   │   │   ├── infrastructure/               # JDBC repositories, SQLite migrator, CSV/PDF exporters
│   │   │   └── presentation/                 # JavaFX FXML controllers (Login, Shell, Dashboard, etc.)
│   │   └── resources/
│   │       ├── fxml/                         # UI layout views (login, app-shell, admin, reports, etc.)
│   │       ├── css/                          # JavaFX theme & design tokens (Teal palette)
│   │       ├── db/migration/                 # Flyway-style SQL migrations (V001..V003)
│   │       ├── icons/                        # Application branding assets
│   │       └── logback.xml                   # Logging configuration
│   └── test/                                 # Unit, integration, and E2E test suites
└── docs/                                     # PRD, Architecture, and implementation memory
```
