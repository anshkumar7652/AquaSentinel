# AquaSentinel — Product Requirements Document

Version: 1.0 | Status: implementation baseline | Date: 2026-10-20

## 1. Product definition

AquaSentinel is an offline-first Java desktop application for managing water-quality inspections, sample tracking, analysis records, configurable rule evaluation, corrective actions, and reporting. It implements capstone Problem 205, Water Quality Monitoring Management System, under SDG 6. The supplied course brief requires a Java-based platform for sample tracking, analysis records, and compliance reporting [file:3].

This document defines product behaviour. `Achitecture.md` defines the implementation. These are proposed engineering decisions, not additional requirements imposed by the college.

## 2. Goals and success criteria

- Demonstrate a complete source → inspection → sample → readings → evaluation → alert → corrective action → follow-up workflow.
- Run without internet after dependencies and a distribution are installed.
- Show substantial Java OOP through domain objects, service interfaces, repositories, and interchangeable evaluators/exporters.
- Provide a polished, understandable five-minute academic demonstration.
- Produce a maintainable GitHub repository with reproducible builds, tests, attribution, and clear limitations.
- Support at least 10,000 reading records with pagination and background processing; verify performance on the documented test laptop rather than claiming unmeasured results.

Success means all P0 acceptance tests pass, `mvn verify` succeeds, persistent data survives restart, and a new user can follow the README to run the project.

## 3. Scope and constraints

### P0 — required release

Local authentication and roles; source management; inspection management; sample lifecycle; reading entry; normalized CSV import with preview; versioned evaluation profiles; reproducible evaluation; in-app alerts; corrective actions; charts; CSV/PDF reports; audit events; fictional demo fixtures; source provenance; automated tests.

### P1 — only after P0

Government-file adapters, additional import mappings, saved filters, verified real-data subset, database backup UI, enhanced report styling, accessible dark theme.

### Explicit exclusions

No mandatory internet API, IoT hardware, machine learning, blockchain, web frontend, Spring Boot, cloud services, SMS/email, multi-user network concurrency, certified laboratory validation, or regulatory certification. No geographic map dependency in the MVP; coordinates may be stored and shown in a source detail screen.

## 4. Safety and evidence boundaries

- Use `WITHIN_PROFILE`, `EXCEEDS_PROFILE`, `INCOMPLETE`, and `NOT_EVALUABLE`, never an unconditional “safe to drink” label.
- A profile evaluates only its listed parameters, intended water use, measurement type, units, and required evidence. Passing a subset does not establish potability.
- Surface-water observations must not be silently evaluated against a drinking-water profile.
- Historical annual summaries, point samples, and monthly observations are different record types. Do not turn minimum/maximum/mean summaries into invented individual samples.
- No universal water-quality index or arbitrary weighted health score in P0.
- Default thresholds are explicitly fictional `DEMO_ONLY` configuration, not WHO/BIS/CPCB standards. Adopt authoritative profiles only after documenting the exact publication, edition, units, applicability, and limits.
- Every view/report using demo values displays “Educational demo data — not a water-safety certification.”
- Missing readings remain missing. Zero is a measured value, not a missing-value replacement.

## 5. Users and permissions

| Capability | Administrator | Field officer | Analyst | Viewer |
|---|---|---|---|---|
| View sources/results/dashboard | Yes | Yes | Yes | Yes |
| Manage users and profiles | Yes | No | No | No |
| Create/archive sources | Yes | No | No | No |
| Schedule/complete inspections | Yes | Assigned only | No | No |
| Create/collect/receive samples | Yes | Assigned inspections only | Receive only | No |
| Edit draft readings/import observations | Yes | No | Yes | No |
| Submit/evaluate samples | Yes | No | Yes | No |
| Acknowledge alerts | Yes | Assigned items only | Yes | No |
| Work corrective actions | Yes | Assigned only | Assigned only | No |
| Close actions | Yes | No | Yes | No |
| Export scoped reports | Yes | Yes | Yes | Yes |
| View audit trail | Yes | No | No | No |

Enforce permissions in application services, not merely by hiding UI controls. All accounts are local to one installation. A desktop login is not protection against an attacker controlling the host or database files.

## 6. Functional requirements

### FR-01 — setup and authentication

First run creates schema and launches an administrator setup form. No committed production passwords. Passwords are salted and hashed; sign-in errors do not reveal whether a username exists. Disabled users cannot sign in. Logout clears the session and returns to login. First-run demo installation is an explicit opt-in action, never an automatic destructive reset.

Acceptance: incorrect password fails; disabled user fails; unauthorized service calls fail even without the UI; password hashes are not plaintext.

### FR-02 — sources and inspections

Create a source with unique code, name, type, intended use, location text, optional latitude/longitude, and active flag. Validate coordinate ranges. Archive rather than delete referenced sources. Schedule an inspection with source, officer, due date, and notes; track `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`.

