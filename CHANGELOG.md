# Changelog

## [Unreleased] - 2026-04-29

### Changed

- App branding updated to `IronlogDB` in launcher + startup/onboarding surfaces.
- README release messaging now marks public builds as unsupported Legacy Alpha instead of stable.

## [1.1.1] - 2026-04-25

### Added

- **Exercise library v2** — upgraded from 873 to 1,731 Codex-verified exercises with new per-exercise metadata: `isBodyweight`, `requiresExternalLoad`, `movementPattern`, `difficulty`, `apparatus`, `equipmentDetail`, `aliases`, `sourceTags`.
- **Exercise library filters** — movement pattern chips (hinge, squat, push, pull, carry, …), difficulty chips (beginner → expert), and a BW-only toggle on the library screen.
- **Search improvements** — MiniSearch now indexes `secondaryMuscles`, `movementPattern`, and `difficulty` with tuned boost weights for more relevant results.
- **Exercise profile engine v2 awareness** — `exerciseProfileEngine` now reads the authoritative v2 `movementPattern` field first before falling back to name-regex detection, improving classification accuracy for hinges, squats, carries, and conditioning exercises.
- **Custom exercise bodyweight flag** — `CreateExerciseScreen` now saves `isBodyweight: true` when equipment is set to Bodyweight so custom exercises work correctly with the BW-only filter and active workout weight logic.

### Fixed

- **Android 12+ splash grey circle** — `windowSplashScreenIconBackgroundColor` was unset, causing Android 12+ to show a system-grey circular frame behind the logo. Fixed via `values-v31/styles.xml` override.
- **Plans tab swipe blocked** — DraggableFlatList's RNGH gesture detector was calling `requestDisallowInterceptTouchEvent(true)` on ViewPager2, preventing horizontal tab swipes away from the Plans tab. Fixed with `nestedScrollEnabled={true}` and `activationDistance={28}`.
- **Home separator above body weight card** — `borderBottomWidth: 1` on `chipsRow` was rendering an unwanted divider line directly above the body weight card. Removed.
- **Stats row width misalignment** — the three stat cards (THIS WEEK / GOAL STREAK / AVG TIME) were 4px narrower than the weekly summary and training intelligence cards due to `paddingHorizontal: 12` vs `marginHorizontal: 16`. Fixed to `paddingHorizontal: 16`.
- **Program Insights sharp corners** — all card, section, metric, pill, callout, and button styles in `ProgramInsightsScreen` now use `RADIUS` constants from the theme system.
- **`normalizeSupplementalExercise` stripping v2 fields** — critical bug where the explicit object construction in this function silently dropped all v2 fields for user-added supplemental exercises. All 8 v2 fields are now passed through explicitly.
- **Stale exercise index not rebuilding** — `shouldRebuildIndex()` now checks for the presence of the `isBodyweight` field to detect and force-rebuild any v1 index persisted in AsyncStorage from before the v2 migration.
- **`isBodyweightExercise()` now v2-aware** — checks the `exercise.isBodyweight` boolean first before falling back to equipment/name string matching.
- **Exercise search adapter missing v2 fields** — `secondaryMuscles`, `movementPattern`, `difficulty` were not being indexed by MiniSearch. Now included with boost weights.
- **QA validator broken after v2 migration** — `validate_program_templates.cjs` was attempting to VM-load the ESM exercise library re-export. Rewritten to read the v2 JSON directly.
- **Plan editor losing trackingType** — time-based and cardio exercises added or replaced in `PlanEditorScreen` were being flattened to `weight_reps`. Now preserves all v2 metadata including `trackingType`.
- **Workout finish using stale exercise metadata** — active workout completion was resolving exercise metadata from the static `EXERCISES` constant instead of the live rebuilt library index, causing v2/custom metadata to be lost in history records.

### Changed

- `buildFromBundled()` and `buildIndex()` in `ExerciseLibraryService` now include all 8 v2 fields so the persisted index carries them forward after first load.
- `LEGACY_EXERCISE_ID_ALIASES` expanded with additional Smith machine and band pull-apart mappings.
- `normalizeEquipment()` now handles singular forms (`'kettlebell'`, `'band'`) in addition to the plural variants.

## [1.1.1-dev] - 2026-04-17

### Added

- MiniSearch-powered exercise library lookup (canonical names + aliases) for faster, more forgiving search.
- Root-level integrations for `react-native-toast-message`, `@gorhom/bottom-sheet`, and `react-native-keyboard-controller`.
- `react-native-gifted-charts` integration for trend-ready performance chart sections.
- `@shopify/flash-list` integration for high-volume exercise/history rendering.
- Recovery map tap-to-explain bottom sheet with confidence/source and train-maintain-backoff recommendation output.
- Progress photo side-by-side compare mode with consistency reminders.
- Android auto-backup behavior documentation in `docs/android-auto-backup.md`.

### Changed

- Backup Center trust status now surfaces backup health and restore compatibility more clearly.
- Backup flow feedback now includes inline toast confirmations/errors for common actions.
- Volume analytics now includes trend signals and next-week action guidance, while keeping radar as summary view.
- Release script now emits renamed artifacts to `release_builds/IRONLOG-<tag>-android.apk`.

### Fixed

- Replaced multiple mojibake/corrupted UI strings in analytics/program/stats and backup flows.
- Added explicit legacy-compat QA checks to distinguish migration bridges from active runtime dependencies.

## [1.1.0] - 2026-04-10

### Added

