# IronLog Android - Current RN Parity Gap Tracker

Last reconciled: 2026-05-18

This file replaces the stale Gemini gap list from 2026-05-18. The old list had 24 gaps, but most were fixed in subsequent passes and documented in `Z:\KOTLIN\AGENTS.md`.

Scope: active RN runtime features only, compared against `Z:\ironlog\src\navigation\AppNavigator.js` and Kotlin `Z:\KOTLIN\UnifiedPort\app\src\main\java\com\ironlog\app\navigation\AppNavigator.kt`.

## Current Parity Snapshot

| Area | Status | Notes |
| --- | --- | --- |
| Active route coverage | MATCH | Kotlin has all active RN stack routes and the same 5 tab roots. |
| Core tab workflows | MOSTLY MATCH | Home, Plans, Log, Stats, Settings are implemented; remaining issues are workflow details, not missing tabs. |
| Plan/program flows | MOSTLY MATCH | Browse, import, AI creation, duplicate, edit, start, and day editing exist. |
| Active workout | NEEDS DEVICE VERIFY | Core loop is rich, but background/lock/resume/timer/haptic/notification behavior must be device-proven. |
| Analytics/intelligence | MOSTLY MATCH | Most old metric gaps are fixed; Training Intelligence still has a context gap. |
| Body/photos/gym profiles | MOSTLY MATCH | Main flows exist; photo capture/import/compare/export need real-device proof. |
| Backup/import/export | MOSTLY MATCH | Core paths and crash fixes exist; document picker/share/restore edge cases need device proof. |
| Settings/notifications/haptics | NEEDS DEVICE VERIFY | UI exists, but notification and locked-screen haptic behavior are device-sensitive. |

Estimated static parity: 94-96%.
Estimated production-proven parity: 88-92% until device walkthrough evidence is captured.

## Open Gaps

### OPEN-01 - TrainingIntelligenceScreen start workout loses recommended day context

Status: CLOSED IN CODE / NEEDS DEVICE VERIFY

Files:
- `app/src/main/java/com/ironlog/app/ui/screens/TrainingIntelligenceScreen.kt`
- `app/src/main/java/com/ironlog/app/navigation/AppNavigator.kt`

Resolution:
- `TrainingIntelligenceScreen` now derives a recommended active-plan day from history.
- The quick action passes the recommended `dayId` to `AppNavigator.kt`.
- Navigation now opens `ActiveWorkout/{dayId}` when context exists, with blank workout only as fallback.

Expected RN parity:
- The quick action should start the recommended next session when a recommendation exists.
- It should only fall back to blank workout when no plan/day context is available.

Implementation target:
- Change the screen callback to carry an optional `dayId`.
- Derive the recommended day from the same data used by the Training Intelligence directive.
- Navigate to `ActiveWorkout/{dayId}` when present.

Verification:
- From Training Intelligence, tap Start Workout with an active program.
- Active Workout should open pre-populated with the recommended day exercises.

### OPEN-02 - WorkoutCalendar start workout is still generic

Status: CLOSED IN CODE / NEEDS DEVICE VERIFY

Files:
- `app/src/main/java/com/ironlog/app/ui/screens/WorkoutCalendarScreen.kt`
- `app/src/main/java/com/ironlog/app/navigation/AppNavigator.kt`

Resolution:
- Calendar long-press start now stores `active_workout_intended_date` before opening `ActiveWorkout`.
- `WorkoutRepository` consumes that date when creating the active workout and uses the selected past/today date as `startedAt`.
- Future-date long-press starts are blocked to avoid future-started active workout duration bugs.

Expected RN parity:
- If RN only starts a blank workout from calendar, this is acceptable.
- If RN preserves selected date or uses the active plan's next session for that date, Kotlin needs to pass that context.

Implementation target:
- Audit RN calendar behavior directly before changing this.
- If date context matters, add an active workout start argument or persisted intended workout date.

Verification:
- Long-press an empty past/future date.
- Confirm whether the resulting workout date and history entry match expected RN behavior.

