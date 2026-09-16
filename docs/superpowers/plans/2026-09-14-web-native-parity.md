# Browser-capable Kotlin parity implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking. Do not dispatch agents unless subsequently authorized. This document records future work; the current user request is audit and planning only.

**Goal:** Reproduce all browser-capable IronLog Kotlin workflows, data behavior, controls, assets, animations and transitions, preserving existing data and excluding Android-only APIs.

**Architecture:** Keep React/Vite, the current Dexie transaction boundary and validated backup codecs. Split a large feature file only when adding its concrete native destination; share domain calculations instead of reproducing them per screen. Port tested Kotlin rules and measured presentation contracts, retaining semantic HTML, keyboard access and reduced motion.

**Tech Stack:** React 19, TypeScript, Vite, Dexie/IndexedDB, Zod, Vitest, Playwright, CSS/SVG/Canvas as required by existing native visuals. Kotlin Compose is the reference, not a browser runtime to deploy.

## Baseline and operating rules

- Implementation tree: `Z:/KOTLIN/IronLogWeb`; web root: `Z:/KOTLIN/IronLogWeb/web`. Initial HEAD `511c232`; clean before planning.
- Native reference: `Z:/KOTLIN/UnifiedPort/app/src/main/java/com/ironlog/app`. This checkout is dirty at HEAD `861674b`; use the inventory hashes and current source, not an assumed clean commit. Never merge the old Android branch wholesale or overwrite its work.
- Publication checkout: `Z:/KOTLIN/_github_publish_ironlog`, initially clean at `511c232`. This audit does not deploy or edit that checkout.
- [Audit and scope matrix](../../../web/docs/parity-2026-09-14/audit.md) defines P00–P17. SHA-256 inventories alongside it pin source inputs. All native references below are relative to the native reference directory; all web references are relative to the web root unless stated otherwise.
- Current user scope: plan now; implementation later. Preserve every existing user-data contract, theme/font choice, deliberate QR removal and local-first default. No new account, sync, subscription, speculative model, or silent external request.
- Never label an existing capability missing merely because its exact visuals remain unverified. No blanket rewrite and no gratuitous dependency upgrades.

## Sequence and checkpoints

Execute P00 → P01 → P02/P03 → P04/P05 → P06/P07 → P08/P09 → P10/P11/P12 → P13/P14 → P15 → P16 → P17. The slash indicates either can follow the other, not permission to spawn agents. Move schema/codec work from P15 into the feature slice that first needs its field; P15 is final cross-feature closure. P08's date-policy regression can be brought forward immediately after P01 because current history labels use completion time while documented native statistics use start time.

For each slice: inspect the named authority, record observed differences, write meaningful behavior regressions for data/state changes, implement only the confirmed delta, run focused checks, capture matching native/web states, and update the audit row. Commit only that slice's reviewed files after validation. No implementation checkboxes are complete yet.

### P00 — Pin the reference and complete the contract ledger

Files: read `navigation/AppNavigator.kt`, every reachable `ui/screens` destination and referenced shared component; read `web/src/App.tsx`, feature files and `ui/components.tsx`. Create `web/docs/parity-2026-09-14/contracts.csv`, `motion.csv`, `settings.csv` and `evidence.md`.

- [ ] Resolve the six normalized source differences listed in the audit. Record which behavior is authoritative for each; retain region-local recovery and existing web recent-performance/rest fixes. Do not mechanically synchronize the older Android copy.
- [ ] Record reference APK build/source identity and synthetic seed identity before screenshots. If no matching installed reference is available, keep visual comparison pending and continue source/data work.
- [ ] Enumerate reachable screens, actions, menus, sheets, empty/loading/error states and dismiss paths from navigator and call sites. For each write: native symbol/path, web destination, input, output consumers, mutation owner, persistence, refresh triggers, lifecycle, settings dependencies, status, test and evidence path.
- [ ] Enumerate settings keys with defaults, UI input, actual consumers, reload behavior and portability. Enumerate animations with trigger, initial/final values, duration/spring parameters, easing, interruption/reversal and reduced-motion equivalent.
- [ ] Reconcile each ledger row to P01–P17 or an explicit Android-only exclusion. An unmapped reachable action blocks completion.