- **OpenWeight import — Phase 1** — new Import Center screen (Settings → Import Center) with `openweightInterop.js` service for parsing and ingesting OpenWeight-format exports; wired into the restore data flow alongside existing encrypted backup and SQLite import paths.
- **Countdown haptics** — 5 escalating haptic events fire during the rest timer's final 5 seconds (`countdownLightest` → `countdownLight` → `countdownMedium` → `countdownHigh` → `countdownBuzz`), giving physical cues as the rest period ends without needing to watch the screen.
- **Google Drive folder mode** — Drive backup can now target a named visible folder in addition to the hidden AppData location; `updateDriveBackupFolder` and `updateDriveSyncMode` surface a folder picker in Backup Center.
- **Runtime Google OAuth client config** — users can paste their own Android OAuth client ID directly in Backup Center without rebuilding the app; `saveDriveOAuthClient` / `clearDriveOAuthClient` stored and applied at auth time.
- **Library mode in workout exercise picker** — "ADD FROM LIBRARY" modal reuses the SwapModal with `mode="library"`, showing the full sorted library with add-circle action instead of ranked substitution candidates.
- **`useDeferredScreenReady` hook** — active workout screen defers heavy work (exercise index load, progression suggestions) until after the navigation transition settles, reducing perceived open latency.

### Fixed

- **Active workout crash on null swap candidate** — `exerciseProfileEngine`, `substitutionEngine`, and `muscleContributionEngine` now guard against `null` / `undefined` exercise objects being passed during swap ranking; prevents crash when swap candidates list contained a null entry.
- **Weight unit hardcoded to kg** — plate calculator text (`getPlateText`), PlateModal target display, BodyMeasurementsScreen, ExerciseProgressScreen, and SettingsScreen weight displays now all respect the user's `weightUnit` setting instead of always showing kg labels.
- **Drive auth runtime config for stable** — `googleDriveService` now resolves the Android OAuth client ID at auth time from runtime config (AsyncStorage) before falling back to the build-time constant, so Drive sign-in works on production builds without a recompile.

### Changed

- FlatList in active workout now uses `initialNumToRender={4}`, `maxToRenderPerBatch={6}`, `windowSize={6}`, and `removeClippedSubviews` for smoother scroll performance on longer workout days.
- Backup Center Drive section copy updated from "alpha build" wording to neutral language for stable release.

## [1.1.0-beta] - 2026-04-10

### Added

- Intelligent notification policy profiles with Balanced defaults (`1/day`, `3/week`), per-topic cooldowns, quiet-hour-safe delivery windows, and jittered timing.
- Notification decision log surfacing and temporary snooze controls in Settings.
- First-launch restore wizard with direct import paths for encrypted backup and SQLite exports.
- Versioned SQLite full-data export/import service (`IRONLOG_SQLITE_EXPORT_V1`) with restore validation.
- Settings actions for full SQLite export + restore on top of existing JSON/CSV flows.
- Share card expansion:
  - Bodyweight progress share card,
  - Exercise progress/PR trend share card,
  - Weekly summary share from Home.
- UX polish:
  - History empty state with actionable CTAs,
  - Inline volume delta vs previous session in history rows,
  - Settings intelligence controls (progression behavior + compact analytics number preference).

### Changed

- Program template generator now enforces equipment-safe accessory enrichment; all 30 picker templates pass structural validation.
- Notification cap override parsing fixed so `null` overrides no longer collapse weekly caps.
- Backup domain coverage now includes manual recovery and milestone state for restore parity.
- Volume Analytics now respects compact number preference for set displays.
- Android release packaging now enforces blocked risky unused permissions (`RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`) through native manifest policy and release-trust hardening.

## [1.1.0-alpha.3] - 2026-04-10

### Added

- Recovery score model with explainable `fresh / recovering / fatigued` state.
- Manual recovery check-in flow (soreness, sleep, energy, notes) on Muscle Recovery screen.
- Engagement engine for streak tracking, milestone unlocks, and weekly summary card generation.
- Weekly summary + milestone surfacing on Home.
- Smart notification scheduler foundation with cooldown and quiet-hour safeguards.

### Changed

- Active workout completion now includes milestone unlock context and milestone haptics.
- Notification settings now expose controls for enable/disable, quiet hours, and cooldown windows.
- Backup schema domains extended to include manual recovery entries and milestone state.

## [1.1.0-alpha.2] - 2026-04-10

### Added

- Program Insights screen with day-level adherence, adaptive next-session targets, and missed-day reschedule guidance.
- Goal Mode setting (`Hypertrophy`, `Strength`, `General Fitness`) that now drives adaptive program target bias.
- Expanded exercise progress dashboard tabs: `E1RM`, `Load`, `Reps`, `Volume`, `Consistency`, and `History`.

### Changed

- Home training intelligence now uses persisted goal mode when plan-level goal mode is missing.
- Home intelligence card now deep-links directly to Program Insights.
- IRONLOG 2.0 implementation checklist updated to mark Milestone 2 (Phase C + D) complete.

## [1.1.0-alpha.1] - 2026-04-10

### Fixed

- Google Drive backup flow now handles unconfigured alpha builds gracefully and disables Drive connect when OAuth config is missing.
- Program window crash guard added for Recovery and Volume Analytics when plan data is missing or malformed.
- Volume Analytics number formatting cleaned up (effective sets, push/pull/legs, and muscle breakdown values are now readable and rounded).

### Added

- Active workout set controls now support inline edit and delete for logged sets.
- Active workout now supports adding extra exercises from the library during an ongoing session.

### Changed

- Program chips on Recovery and Volume Analytics now degrade safely when no valid program data is available.

## [1.0.0] - 2026-04-09

### Added

- Initial public Android release
- Workout logging, plans, and exercise library
- Volume analytics, recovery heatmap, and body weight analytics
- Program recommendations and YouTube exercise demo links

### Improved

- Navigation smoothness
- Haptics behavior and action feedback
- Overall app stability for the public release baseline
