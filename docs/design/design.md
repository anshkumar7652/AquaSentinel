# AquaSentinel — Desktop UI Design Specification

Version: 1.0 | Date: 2026-10-20 | Target: Java 21 + JavaFX + FXML + CSS

## 1. Purpose and reference

Implement the light-theme desktop dashboard direction approved by the user: a professional AquaSentinel application with a left sidebar, header, KPI cards, trend charts, recent samples, alert badges, and a contextual evaluation panel. Use restrained teal and slate, subtle depth, softly rounded cards, and clear typography.

The approved AI-generated dashboard image is the visual reference, not an implemented screenshot or a source of factual measurements. Its reference in the conversation is [generated_image:186]. Save the approved image locally as `docs/design/reference-dashboard.jpg` if available; do not depend on an expiring image URL at runtime. This specification describes the intended direction rather than a pixel-perfect transcription of the generated image. Exact tokens and dimensions below are proposed implementation values.

This file governs presentation only. `Prd.md` governs product behaviour and `Achitecture.md` governs technical implementation. Preserve those documents' safety, roles, immutable evaluations, provenance, and workflow requirements. In a conflict, preserve the functional contract and ask before changing architecture.

## 2. Design character

- Calm, modern environmental-monitoring workspace, not a futuristic control room.
- Predominantly white and pale-slate surfaces with teal used for selection and primary actions.
- Dense enough for real tables but generous enough for an academic projector demonstration.
- Dashboard hierarchy: operational summary → trends → sample evidence → next action.
- Honest evidence labels: clearly distinguish fictional fixtures, imported historical observations, and manual entries.
- No decorative water photographs, animated waves, glass effects, neon glows, gradient text, or decorative 3D objects inside the app.
- JavaFX desktop application only; do not substitute an HTML/WebView screenshot, React application, or static image for working controls.

## 3. Application shell

### Window

Design at a 1440 × 900 logical-pixel viewport, with a supported minimum of 1100 × 720. Dimensions are JavaFX logical coordinates, not fixed physical pixels. Test on Windows at 100%, 125%, and 150% scaling. Begin with native operating-system window chrome; do not spend the MVP on custom minimize/maximize/resize controls. The reference may be laptop-framed, but the actual interface must fill its application window without a laptop-image background.

Use a root BorderPane: navigation on the left, workspace on the center. The workspace contains a 72 px header and one vertically scrolling main area. Only tables and drawers may have local scrolling where necessary. Avoid a fixed screenshot-sized Canvas.

### Navigation

Sidebar width: 224 px expanded; optional 72 px compact mode below 1200 px. Use a muted surface, a subtle right divider, and a persistent AquaSentinel wordmark with a simple droplet/shield mark drawn using local vector shapes or a bundled asset.

Items, in order:

1. Dashboard
2. Water Sources
3. Inspections
4. Samples
5. Data Imports
6. Alerts & Actions
7. Reports
8. Administration — visible only to authorized users

Use 44 px rows with 16–18 px icons and 12 px icon-to-label gaps. Selected navigation has a pale teal background and dark teal label/icon, not an oversized painted side border. Add a small count badge only for genuinely unresolved alerts. At the bottom show signed-in display name, role, and an explicit logout control. Compact icons need accessible names and tooltips.

### Header

Left: page title and a short context line. Right: provenance/demo badge when applicable, persisted alert count, account/menu, and a single context-appropriate primary action. On the dashboard use `New sample` for users permitted to create one; otherwise use `View samples` or omit it. Do not expose actions the role cannot perform.

Do not show a fabricated live synchronization indicator. Show `Local database` and the last successful import time only when it exists. Offline-first describes this local application, not a simulated sensor connection.

## 4. Design tokens

Use these as the baseline palette. Validate contrast in rendered controls and adjust if necessary, preserving semantic roles.

| Token | Value | Use |
|---|---|---|
| background | #F4F7F9 | Main workspace |
| surface | #FFFFFF | Cards, table, inputs |
| surface-subtle | #F8FAFC | Sidebar and table header |
| surface-hover | #EEF3F6 | Interactive hover |
| text-primary | #162B3A | Headings and important values |
| text-secondary | #475569 | Supporting text |
| text-muted | #64748B | Timestamps and secondary labels |
| border | #DFE7ED | Quiet dividers |
| teal | #0F766E | Primary buttons and selected text |
| teal-hover | #115E59 | Primary hover |
| teal-soft | #E6F4F1 | Selected navigation and informational chips |
| info | #1D4ED8 | Information |
| info-soft | #EFF6FF | Information surfaces |
| success | #166534 | Within-profile label |
| success-soft | #F0FDF4 | Within-profile badge |
| warning | #92400E | Incomplete evidence label |
| warning-soft | #FFFBEB | Incomplete badge |
| danger | #B91C1C | Profile violations and validation errors |
| danger-soft | #FEF2F2 | Violation badge |
| focus | #0F766E | Keyboard focus outline |