Acceptance: source codes remain unique; archived sources retain history and cannot receive new inspections; cancelled inspections cannot collect new samples; completion requires at least one collected sample or an explicit no-sample reason.

### FR-03 — sample tracking

Create a uniquely coded sample under an inspection, with collection time, collector, laboratory receipt time, and record origin. Workflow: `REGISTERED → COLLECTED → RECEIVED → ANALYSIS_DRAFT → SUBMITTED → EVALUATED`. Rejection before evaluation requires a reason and creates `REJECTED`. Capture notes and chain-of-handling events, without claiming forensic certification.

Acceptance: receive time cannot precede collection; drafts cannot be evaluated; submitted readings are locked; corrections create a new sample revision linked to the superseded sample, not silent edits. An evaluation requires a compatible published profile.

### FR-04 — parameters and readings

Catalog parameters by stable code, display name, canonical unit, and input constraints. Store numerical values as decimal values; unitless parameters explicitly use unit `1`. Reject nonfinite values and invalid decimal syntax. Only implement documented physical input constraints; do not confuse a plausible range with an acceptance limit.

P0 supports exact numerical observations only. Text such as `<0.1`, `ND`, `trace`, and ranges is preserved during import preview and rejected from numeric evaluation with an actionable message, not silently converted. Missing readings are not inserted as zeros.

Acceptance: one reading per parameter per sample revision; no duplicate parameter entries; mismatched units require an explicit allowlisted conversion or rejection.

### FR-05 — CSV imports and provenance

Provide a documented normalized long-format template. Import to staging, preview parsed rows, map source and parameter codes, show validation failures, and commit all accepted data in one transaction only after user confirmation. P0 is all-or-nothing: errors prevent commit. Cancellation before commit changes nothing.

Support two explicit record kinds: `POINT_SAMPLE` and `PERIOD_SUMMARY`. Point samples create inspection/sample records with imported origin; summaries remain historical observations and do not enter operational sample evaluation. Record publisher, dataset title, original URL, access date, license/redistribution status, raw-file SHA-256, original row number, and any transformations.

Acceptance: preview counts reconcile; unknown codes fail clearly; duplicate file content cannot be committed twice; retry after failure does not duplicate rows; period summaries are never plotted as invented point samples.

### FR-06 — evaluation profiles

An administrator creates immutable published profile versions with name, intended use, source applicability, record-kind applicability, reference, provenance class (`DEMO_ONLY` or `REFERENCED`), and rules. Each rule contains parameter, canonical unit, optional lower/upper bounds, inclusion flags, and required flag. At least one bounded rule is required. Published profiles cannot be edited; new limits require a new version.

Every evaluation stores the exact profile version and rule results. For each rule return `PASS`, `FAIL`, or `MISSING`. Any available-rule failure yields `EXCEEDS_PROFILE`; otherwise any missing required rule yields `INCOMPLETE`; otherwise return `WITHIN_PROFILE`. Incompatible context yields `NOT_EVALUABLE`, with a reason and no alert. If a failed sample also lacks evidence, show both violations and missing required parameters.

Acceptance: exact boundary inclusivity is tested; missing required data never passes; optional missing data does not fail; historical evaluations remain unchanged after profile updates.

### FR-07 — alerts and corrective actions

Create one open alert per failed evaluation, with linked violations and profile context. Use `OPEN`, `ACKNOWLEDGED`, `RESOLVED`; acknowledgment is not resolution. Repeated requests return the original evaluation/alert instead of duplicating effects. Profiles do not infer health severity; use a neutral “Profile violation” label.

Create assignable actions with description, due date, notes, and states `OPEN`, `IN_PROGRESS`, `AWAITING_RETEST`, `CLOSED`. Closing requires completion notes and a later compatible follow-up evaluation at the same source with `WITHIN_PROFILE`, no missing required parameters, and the same profile version. The follow-up must follow the triggering collection time. Closing the action resolves its associated alert only when all its linked actions are closed. Retests never overwrite historical failures.

Acceptance: a passing retest alone does not silently close an action; missing/invalid retests prevent closure; transitions and ownership are checked in services.

### FR-08 — dashboard and trends

Show filterable counts for active sources, collected samples, evaluated samples, profile violations, incomplete results, and unresolved alerts. State whether a metric counts samples, evaluations, or alerts. Default sample classification charts use the latest evaluation per non-superseded sample; never mix profile versions into a single compliance rate.

Trend view: one parameter, canonical unit, source, and record kind at a time; date range filter; labelled points; profile bounds only where applicable. Display observation count, time range, provenance, and missing-data notes. Period-summary charts are a separately labelled view with min/mean/max statistic selection.