Acceptance: 254-file source manifest remains reproducible; every reachable native surface has a ledger disposition. This is a source census, not a screenshot acceptance claim.

### P01 — Navigation, pager, back and shared surfaces

Files: modify `src/App.tsx`, `src/ui/context.tsx`, `src/ui/components.tsx`, `src/styles.css`; create `src/ui/navigation.ts`, `tests/navigation-parity.test.tsx`, `tests/browser/navigation-parity.spec.ts`. Native: navigator/tab bar and shared sheet/dialog components.

- [ ] Define route parents and actual navigation history for plan, history, stats/body, recovery, intelligence and settings destinations. Preserve browser Back/Forward and refreshable hashes, returning to the originating list position.
- [ ] Preserve transient drafts or present native discard confirmation on navigation; restore focus to the invoking control after a sheet closes.
- [ ] Compare continuous native pager drag with the existing translate/fade implementation. Add adjacent-page presentation and native settle/interruption behavior where reference evidence differs; preserve one-tab-per-swipe and boundary resistance.
- [ ] Verify vertical scrolling, horizontal day carousels, sliders, photo gestures and drag handles own their gestures without accidental tab changes. Check pointercancel and release-click suppression.
- [ ] Test Home→Plans→editor→Back, Stats→Body→Back, direct detail refresh, browser Back/Forward and interrupted swipe. Capture open/closing/interrupted sheet states with keyboard visible.

Acceptance: no incorrectly forced Home navigation, lost saved work, blank route, trapped focus or accidental gesture activation.

### P02 — Full onboarding and calibration

Files: modify `src/features/Onboarding.tsx`, `src/domain/onboarding-baseline.ts`, `src/domain/types.ts`, `src/data/schema.ts`, `src/data/store.ts`, `src/domain/codecs.ts`; create `src/features/onboarding/OnboardingSteps.tsx`, `src/features/onboarding/OnboardingWheel.tsx`, `tests/onboarding-flow.test.tsx`, `tests/browser/onboarding-parity.spec.ts`. Extend `tests/onboarding-baseline.test.tsx`. Native: `OnboardingScreen.kt`, `onboarding/*`, `BaselineCalibrationEngine.kt`, `data/repository/OnboardingBaselinePersistence.kt`.

- [ ] Map the ten native pages and actual draft fields: welcome/skip, registration, performance/history baseline, classification, weekday schedule/units, goal mode, coaching, browser-capability permissions, calibration preview, starter plan.
- [ ] Extend validated profile/calibration storage with migration defaults. Keep immutable onboarding bodyweight separate from later measurements and retain stable baseline timestamp/provenance.
- [ ] Port the current calibration equations and input gating from Kotlin; use synthetic no-history, gym/no-gym and missing-performance cases. Verify fixed baseline identity, 20-XP/session policy and trust application against native expected values.
- [ ] Implement draft persistence and atomic final plan/profile/baseline commit. Re-entry/retry must not duplicate plans, move baseline time or overwrite later measurements.
- [ ] Reproduce wheel visual looping, snapping, modal layout and page transitions while exposing bounded accessible values. Preserve removed decorative blobs.
- [ ] Test skip→Level 1/0 XP, full completion, reload each stage, back edits, final write failure/retry, old four-step migration and lower-calibration removal of provisional-only grants.

Acceptance: matching Kotlin synthetic profiles yield the same baseline values and supported awards; no invented workout rows; supported browser steps visually match.

### P03 — Exercise library, custom metadata and resolution

Files: modify `src/features/Plans.tsx`, `src/domain/types.ts`, `src/domain/codecs.ts`, `src/data/store.ts`; create `src/features/exercises/ExerciseLibrary.tsx`, `src/features/exercises/ExerciseEditor.tsx`, `src/domain/exercise-resolution.ts`, `tests/exercise-resolution.test.ts`, `tests/exercise-library.test.tsx`. Native: settings library/create screens, `domain/ai/ExerciseResolutionEngine.kt`, tracking normalizer and repository metadata.