### OPEN-03 - History edit date/time still uses raw text fields

Status: CLOSED IN CODE / NEEDS DEVICE VERIFY

Files:
- `app/src/main/java/com/ironlog/app/ui/screens/HistoryScreen.kt`

Resolution:
- History edit now has Material date and time pickers.
- Raw fields remain editable, but invalid date/time now shows an inline error.
- Saving no longer silently falls back to the old timestamp when input is invalid.

Expected RN parity:
- Editing should be deliberate and validated.
- A picker-style date/time edit is safer than raw fields.

Implementation target:
- Replace or supplement raw date/time fields with Material date/time picker controls.
- Show inline validation when parsing fails.
- Do not silently save the old timestamp when the user entered invalid text.

Verification:
- Edit a log date/time.
- Try invalid input and confirm visible error.
- Save valid input and verify persistence after app restart.

### OPEN-04 - Onboarding browse-program navigation uses pending route token

Status: CLOSED IN CODE / NEEDS DEVICE VERIFY

Files:
- `app/src/main/java/com/ironlog/app/ui/screens/OnboardingScreen.kt`
- `app/src/main/java/com/ironlog/app/navigation/AppNavigator.kt`
- `app/src/main/java/com/ironlog/app/MainActivity.kt`

Resolution:
- Onboarding entry now clears any stale `pending_nav_route` before rendering.
- Browse Programs can still set the one-shot ProgramPicker token for the live transition, but old interrupted tokens are removed.

Expected RN parity:
- Browse Programs should directly and reliably complete onboarding then open ProgramPicker.

Implementation target:
- Prefer a scoped navigation event or direct post-navigation callback.
- At minimum, clear stale pending route when onboarding starts/resumes.

Verification:
- Reset onboarding.
- Tap Browse Programs.
- Back out, restart app, and ensure no stale ProgramPicker navigation fires later.

## Device Verification Gaps

These are not proven missing in code, but cannot be honestly closed without a real device walkthrough.

### DEVICE-01 - Active workout background/lock/resume survival

Status: CODE HARDENED / NEEDS DEVICE VERIFY

Code hardening completed 2026-05-18:
- `ActiveWorkoutScreen` now writes a lifecycle-critical active-workout draft snapshot synchronously on `ON_STOP`.
- The disposal path now also uses the blocking draft snapshot instead of launching a coroutine that may be cancelled during teardown.
- Normal typing/logging persistence still uses the existing async path, so steady-state UI interaction remains lightweight.

Evidence needed:
- Start a workout from Home and Plans.
- Log multiple sets.
- Minimize, lock screen, wait, unlock, resume.
- Confirm set inputs, order, rest timer, and active workout notification survive.

Risk:
- This area has had recent real-device failures, so it remains a hard verification item.

### DEVICE-02 - Foreground workout notification and notification permission flow

Status: CODE HARDENED / NEEDS DEVICE VERIFY

Code hardening completed 2026-05-18:
- `WorkoutForegroundService` is now the single source for active workout notifications.
- The live notification is theme-tinted from `ironlog_settings`.
- The notification shows current workout, set label, rest seconds, and session time.
- Resting notifications expose `Skip` and `+30s` actions.
- All live workout notifications expose `Finish`.
- Notification actions deep-link through `MainActivity` and set `pending_workout_action`, so `ActiveWorkoutScreen` applies the real reducer path instead of leaving dead background actions.

Evidence needed:
- Permission denied path.
- Permission granted path.
- Test notification from Settings.
- Active workout persistent notification with current workout/set/timer.
- Notification tap/action deep-links back to correct workout state.

### DEVICE-03 - Locked-screen timer haptics

Evidence needed:
- Start rest timer.
- Lock phone during final countdown.
- Confirm final 5-second haptic ramp fires while app is backgrounded/locked.

### DEVICE-04 - Progress photo lifecycle

Evidence needed:
- Capture photo.
- Import from gallery.
- Open viewer.
- Compare two photos.
- Delete/export/clear behavior if exposed.