Never use pale text for important instructions. Status includes words and/or an icon, not color alone. Primary buttons have white text on teal; secondary actions use neutral surfaces and borders. Red is reserved for violations and destructive operations, not ordinary selection.

### Typography

Use a bundled, redistributable Inter Regular/Medium/Semibold font, including its license. Fall back to Segoe UI/System if unavailable. No runtime font CDN. Use consistent numeric alignment; prefer a font's tabular numerals where supported, otherwise align numeric table columns to the right.

| Role | Size | Weight |
|---|---:|---|
| Page title | 26 px | Semibold |
| KPI value | 28 px | Semibold |
| Section/card title | 16 px | Semibold |
| Body/table text | 14 px | Regular |
| Buttons/navigation | 14 px | Medium |
| Metadata/badges | 12 px | Medium |

Never render functional text below 12 px. For presentation mode or larger accessibility settings, allow the base font to increase without clipping.

### Spacing, geometry, and depth

Use a 4 px base: 4, 8, 12, 16, 20, 24, 32. Main content padding 24 px; grid gaps 16–20 px; card padding 20 px. Card radius 12 px; input/button radius 8 px; badges 999 px. One restrained shadow on elevated cards, for example black at 5% opacity, radius 12, vertical offset 3. Avoid multiple shadows on every nested panel.

Prefer alignment and spacing to decorative borders. Controls use a 40 px preferred height, navigation and key row actions use 44 px touch/click targets. Icons alone are not labels unless tooltips and accessible names are provided.

## 5. Dashboard composition

### Layout reference

```text
┌───────────────┬──────────────────────────────────────────────────────┐
│ AquaSentinel  │ Dashboard                [Demo]      [+ New sample]  │
│               ├──────────────────────────────────────────────────────┤
│ Dashboard     │ [Source] [Date range] [Profile/version] [Apply]      │
│ Water Sources │                                                      │
│ Inspections   │ [Active sources] [Samples] [Violations] [Open alerts] │
│ Samples       │                                                      │
│ Data Imports  │ [Parameter trend: one parameter]  [Evaluation detail]│
│ Alerts/Actions│ [Source / unit / evidence labels] [Rules / missing]  │
│ Reports       │                                                      │
│ Administration│ [Recent samples table.............................] │
│               │ [Unresolved alerts and next actions..............] │
│ User / Logout │                                                      │
└───────────────┴──────────────────────────────────────────────────────┘
```

At wide widths, use a 2:1 split for the trend card and contextual evaluation panel. If the workspace is narrow, stack the detail panel below the chart. Do not squeeze the sample table into unreadable columns. The expanded navigation may collapse before content is truncated.

### Filters

Provide source, date range, and explicit profile/version filters. Display active filter chips or a concise summary and a `Clear filters` action. Define date inclusivity in the service contract. Evaluation classification metrics apply to the selected profile/version and latest relevant evaluation per non-superseded sample. A source count uses sources; sample count uses samples; alert count uses alerts. Never mix these units silently.

### KPI cards

Use four cards: Active sources, Samples in period, Profile violations, Unresolved alerts. Each has a small label, a large persisted value, and a plain-language scope note. Add a separate compact Incomplete evidence count near evaluation results. Cards are not decorated with large colored icon bubbles.

Only add trend percentages if calculated from actual comparable periods. Otherwise show `No previous-period comparison`. Do not animate random counters or hardcode impressive values. Empty database values must be zero or an honest not-applicable state.

### Trend card

Title `Parameter trend`. Include source, parameter, canonical unit, record kind, and date range. Use JavaFX LineChart for exact point observations. Default to one selected parameter per chart. The mockup's general multi-series appearance is not permission to plot pH and turbidity on a shared unlabeled axis. For multiple parameters, use parameter tabs or separate small charts with their own units.

Display a relevant lower/upper bound as labelled neutral dashed lines where practical. A bound must belong to the selected profile/version and match the source context/unit. If custom chart overlays become complex, use a clear bound legend and result table rather than misaligned graphics. Missing readings create gaps or omissions with a note, not zeros or interpolated measurements. Tooltips show source, timestamp, original decimal value/unit, and origin.

Historical period summaries use a separate view with MIN/MEAN/MAX selection and explicit period labels, never a point-sample simulation. Downsample only with a visible explanation, retaining full report data.