- [ ] Extract the current picker into a reusable picker/library boundary; implement native search/filter/detail and custom-edit controls, including all supported tracking types and primary/secondary contribution metadata.
- [ ] Port deterministic exact-ID/name/alias resolution before fuzzy ranking. Show candidate confidence/review choices and keep unresolved rows visible; never silently assign guessed muscles.
- [ ] Preserve historical/session snapshots when editing a library entry; disallow unsafe tracking changes over incompatible logged values or provide explicit reviewed conversion as native permits.
- [ ] Test duplicate names/different IDs, aliases, missing/custom metadata, fuzzy ties, assisted/added-load/timed/distance modes and reload/import of edits.

Acceptance: native fixture resolution decisions match; all typed modes can be created and selected; existing logs remain unchanged.

### P04 — Plans, program picker and editor controls

Files: modify `src/features/Plans.tsx`, `src/domain/plans.ts`, `src/data/store.ts`; create `src/features/plans/ProgramPicker.tsx`, `src/features/plans/PlanEditor.tsx`, `tests/plan-editor-parity.test.tsx`, `tests/browser/plan-editor-parity.spec.ts`. Native: `plans/PlansScreen.kt`, `PlanEditorScreen.kt`, `ProgramPickerScreen.kt`, ordering/move helpers.

- [ ] Retain current plan-list drag/top activation and duplicate/JSON actions. Add native program filtering/details and selection states.
- [ ] Reproduce day/exercise selection, reorder and supported cross-day move actions using stable IDs and keyboard alternatives. Do not change meaning of existing saved IDs or warm-up flags.
- [ ] Add native note visibility and separately confirmed delete-all-notes behavior; scope plan edits to the plan, preserving history. Match supported policy/goal override fields.
- [ ] Add unsaved-close confirmation and conflict-aware save/reload. Test long names, empty days, reorder after removal, duplicate then edit, import notes and stale second-tab save.

Acceptance: imported descriptions/exercise/set notes and supersets survive editing and workout creation; native action coverage is complete and gesture results persist.

### P05 — Manual AI plan creation/import flow

Files: modify `src/features/Plans.tsx`, `src/domain/manual-prompt.ts`, `src/domain/codecs.ts`; create `src/features/plans/AIPlan.tsx`, `tests/ai-plan-flow.test.tsx`. Native: `plans/AIPlanScreen.kt` and `AIPlanGymProfile.kt`. Depends on P03/P04.

- [ ] Implement native INTRO→QUIZ→PROMPT/LIBRARY→PASTE→PREVIEW stages, preserving draft on back and validated selected gym context.
- [ ] Include supported goal, schedule, equipment, time and limitation fields in a reviewed manual prompt. Keep template generation clearly distinct from an actual AI response.
- [ ] Route pasted responses through existing canonical/legacy plan codecs and P03 review. Preview exercises, unknowns, notes, supersets and intended save scope before import.
- [ ] Test invalid JSON, unknown exercises, malformed relationships, pasted instruction-like text, duplicate import retry and successful editable plan persistence.

Acceptance: manual external-AI workflow works without network calls or keys; no response is executed as code or silently saved.

### P06 — Built-in program intelligence and progression

Files: modify `src/features/Intelligence.tsx`, `src/domain/engine.ts`, `src/domain/types.ts`; create `src/domain/program-intelligence.ts`, `src/features/plans/ProgramInsights.tsx`, `tests/program-intelligence.test.ts`, `tests/program-insights.test.tsx`. Native: ProgramInsights, TrainingIntelligence, recommendation/volume engines and actual progression-policy call sites.

- [ ] Port actual exercise→plan→global→fallback policy precedence and persist missing policy fields. Separate native rules from newer product proposals in old briefs.
- [ ] Derive completed-work adherence, typed performance trends, planned/effective volume, deload/plateau states and recommended next day from one shared snapshot and injected clock.
- [ ] Show evidence, units, comparable setup, insufficient-history state, pain exclusions and applicable equipment increments. Never automatically rewrite plans.
- [ ] Build native charts/cards and recommendation actions. Test warmups, short valid sessions, assisted/cardio modes, missing effort, one bad session, changed gym and incomplete current week.

Acceptance: fixed-clock native/web fixtures agree for recommendations and policy sources; built-in mode never displays or invokes cloud-only features.

### P07 — Optional cloud AI, stats summary and debrief