Acceptance: empty datasets show an explanation and import action; dashboard updates after commit; chart points come from persisted observations, not random values; filters match exported records.

### FR-09 — reports and audit

Export filtered CSV and PDF with generated-at timestamp, filters, source/sample identifiers, record kinds, measurements/units, profile/version, violations, missing evidence, provenance classification, and disclaimer. Reports are “Profile evaluation reports,” not government-issued certificates.

Audit successful mutations in the same transaction with actor, action, entity ID, timestamp, and safe before/after metadata. Never log passwords, session secrets, or hashes used for authentication. Audit immutability is application-level only; do not claim host-level tamper proofing.

Acceptance: exports reflect a consistent data snapshot; output is written atomically; CSV formula-leading fields are escaped for spreadsheet safety; failed exports leave no misleading success state.

## 7. UX requirements

Sidebar: Dashboard, Sources, Inspections, Samples, Imports, Alerts & Actions, Reports, Administration. Use white/slate surfaces, restrained teal accent, readable type, visible labels, and status text/icons rather than color alone. Supply FXML/CSS and bundled fonts/assets so presentation does not require a CDN.

Every screen handles loading, empty, validation, error, and success states. Tables use pagination and filters. Confirm archive, publication, submission, and action closure. Show importer progress and allow cancellation before commit. No blocking I/O on the JavaFX application thread. Keyboard access and visible focus are required.

## 8. Dataset plan

Official discovery sources: National Water Data Portal https://nwdp.nwic.gov.in/ ; CPCB https://cpcb.nic.in/nwmp-data/ ; Open Government Data https://www.data.gov.in/ . These URLs are candidate sources, not a claim that any particular file, API, state, license, or period has been verified.

Begin with a clearly fictional, deterministic fixture dataset and fictional demo profiles. Add a real-data adapter only after inspecting an actual download. Prefer a small attributable Delhi/Uttar Pradesh subset if available; do not invent government observations, dates, coordinates, labels, row counts, or endpoints. Keep raw/processed boundaries and transformation documentation. Do not redistribute data whose license is unclear; provide download/import instructions instead. An unavailable dataset must not block the offline product.

## 9. Nonfunctional acceptance targets

- Reproducibility: Maven wrapper, exact dependency/plugin versions, Java 21 compatibility, no dynamic/LATEST versions.
- Performance targets: first paginated view within 2 seconds and 10,000-row preview/import within 30 seconds on a documented reference laptop. Measure and report; never fabricate benchmarks.
- Responsiveness: blocking database/import/report work runs on bounded background executors; UI provides busy state and prevents duplicate submission.
- Durability: foreign keys, transactions, schema migrations, rollback tests, and safe backups.
- Security: prepared SQL, permission checks, restrictive local file permissions where supported, no hardcoded secrets, no telemetry.
- Packaging: documented development command and Windows-first packaged application; platform-specific packaging tested on its target OS.
- Quality: JaCoCo target at least 80% line coverage for domain/evaluation services, excluding UI/generated classes; prioritize boundary and failure tests over gaming percentages.

## 10. Implementation phases and gates

1. Bootstrap: Maven wrapper, JavaFX shell, CI, schema migrations, temporary-DB test. Gate: clean build and persistent connection.
2. Identity and sources: setup, authentication, permissions, source/inspection CRUD. Gate: service authorization tests.
3. Samples and readings: lifecycle, validation, revisions. Gate: state-transition tests and restart persistence.
4. Profiles and evaluation: publish/version rules, pure evaluator, atomic evaluation and alert. Gate: boundary/missing/context/idempotency tests.
5. Import: template, staging preview, provenance, transaction/deduplication. Gate: invalid CSV rollback and repeat-import tests.
6. Actions and analytics: retest closure, dashboard, trends. Gate: consistent counts and retest checks.
7. Reports and delivery: PDF/CSV, demo fixtures, packaging, README, screenshots. Gate: five-minute demo and full `mvn verify`.

Antigravity must implement one phase at a time, execute tests after each phase, report actual command output/failures, and stop for clarification when requirements conflict. Do not replace JavaFX with a browser dashboard or add speculative infrastructure to make generation easier.

## 11. Demonstration script

Start in an explicitly labelled demo database. Log in as administrator; show sources and historical trends; create an inspection/sample; enter readings against a fictional profile; submit and evaluate; inspect violated bounds; assign an action; add a later passing retest; close the action; export the report; restart to show persistence. Also demonstrate one missing required measurement producing `INCOMPLETE`.

## 12. Definition of done

All P0 acceptance tests pass; no placeholder actions or hardcoded chart statistics; clean first-run setup works; all state changes persist; audit and profile provenance are visible; datasets are attributed or clearly fictional; README includes commands, limitations and demo walkthrough; screenshots show the actual application; generated build outputs, database files, personal records, and secrets are excluded from Git.