### Evaluation detail panel

Default empty state: `Select an evaluated sample to inspect its rules.` Selecting a recent sample updates the panel. Show sample code, source, collection time, selected profile/version, provenance badge, and headline status. Follow with evaluated parameters, observed values/units, applicable limits, pass/fail/missing text, and a compact required-evidence summary.

Actions are contextual: `Open sample`, `View alert`, or `Create action` when authorized. Do not use `Approve water safety` or `Certify safe` buttons.

### Recent samples table

Columns: Sample code, Source, Collected, Workflow state, Evaluation, Origin, action. Use 44–48 px rows, right-aligned numerical cells where present, quiet horizontal dividers, clear header labels, and deterministic ordering. Display the first page with 10 rows on the dashboard and a `View all samples` link; the full samples screen uses 50-row pages. Never show a single badge that confuses lifecycle state with quality outcome.

## 6. Status language

| Internal outcome | Visible label | Appearance |
|---|---|---|
| WITHIN_PROFILE | Within selected profile | Green text/badge + check icon |
| EXCEEDS_PROFILE | Profile violation | Red text/badge + warning icon |
| INCOMPLETE | Missing required readings | Amber text/badge + evidence icon |
| NOT_EVALUABLE | Not evaluated: context mismatch | Slate badge + explanation |
| No evaluation | Not evaluated | Neutral badge |

If a failed evaluation also has missing required data, show `Profile violation` and a second missing-evidence indicator. Passing listed parameters must never be presented as a universal drinking-water safety claim. Demo profiles and fixtures require a visible `Educational demo` badge and report disclaimer.

Source-level status is not automatically a scientific declaration about the entire source. Prefer `Latest evaluated sample` plus date/profile rather than `Source is safe`.

## 7. Screen-specific patterns

### Water sources

Searchable table with code, name, type, intended use, location, active/archive status. Detail screen has overview, inspections, samples, and trends tabs. Optional coordinates appear as text; no mandatory embedded web map. Source creation form validates fields inline. Archive uses a confirmation explaining history is preserved.

### Inspections

Table with source, assigned officer, due date, state, sample count. Detail view shows schedule, notes, collection events, and related samples. Only valid state-transition actions appear. Completing without a sample requires a clearly labelled reason.

### Sample detail and entry

A full-width detail screen with a header and textual lifecycle stepper. Tabs: Overview, Readings, Evaluation, History. Use a short stepwise form: (1) inspection/source context, (2) collection/receipt metadata, (3) readings and review. Reading grid shows parameter, decimal value, unit, and validation message. Submission confirmation explains readings will lock; corrections require a revision.

Place evaluation results next to or below the reading grid, not in an unrelated popup. Evaluation failures must explain the rule, not merely show a red total score. Preserve scroll position after validation.

### Import wizard

Four steps: Select file and provenance → Map/validate → Preview → Commit/result. Show actual record count, point-vs-summary counts, parser errors, duplicate warnings, and per-row messages. Disable commit when blocking errors exist. The result includes persisted counts and a link to imported records. Progress is indeterminate until measurable; cancellation is allowed before commit. No fake progress percentages.

### Alerts and actions

Default to an actionable list, not a complex drag-and-drop board. Filters: state, assignee, due date, source. Detail includes the triggering evaluation, linked violations, notes, actions, and retest selector. The closure control explains missing requirements and cannot close without the required compatible passing follow-up.

### Profiles and administration

Profile list shows name, version, intended use, publication state, and DEMO_ONLY/REFERENCED classification. Published profiles show read-only rules and a `Create new version` action. Use clear field labels for bounds, units, inclusion flags, and required parameters. User management and audit browser are admin-only. Avoid hiding crucial provenance in tooltips.

### Reports

Filter builder, snapshot summary, then explicit `Export PDF` and `Export CSV` controls. Show output location and open-folder action only after a successful write. PDFs use a matching white/slate/teal visual style but optimize for printing: repeated table headers, page numbers, readable units, date/profile/version, missing evidence, provenance and disclaimer.

## 8. Shared control and interaction rules

- One visually dominant primary action per screen section.
- Button variants: primary teal, secondary neutral outline, tertiary text, destructive red only when needed.
- Text fields: visible labels, optional helper text, inline errors; placeholders never replace labels.
- Do not show green success styling merely because a field contains a value.
- Selection, hover, focus, disabled and loading states must be distinct.
- Show success/error inline near the relevant operation; optional transient notices only supplement important feedback.
- Modals require a title, clear cancel/dismiss action and keyboard Escape where safe; preserve unsaved input or confirm discard.
- Use a bundled consistent icon set with license attribution, or simple JavaFX vector shapes. No emoji navigation icons.
- Functional transitions may use 120–180 ms fades. Avoid bouncy rows, continuous pulsing alerts and chart animations that distract from interpretation.
- UI changes occur on the JavaFX application thread; imports, database I/O, evaluation orchestration and reports use the architecture's bounded background executors.