Files: modify `src/features/Intelligence.tsx`, `src/features/Settings.tsx`, `src/features/Workout.tsx`, `src/features/Progress.tsx`; create `src/domain/cloud-ai.ts`, `src/features/intelligence/CloudSettings.tsx`, `src/features/intelligence/CloudResult.tsx`, `tests/cloud-ai.test.ts`, `tests/cloud-mode-gating.test.tsx`. Native: CloudAiEngine/Card, CloudStatsSummaryHost, WorkoutCloudDebrief. Depends on P05/P06.

- [ ] Before choosing a transport, verify current official provider documentation for browser CORS and key handling. Record provider-by-provider support; do not assume every native custom endpoint permits browser calls.
- [ ] Implement provider/model/base-URL configuration and explicit user-triggered requests. Keep keys out of bundles, URLs, logs, backups and test screenshots. Default to session-only credentials; explain any optional local retention honestly.
- [ ] Prefer browser direct requests where supported. For unsupported CORS retain the manual workflow until a narrowly scoped authenticated transport is designed; never create an unrestricted arbitrary-URL credential proxy.
- [ ] Implement cancellation, timeout, provider error, invalid schema and stale-result handling. Minimize context; do not include photos or unrelated history. Native mode gating must cover onboarding, plan builder, Stats and completion.
- [ ] Add optional summary/debrief presentation only when configured and requested. Test built-in mode makes zero requests, provider switching isolates credentials, cancelled results cannot overwrite newer state, and malformed responses cannot mutate saved plans.

Acceptance: supported cloud workflows function with synthetic requests; unsupported providers have a truthful manual equivalent and explicit remaining status, not a fabricated success. Gemini Nano Android binding is excluded.

### P08 — Calendar and historical workout creation/editing

Files: modify `src/features/Progress.tsx`, `src/App.tsx`, `src/data/store.ts`, `src/domain/dates.ts`; create `src/features/history/WorkoutCalendar.tsx`, `src/features/history/HistoricalWorkoutEditor.tsx`, `src/domain/historical-workout.ts`, `tests/historical-workout.test.ts`, `tests/browser/history-parity.spec.ts`. Native: `history/*`, `workout/WorkoutCalendarScreen.kt`, `stats/HistoryScreen.kt`.

- [ ] Replace Log past workout→Home and Calendar→Log substitutions with actual destinations.
- [ ] Implement native calendar month/day selection, date filtering and existing-workout navigation. Align list labels/filter clock with the native start-date policy after tracing its caller.
- [ ] Implement historical name/date/time/duration/rating/notes, exercises and typed sets. Resolve DST gaps and repeated-hour offset choice; reject future/invalid chronology without changing existing data.
- [ ] Commit directly to completed history transactionally, with same-date conflict review and revision checks. Do not start a live workout, rest timer or completion celebration.
- [ ] Test Europe/Warsaw DST gap/fold, midnight crossing, same-date duplicate confirmation, past entry during an active workout according to native policy, edit/delete recomputation and failed-save draft retention.

Acceptance: entry appears on the correct day and updates Home/Stats/recovery/Ledger consistently; existing active workout is preserved.

### P09 — Stats, exercise progress and volume analytics

Files: modify `src/features/Progress.tsx`, `src/App.tsx`; create `src/features/stats/ExerciseProgress.tsx`, `src/features/stats/VolumeAnalytics.tsx`, `src/domain/analytics.ts`, `tests/analytics-parity.test.ts`, `tests/browser/stats-parity.spec.ts`. Native: StatsScreen, ExerciseProgressScreen and VolumeAnalyticsScreen.

- [ ] Split combined analytics into native destinations with stable exercise identity, native range/metric tabs and session drilldowns.
- [ ] Port typed trend aggregation, training max, PR markers, effective sets, weekly volume/consistency, muscle balance/radar and previous-period comparison. Reuse canonical tracking/date rules.
- [ ] Reproduce native axes, legends, metric formatting, selection/highlight and chart entrances; provide accessible equivalent values.
- [ ] Test same-name distinct IDs, missing data, all-zero range, kg/lb, assisted/time/distance metrics, future rows, partial weeks and imported start/completion differences.

Acceptance: fixture chart values and native metric labels match; unsupported dimensions are never displayed as lifting volume.

### P10 — Bodyweight and measurements

