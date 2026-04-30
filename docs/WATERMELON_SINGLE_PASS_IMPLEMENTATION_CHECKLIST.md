# Ironlog Single-Pass Implementation Checklist

Date: 2026-04-29  
Scope: Feature interoperability audit + complete Watermelon migration plan (production UI retained)

## Current Reality (Audit Snapshot)
- [x] App identity is currently old package/app naming (`com.ironlog.app`, `Ironlog`).
- [x] Active runtime still boots through legacy startup:
  - `runMigrations()` in `App.js`
  - `ensureTrainingDatabase()` in `App.js`
  - `AppContext` is still canonical data source for most screens.
- [x] Training Intelligence and Athlete/Gym profile features are wired, but depend on mixed storage paths.
- [x] Active workout/session persistence still uses AsyncStorage in active flows.
- [x] Body metrics/progress photos/settings still contain active AsyncStorage writes.

## Success Definition
- [ ] Production UI remains parity with the main Ironlog experience.
- [ ] WatermelonDB becomes the only persistent source of truth for core app data.
- [ ] Training Intelligence + Athlete/Gym profile defaults are fully aligned with set creation/logging/history/stats.
- [ ] Import/export and backup routes normalize into Watermelon (no legacy runtime restore path).
- [ ] No active screen depends on legacy SQLite/AsyncStorage for domain state.

## Phase 1: Runtime Boot Cutover
- [ ] Remove legacy boot from `App.js` active path:
  - [ ] Remove `runMigrations()` startup dependency.
  - [ ] Remove `ensureTrainingDatabase()` startup dependency.
  - [ ] Initialize Watermelon + seed checks on startup.
- [ ] Keep providers only for UI/session orchestration, not persistence source-of-truth.
- [ ] Keep `AppNavigator` as active nav for full feature parity.

Files:
- `App.js`
- `src/context/AppContext.js`
- `src/db/*`

Gate:
- [ ] App launches cleanly with Watermelon boot only.

## Phase 2: Canonical Domain Persistence Migration
- [ ] Exercises:
  - [ ] `ExerciseLibraryScreen`, pickers, create/edit flows use Watermelon repositories.
  - [ ] Static JSON used only for seeding.
- [ ] Plans:
  - [ ] `PlansScreen`, `PlanEditorScreen`, day/exercise CRUD/reorder via repository.
- [ ] Active Workouts:
  - [ ] `ActiveWorkoutScreen` and `WorkoutContext` session state backed by Watermelon persistence model.
  - [ ] Remove active AsyncStorage session keys and last-performance keys.
- [ ] History/Stats:
  - [ ] History and detail screens read from Watermelon workout tables.
  - [ ] Stats derive from Watermelon only (no legacy PB/history mirrors).
- [ ] Body/Progress:
  - [ ] Body weight + body measurements + progress photo metadata in Watermelon.
  - [ ] Filesystem only for binary photo files.

Files (primary):
- `src/screens/ActiveWorkoutScreen.js`
- `src/context/WorkoutContext.js`
- `src/context/AppContext.js`
- `src/screens/PlansScreen.js`
- `src/screens/PlanEditorScreen.js`
- `src/screens/ExerciseLibraryScreen.js`
- `src/screens/HistoryScreen.js`
- `src/screens/StatsScreen.js`
- `src/screens/BodyWeightScreen.js`
- `src/screens/BodyMeasurementsScreen.js`
- `src/screens/ProgressPhotosScreen.js`
- `src/db/repositories/*`

Gate:
- [ ] No active domain writes to AsyncStorage.

## Phase 3: Training Intelligence + Athlete/Gym Profile Contract
- [ ] Athlete profile defaults flow through all set creation paths:
  - [ ] start workout
  - [ ] add set
  - [ ] auto-fill/new set defaults
  - [ ] warmup generation
- [ ] Ensure unit conversion correctness (`kg/lb`) when applying bar/plate defaults.
- [ ] Ensure existing logged sets are not retroactively mutated by profile changes.
- [ ] Training Intelligence derives from canonical Watermelon-backed history shape.
- [ ] Verify movement-pattern resolution uses metadata before fallback heuristics.

Files (primary):
- `src/screens/ActiveWorkoutScreen.js`
- `src/screens/GymProfileEditorScreen.js`
- `src/screens/GymProfilesScreen.js`
- `src/screens/TrainingIntelligenceScreen.js`
- `src/services/TrainingIntelligenceService.js`
- `src/domain/intelligence/*`

Gate:
- [ ] Profile changes affect new sets immediately and safely.
- [ ] TI metrics align with workout/log history.

## Phase 4: Legacy Reachability Elimination
- [ ] Remove or isolate active imports of:
  - [ ] `trainingDatabase`
  - [ ] `trainingRepository`
  - [ ] legacy `migrations` bootstrap
  - [ ] legacy restore/state mirrors
- [ ] Allow legacy code only as one-way import normalizers (explicitly isolated).
- [ ] Remove dead active code paths that silently swallow persistence errors.

Files (primary):
- `src/context/AppContext.js`
- `src/services/migrations.js`
- `src/domain/storage/*`
- `src/services/sqliteExportImport.js`
- `src/services/backupService.js`

Gate:
- [ ] Active import tree has no unsafe legacy persistence path.

## Phase 5: Theming and Monet Reliability
- [ ] Full active-screen sweep for Monet safety:
  - [ ] no invalid PlatformColor usage in Reanimated color props
  - [ ] no unsafe color string concatenations
- [ ] Contrast-safe text on accent surfaces/buttons/modals/toasts across:
  - [ ] Light
  - [ ] Dark
  - [ ] Amoled
  - [ ] Monet

Files (primary):
- `src/utils/themes.js`
- `src/utils/colorUtils.js`
- `src/components/CustomAlert.js`
- `src/components/ui/*`
- all active screens/components with accent buttons and overlays

Gate:
- [ ] No theme-specific readability failures.
- [ ] No Monet-only crash path.

## Phase 6: Import/Export + Restore Contract
- [ ] Export emits normalized Watermelon schema only.
- [ ] Import validates payload and fails safely on malformed/wrong type.
- [ ] Legacy SQLite/Async imports normalize into Watermelon rows only.
- [ ] No restore path rehydrates legacy runtime state as canonical source.

Files (primary):
- `src/db/repositories/importExportRepository.js`
- `src/services/sqliteExportImport.js`
- `src/screens/SettingsScreen.js`
- `src/screens/RestoreDataScreen.js`
- `src/screens/BackupCenterScreen.js`

Gate:
- [ ] Clean reinstall + import restores exercises/plans/workouts/sets/settings.

## Phase 7: Final Verification Matrix (Must Pass)
- [ ] Seed runs once (no duplication on reopen).
- [ ] Custom exercise persists after restart.
- [ ] Plan/day/exercise config persists after restart.
- [ ] Active workout survives kill/reopen.
- [ ] Completed workout appears in history + detail.
- [ ] Editing completed set updates stats.
- [ ] Warmup PR exclusion validated (`100x10 warmup`, `50x10 normal` -> PR from 50).
- [ ] Export file schema valid.
- [ ] Import works in clean app.
- [ ] Malformed import safely rejected.
- [ ] Training Intelligence and Athlete profile behavior verified end-to-end.

## Final Migration Verdict Rules
- TRUE WATERMELON-FIRST only if all phases + matrix pass.
- PARTIAL if architecture mostly migrated but one or more gates fail.
- FAIL if active runtime still depends on legacy persistence for domain truth.