## 9. Loading, empty, error and offline states

Loading: show an appropriately placed ProgressIndicator and operation text; retain stable layout and disable duplicate submissions. Empty dashboard: explain how to add a source/import records; do not populate invented statistics. Empty chart: `No matching observations for these filters` with a clear-filter action. Failed operation: concise explanation, retry when safe, and correlation ID for troubleshooting, without raw stack traces.

The local database works without an internet connection; an internet-off message must not imply local data is unavailable. Real-data discovery/download is optional. Indicate freshness honestly using stored observation/import times, not “Live” badges.

## 10. Accessibility and display scaling

Support keyboard navigation, visible focus, logical tab order, labelled inputs, accessible text for icon-only controls, and escape from dialogs. Aim for at least 4.5:1 contrast for normal text and 3:1 for large text; verify actual combinations. Table status must remain understandable in grayscale. Do not make tooltips the only place to find errors, unit labels, or rule explanations.

Use JavaFX layout containers and preferred/minimum sizes rather than hardcoded absolute coordinates. Ensure text wraps where appropriate and tooltips supplement truncated table text. Test at 1100×720, 1280×800, and 1440×900, including OS scaling. Light mode is the P0 design; dark mode is optional P1 and must not delay functional delivery.

## 11. JavaFX implementation guidance

Create `app.css`, `components.css`, and screen-specific CSS only when needed. Define reusable JavaFX looked-up colors on the root, such as `-aq-bg`, `-aq-surface`, `-aq-text`, `-aq-muted`, and `-aq-primary`. JavaFX CSS is not browser CSS: do not paste Tailwind, CSS Grid, `var()`, or unsupported web-only properties. Reuse style classes such as `.kpi-card`, `.section-card`, `.primary-button`, `.status-chip`, `.metadata-label`, and `.sidebar-item`.

Use BorderPane for shell; VBox/HBox/GridPane for content; StackPane for overlays; TableView with cell factories for badges/actions; LineChart for trends. Inject services through the existing controller factory. Controllers receive real DTOs and never call SQL directly. View previews can use labelled fixture DTOs but production wiring must query services. Fonts, icons and styles load from classpath resources.

Suggested FXML files: `app-shell.fxml`, `dashboard.fxml`, `sources.fxml`, `source-detail.fxml`, `inspections.fxml`, `sample-detail.fxml`, `sample-editor.fxml`, `import-wizard.fxml`, `alerts-actions.fxml`, `reports.fxml`, `profiles.fxml`, `users.fxml`, `audit.fxml`, and `login.fxml`.

## 12. Build order for Antigravity

1. Read this file together with the PRD and architecture; retain JavaFX/SQLite and all permission rules.
2. Implement shell, tokens, font loading, button/input/table styles, and dashboard layout using explicitly labelled fixture DTOs.
3. Validate layout at the target sizes and OS scaling before duplicating components across screens.
4. Connect KPI cards, sample selection, trend filters, and evaluation detail to real service DTOs; remove static production values.
5. Implement the inspection/sample workflow screens before adding secondary charts or decoration.
6. Implement import preview/errors and action/retest screens with all failure states.
7. Finish report styling, keyboard flow, empty states, and restrained motion.
8. Capture real running-app screenshots for GitHub. Do not use the AI concept image as evidence that the UI has been implemented.

## 13. Visual acceptance checklist

- [ ] Main layout resembles the approved dashboard direction: sidebar, header, KPI row, trend and context panel, recent samples.
- [ ] Teal is restrained; surfaces are mostly white/slate; labels remain legible on a projector.
- [ ] No clipped controls at supported sizes or 125%/150% scaling.
- [ ] No browser-wrapper substitute or screenshot-only interface.
- [ ] Every visible action is functional or explicitly unavailable with a reason.
- [ ] Counts, plots, rows and alerts are derived from persisted data.
- [ ] Charts show parameter, unit, source, time range and provenance.
- [ ] pH and turbidity are not plotted on one misleading unlabeled scale.
- [ ] Profile/version and missing evidence remain visible with results.
- [ ] Fictional data is labelled; no “safe to drink,” fabricated live data, or unsupported certification claims.
- [ ] All screens support loading, empty, error and permission states.
- [ ] Actual screenshots and a five-minute demo are reviewed before final delivery.