### DEVICE-05 - Backup/import/export real intents

Evidence needed:
- Full backup share/save.
- Restore preview.
- Replace/append behavior.
- CSV/OpenWeight/data portability import and export.
- Malformed file rejection.

## Closed Gaps From Old `PARITY_GAPS.md`

These were listed as open in the old tracker but are now closed or confirmed false positive.

| Old Gap | Current Status | Evidence |
| --- | --- | --- |
| GAP-01 ProgramInsights wrong adherence formula | CLOSED | `AGENTS.md` 2026-05-18 ProgramInsights rewrite. |
| GAP-02 ProgramInsights wrong active plan | CLOSED | Active plan fallback implemented. |
| GAP-03 Deleting active gym profile stale ID | CLOSED | `GymProfilesScreen.kt` clears or reassigns `active_gym_profile_id`. |
| GAP-04 VolumeAnalytics PROGRAM window | CLOSED | Program-duration-aware window implemented. |
| GAP-05 Empty calendar day dead tap | CLOSED | `AddWorkoutForDateSheet` exists and is wired. |
| GAP-06 Superset grouping/auto-advance | CLOSED | Superset labels, grouping behavior, and scroll-to-partner logic exist in ActiveWorkout. |
| GAP-07 Volume unit conversion | CLOSED | VolumeAnalytics converts display values by weight unit. |
| GAP-08 Progress photo labels | CLOSED | Labels swapped/fixed. |
| GAP-09 BodyWeight delete confirmation | CLOSED | Delete confirmation dialog implemented. |
| GAP-10 Duplicate plan copies day IDs | CLOSED | Duplicate uses `importFullPlan`, creating fresh entities/IDs. |
| GAP-11 ExerciseProgress consistency always 100% | CLOSED | ISO week sessions-per-week chart implemented. |
| GAP-12 TrainingIntelligence blank start | OPEN | See OPEN-01. |
| GAP-13 Onboarding unit selector | CLOSED | kg/lbs toggle added and persisted. |
| GAP-14 BodyMeasurements expanded trend | CLOSED | Expanded trend dialog/range chips implemented. |
| GAP-15 History date picker | OPEN | See OPEN-03. |
| GAP-16 History star rating widget | CLOSED | 5-star selector implemented. |
| GAP-17 ProgramInsights stub cards | CLOSED | Added adherence, consistency, per-day breakdown, top PRs. |
| GAP-18 Calendar volume unit conversion | CLOSED | `toDisplayVolume(..., weightUnit)` used. |
| GAP-19 Calendar Monday week start | CLOSED | ISO Monday start implemented. |
| GAP-20 Gym plate color picker | FALSE POSITIVE | Inline preset color picker already exists. |
| GAP-21 ExerciseProgress reactive weight unit | CLOSED | `AppNavigator.kt` passes `statsState.weightUnit`; screen recomputes with `weightUnit`. |
| GAP-22 BodyWeight validation unit-aware | CLOSED | kg/lbs validation ranges implemented. |
| GAP-23 Home XP progress bar | CLOSED | Tier progress bar implemented. |
| GAP-24 Onboarding pending route | OPEN | See OPEN-04. |

## Kotlin Features That Exceed RN Parity

These should not be removed just to chase RN sameness.

- Cloud AI plan generation with system-prompt JSON enforcement and larger token budget.
- AI import review with deterministic fuzzy matching and one-tap suggested mapping.
- Smart plan structuring for warmups, supersets, rest normalization, and metadata repair.
- Exercise tracking-type normalization and startup repair.
- Process-scoped Cloud AI insight cache.
- Material You theme system and multiple visual themes.
- Duplicate-safe active workout row binding for repeated exercise IDs.

## Next Closure Pass

Recommended order:

1. Run a real-device proof pass for DEVICE-01 through DEVICE-05.
2. Verify the four code-closed gaps above on device.
3. If device behavior matches RN, mark the code-closed items fully closed.

Only after those are done should parity be marked 100%.