Files: modify `src/features/Progress.tsx`, `src/domain/types.ts`, `src/domain/codecs.ts`; create `src/features/body/BodyWeight.tsx`, `src/features/body/BodyMeasurements.tsx`, `src/domain/body-analytics.ts`, `tests/body-analytics.test.ts`, `tests/browser/body-parity.spec.ts`. Native: both body screens, bodyweight analytics and current-bodyweight repository.

- [ ] Implement separate native cards/screens, weight and measurement goals, ranges, moving average, deltas, chart dialog and summary presentation.
- [ ] Persist goals and native-compatible measurement fields; current weight must derive from canonical measurement data without rewriting immutable onboarding baseline.
- [ ] Add progress share/download equivalent with native content and composition where supported.
- [ ] Test sparse dates, duplicate dates, ±window matching, unit conversion, goal change, edit/delete, missing height and restore. Compare native expected trend values.

Acceptance: measurement history and goals survive reload/backup; native chart and field interactions are available.

### P11 — Progress photo calendar, viewer and comparison

Files: replace Photos implementation in `src/features/Progress.tsx` with `src/features/body/ProgressPhotos.tsx`; create `src/features/body/PhotoViewer.tsx`, `src/domain/photo-selection.ts`, `tests/photo-selection.test.ts`, `tests/browser/photos-parity.spec.ts`; extend store/codecs only for required notes/metadata. Native: photo screen and compare/viewer helpers.

- [x] Add date calendar, Filter day/Compare dates, deterministic before/after selection and native empty/missing-image presentation.
- [x] Add full-screen viewer, actual native supported zoom/pan/compare controls, editable notes and unsaved dismissal guard. Do not invent gestures absent from the pinned native version.
- [x] Add camera/file picker equivalents and native share/latest, export-all and clear-all flows with confirmation and download fallback. Release object URLs and preserve photo bytes in ZIP.
- [x] Test same-day photos, selection after deletion, notes reload, pinch vs tab swipe, cancelled picker, oversized/invalid file and backup restored bytes. The pinned native viewer has no pinch gesture, so the regression asserts the comparison slider and absence of the removed web-only zoom control.

Acceptance: viewer/compare behavior and exported content match native intent; photo URIs are never treated as transferable bytes.

### P12 — Recovery, Ledger and circuit detail parity

Files: modify `src/features/Recovery.tsx`, `src/ui/BodyMap.tsx`, `src/domain/engine.ts`, `src/domain/badges.ts`, store/types/codecs; create `tests/recovery-evidence.test.ts`, `tests/browser/recovery-ledger-parity.spec.ts`. Native: RecoveryMap/Heatmap/Circuit, region presentation and StatusWindow.

- [x] Add recovery ranges/trend, last-trained/contributing exercises/source evidence and native next-actions; keep estimates and unknown coverage honest.
- [x] Extend manual check-in with native notes/region selection and score controls; pain must remain visible across all recommendations.
- [x] Compare native Ledger sections, stat/badge detail, baseline-vs-proof presentation and circuit stages; fill actual gaps while preserving durable unlock semantics.
- [x] Verify body-map draw/hit transforms with front/back, narrow landscape, large type and region boundaries. Never use visual nudges to compensate for coordinate errors.
- [x] Test expired check-in, no history, short workload without reward, baseline overlap, single eligible circuit/week, concurrent save and restored unlock timestamps.

Acceptance: all consumers agree at a fixed clock; every displayed body region opens its corresponding evidence; existing circuit and badge work remains intact.

### P13 — Remaining workout controls and motion

Files: modify `src/features/Workout.tsx`, `src/features/RecentPerformance.tsx`, `src/features/ExerciseNextNote.tsx`, `src/styles.css`; extend `tests/motion-parity.test.tsx`, `tests/tracking-ui.test.tsx`, `tests/browser/app.spec.ts`; create `tests/browser/workout-control-parity.spec.ts`. Native: current ActiveWorkoutScreen, recent-performance/rest controls and viewport/input anchors.

