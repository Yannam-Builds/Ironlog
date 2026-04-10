# IRONLOG Android Deep Audit Report (Post 1.1.0-beta)

## Part A - Evidence Table

| Feature | Status | Evidence | Where found | Roadmap decision | Missing delta |
| --- | --- | --- | --- | --- | --- |
| Workout logging + rest timer | EXISTING | Full active session flow is implemented. | `src/screens/ActiveWorkoutScreen.js` | Remove from upcoming. | None |
| Plans/program picker | EXISTING | Program selection and plan day routing are implemented. | `src/screens/PlansScreen.js`, `src/screens/ProgramPickerScreen.js` | Remove from upcoming. | None |
| Volume analytics | EXISTING | Timeframe analytics, breakdowns, and insights are implemented. | `src/screens/VolumeAnalyticsScreen.js` | Remove from upcoming. | None |
| Recovery heatmap | EXISTING | Front/back map and multi-window filtering are implemented. | `src/screens/RecoveryMapScreen.js` | Remove from upcoming. | None |
| PR/trend dashboards | EXISTING | E1RM/load/reps/volume/consistency/history tabs are implemented. | `src/screens/ExerciseProgressScreen.js` | Remove from upcoming. | None |
| Program intelligence | PARTIAL | Recommendations and guidance are implemented, but true schedule reshaping is limited. | `src/domain/intelligence/programIntelligenceEngine.js`, `src/screens/ProgramInsightsScreen.js` | Keep only missing adherence delta. | Busy-week compression + fatigue-aware reshuffle |
| Notifications policy engine | EXISTING | Profiles, quiet hours, cooldowns, snooze, and decision logging exist. | `src/services/notificationScheduler.js`, `src/screens/SettingsScreen.js` | Remove from upcoming. | None |
| Restore wizard + SQLite import/export | PARTIAL | Foundation exists, but parity for all app-state domains is incomplete. | `src/screens/RestoreDataScreen.js`, `src/services/sqliteExportImport.js` | Keep parity closure only. | Include all 1.1.0-beta state in versioned contract |
| In-workout set edit/delete + add exercise | EXISTING | Both capabilities are present in active workout flow. | `src/components/SetRow.js`, `src/screens/ActiveWorkoutScreen.js` | Remove from upcoming. | None |
| Exercise block management | PARTIAL | Reorder exists; collapse/duplicate/remove-with-confirmation are not complete as a set. | `src/screens/ActiveWorkoutScreen.js` | Keep as narrow UX delta only. | Add missing block controls |
| OpenWeight interop | PENDING | No OpenWeight import/export layer exists yet. | repo-wide audit | Keep and prioritize. | Implement OpenWeight portability layer |
| Canonical alias mapping for imports | PENDING | No durable alias system across imports and exercise identity. | `src/services/CSVImport.js`, `src/services/ExerciseLibraryService.js` | Keep and prioritize with Import Center. | Canonical ID + alias tables |
| Play Protect release hardening | PARTIAL | Risk reductions are being applied but must be codified in roadmap and release standards. | `app.json`, `android/app/src/main/AndroidManifest.xml`, `android/app/build.gradle` | Keep in next phase acceptance criteria. | Enforce non-debug signing and permission hygiene for production builds |

## Part B - Messaging Inconsistencies

| Current wording problem | Why wrong | Corrected direction | File |
| --- | --- | --- | --- |
| "IRONLOG 2.0 - Upcoming Improvements" listed many shipped systems as future | Contradicts app state and beta screenshots. | Replace with truth map: `1.0`, `1.1.0-beta`, `pending`. | `README.md` |
| Beta release source was too thin | Undersold real shipped beta scope. | Rewrite as accurate shipped-beta summary with trust notes. | `.github/release-1.1.0-beta.md` |
| 2.0 docs read like untouched future plan | Mixed shipped and pending states. | Reframe to pending-only roadmap with status notes. | `docs/ironlog-2.0/*.md` |
| Feature gallery lacked explicit version framing | Could be mistaken as mock roadmap rather than shipped UI. | Add shipped-state note. | `features/README.md` |

## Part C - Clean Pending Roadmap Tiers

Tier 1 (must-build):
- OpenWeight interoperability + Import Center + import parity closure
- release trust standards (Play Protect hygiene and release signing)
- backup/restore parity completion for shipped state domains

Tier 2 (strong multipliers):
- coach-grade program builder depth
- adherence v2 with real reschedule/compression behavior

Tier 3 (retention and differentiation):
- explainability/confidence layer
- canonical exercise identity and alias reuse
- onboarding fast-path for switchers

Tier 4 (later/optional):
- Health Connect integration
- advanced coach utility exports
- no social feed and no non-training bloat
