# AquaSentinel

AquaSentinel is an offline-first Java 21 desktop application for managing water-quality inspections, sample tracking, analysis records, configurable rule evaluation, corrective actions, and reporting. It addresses capstone **Problem 205: Water Quality Monitoring Management System** under UN SDG 6.

## Safety & Educational Boundary
> **Important Notice:**
> AquaSentinel evaluates sample measurements strictly against a selected, configurable quality evaluation profile (e.g., demo thresholds). It **never** issues an unconditional "safe to drink" certification. All fixture values are educational demo data.

---

## Technical Stack & Architecture
- **Language & Runtime:** Java 21 (tested on JDK 26 runtime with `-release 21`)
- **UI Framework:** JavaFX 21 + FXML + CSS (native desktop application)
- **Database:** SQLite via Xerial JDBC (WAL mode, foreign keys enforced)
- **Data & Documents:** Apache Commons CSV, Jackson JSON, Apache PDFBox
- **Testing:** JUnit 5, Mockito

---

## Quickstart & Build Commands

Build the project and run test suites:
```bash
.\mvnw.cmd clean verify
```

Run the desktop application:
```bash
# 1-Click desktop launcher (Windows):
.\run-aquasentinel.bat

# Or via Maven wrapper:
.\mvnw.cmd javafx:run
```

Run with custom data directory:
```bash
.\mvnw.cmd javafx:run -Djavafx.args="--data-dir C:\path\to\data"
```

---

## Implementation Progress
- [x] **Milestone 1:** Application shell, Maven wrapper, SQLite WAL connection & migration ledger, design tokens, navigation.
- [x] **Milestone 2:** User authentication, RBAC, sources & inspections management.
- [x] **Authentication & Startup Flow:** Interactive login screen (`login.fxml`), first-run administrator setup, PBKDF2 hash verification, demo account quick-fill (`admin` / `analyst1`), and sidebar session logout.
- [x] **Milestone 3:** Water samples lifecycle, decimal readings, and revisions.
- [x] **Milestone 4:** Threshold rule profiles, pure evaluation engine, and alert generation.
- [x] **Milestone 5:** CSV import wizard with staging and batch provenance.
- [x] **Milestone 6:** Corrective action closure with retests & operational dashboard KPIs.
- [x] **Milestone 7:** CSV/PDF reports export & final demonstration script.
- [x] **Full E2E Test Suite & 1-Click Launcher:** 27 unit, integration, and E2E tests passing. `run-aquasentinel.bat` added.