- [ ] Compare actual remaining controls against the P00 ledger: native superset interaction, note hide/show and confirmed delete-all, video action, target/policy selection, set clear/edit, rest choice and completion timing.
- [ ] Retain already implemented targets, warm-up queue, effort/type chips, scoped swap, rest deadline, drag and burst; change only observed differences.
- [ ] Keep session-only changes separate from plan mutation. Protect immutable logged metadata when swapping/tracking changes; preserve accepted write order and visible failure recovery.
- [ ] Match focused-input/keyboard anchoring, native drag initiation/settling, large list autoscroll, timer/control entrance and cancelled interactions.
- [ ] Test quick consecutive sets, clear effort, hide vs delete notes, both swap scopes, warmup log/skip, rest extension/resume, finish retry, stale callbacks and reload. Visually compare with keyboard and long exercise names.

Acceptance: fast logging does not jump the list, lose edits, double-finish or affect another session; exact-motion claims require recorded matching states.

### P14 — Settings destinations, appearance controls and tools

Files: modify `src/features/Settings.tsx`, `src/App.tsx`, types/store/codecs and appearance modules; create `src/features/settings/SettingsPages.tsx`, `src/features/settings/GymProfiles.tsx`, `tests/settings-parity.test.tsx`, `tests/browser/settings-parity.spec.ts`. Native: SettingsConsoleModel, SettingsScreen, GymProfiles/Editor, appearance cards, About/tutorial paths.

- [ ] Create focused native Training, Intelligence, Appearance, adapted Notifications, Data & Privacy and About destinations; use native multi-term keyword matching and correct Back behavior.
- [ ] Implement every browser-capable P00 settings row with real consumers, defaults, reload and portability. Include independent shine/glass/motion controls and native tutorial replay semantics.
- [ ] Replace comma-only gym editing with native controls where applicable; allow editing saved profiles and active identity while retaining finite plate quantities and odd-spare handling.
- [ ] Implement scoped clear-history and reset-PR behavior from native mutation policy with explicit confirmation; preserve plans/photos/baseline/durable awards as the authority specifies.
- [ ] Test settings search, each toggle's effect and restart, gym edit/use/delete, destructive cancel and independent effect settings. Excluded OS settings must not look functional.

Acceptance: no setting exists solely as a saved value without a consumer; each destination and browser-capable action is mapped and tested.

### P15 — Cross-feature persistence and portability closure

Files: modify `src/domain/codecs.ts`, `src/data/schema.ts`, `src/data/store.ts`, `src/domain/types.ts`; extend `tests/codecs.test.ts`, `tests/data.test.ts`, `tests/plate-codecs.test.ts`, `tests/browser/storage.spec.ts`; create `web/docs/parity-2026-09-14/portability.csv`. Native: ImportExportRepository, validation and calibration/ledger persistence.

- [ ] Classify each native field/table/setting: portable, derived, preserved opaque metadata, intentionally excluded, or unrepresentable photo URI. Cover baseline, calibration, earned event provenance, goals, check-in notes, gym identity and all new slice fields.
- [ ] Implement missing canonical/legacy importer formats actually reachable in native, including native CSV/manual flows where applicable. Add reviewed append/merge as well as replace when supported; show conflicts before mutation.
- [ ] Preserve canonical Android rows over stale extensions, validate all relations/numeric/time bounds before writes, and commit atomically. Retain original backups and report unsupported bytes/fields honestly.
- [ ] Exercise retry/import twice, merge ID conflicts, old schema migration, baseline overlap, newer earned badges, partial backup, corrupt archive and rollback over populated storage.
- [ ] Close failure-state lifecycle gaps: a failed accepted workout change must not be silently forgotten by reload and then followed by successful completion. Design durable failure/draft acknowledgment without blindly replaying uncommitted mutations.

Acceptance: native→web→web backup→restore has expected canonical equality; Android roundtrip preserves supported fields without overclaiming extension retention by Android.

### P16 — Complete visual, control and motion comparison

Files: modify only affected feature/UI/CSS files and extraction scripts after evidence; create `tests/browser/visual-parity.spec.ts`; maintain `web/docs/parity-2026-09-14/motion.csv` and `evidence.md`. Native: all P00 reachable components and artwork/theme definitions.

- [ ] Use identical synthetic data, viewport, theme, font and scale in native/web captures. Cover every primary/detail route and every shared overlay/state class; compare functional equivalents for OS chrome.
- [ ] Verify all icon references, artwork bounds, palette roles, fonts, spacing, hit targets, controls, sheets, keyboard insets and scrims. All-assets-exported does not imply every UI usage is correct.
- [ ] Measure native animation trigger/trajectory/duration and interruption against web. Cover Today card shine, plan/day/workout drag, pager, wheel, tabs, expansion, counters, badges, completion and modal dismiss.
- [ ] Test default/disabled/pressed/selected/loading/error states; independent shine/glass settings; reduced motion; keyboard and screen-reader alternatives.
- [ ] Record each difference with expected/actual and disposition. Do not mark approximate CSS springs exact unless comparison supports it.

Acceptance: every motion/control ledger row has evidence or an explicit browser/a11y adaptation; no known feasible mismatch remains unassigned.

### P17 — Integrated acceptance and release handoff

Files: extend `tests/browser/app.spec.ts`, `resilience.spec.ts`, `overlays.spec.ts`, `backup-accessibility.spec.ts`; update `web/docs/acceptance.md`, `web/docs/domain-parity.md`, the audit, evidence and handoff documents.

- [ ] Run a complete synthetic week for beginner/experienced-baseline/imported-returning users: onboarding→plan/import/review→workouts→recovery→progression→history/analytics→Ledger/circuit→backup/restore.
- [ ] Test offline cold start and origin unavailable, second-tab conflicts, service-worker update during active workout, failed write, tab suspension/process termination and resumed rest deadline. Never promise storage survives user clearing it.
- [ ] Cover 320×568, 360×800, 411×891 and landscape, text 100/130/200%, all palette roles, representative font extremes, spacing extrema and reduced motion. Use full shared-primitive coverage plus risk-based screen combinations.
- [ ] Run physical iPhone Safari and installed Home Screen separately; Android browser/native comparison; keyboard/screen reader and photo/share flows. If hardware is absent, keep those rows pending rather than treating desktop WebKit as physical evidence.
- [ ] Measure startup and long-history scrolling/interaction with a reproducible synthetic large dataset; compare against pre-change baseline and investigate material regressions.
- [ ] Update acceptance and domain docs from actual evidence; remove stale old deployment links and obsolete missing-feature claims. Record commit, commands, test counts/skips, screenshots, known adaptations and artifact/deployment identity if a later implementation run publishes.

Acceptance: all P00–P16 rows closed with proof, only agreed Android-only exclusions remain, full browser checks pass, device-dependent gaps clearly reported. Do not call it 100% verified while physical/visual acceptance is pending.

## Verification commands

Run from `Z:/KOTLIN/IronLogWeb/web`. For each named new test file, run `npm test -- tests/<file>` after adding its real behavioral cases; expected result is all cases passing. Before changes, run the existing baseline once and record counts rather than recycling historical results.

```powershell
npm run check
npm test
npm run build
npm run verify:output
```

Expected: TypeScript exit 0; unit/component tests pass; production bundle/service worker emitted; output verifier passes. For browser tests, run preview in a separate managed session, then execute:

```powershell
npm run preview -- --port 4173
```

```powershell
npm run test:e2e
```

The existing Playwright configuration uses `tests/browser`, base `http://127.0.0.1:4173/Ironlog/`, Chromium and WebKit plus a limited Android-sized project. Extend its Android testMatch for relevant new journeys; do not claim the existing subset covers all screens. For a deployment use its correct app base URL through `PLAYWRIGHT_BASE_URL`; verify routing before running tests.

No APK build/install is necessary for a docs-only pass. During implementation, rebuild a pinned native reference only when needed for credible comparisons. Never install to a physical device or clear its data as an incidental verification step.

## Progress and future handoff

- [x] Audit current route/source behavior and reconcile major stale gap claims.
- [x] Record native/web source hashes and line-ending-normalized divergence.
- [x] Write prioritized implementation slices, dependencies, tests and acceptance criteria.
- [ ] Implement P00–P17 and populate runtime evidence.

At each checkpoint append: slice ID; actual files/commit; fixed behavior; test command/result; source and screenshot identity; remaining subitems; next slice. Keep incomplete boxes unchecked. Preserve unrelated working-tree changes. This plan deliberately contains implementation contracts rather than speculative full replacement code before the per-slice source census is complete.
