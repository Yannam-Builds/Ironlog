# IronlogDB Handoff (ChatGPT Web)

Last updated: 2026-04-29

## What this repo currently is

- Branch/repo line: Ironlog (Android, React Native JS-first).
- Public release policy: legacy `v1.0.0`, `v1.1.0-beta`, `v1.1.0` are unsupported Legacy Alpha.
- Active rebuild direction: IronlogDB branding + WatermelonDB-first migration path.

## Branding and release messaging status

- Android app launcher name: `IronlogDB` (`android/app/src/main/res/values/strings.xml`).
- Startup/onboarding labels updated to `IronlogDB`:
  - `App.js`
  - `src/screens/LibrarySetupScreen.js`
  - `src/screens/MigrationScreen.js`
  - `src/screens/OnboardingScreen.js`
- README no longer markets "stable" as active support; it now labels public APKs as Legacy Alpha and unsupported.

## Active workout + athlete profile pass completed

- Copy-previous set fill now supports unit-aware prefill through `COPY_PREVIOUS_CUSTOM` in `src/context/WorkoutContext.js`.
- Active workout now generates copy-previous inputs with conversion based on current unit (`kg/lbs`) and tracking type (duration vs weight-reps):
  - `src/screens/ActiveWorkoutScreen.js`
- Warm-up generator dependency corrected to react to active profile bar weight changes:
  - `src/screens/ActiveWorkoutScreen.js`
- Gym profile bar and plate editor/list UI is unit-aware while still storing canonical kg:
  - `src/screens/GymProfileEditorScreen.js`
  - `src/screens/GymProfilesScreen.js`

## Monet/theming safety

- Existing active crash pattern (`colors.* + 'xx'`) scan in `src/` paths is clean.
- `CustomAlert` remains themed and PlatformColor-safe via `withAlpha(...)` resolution.

## Important project reality (do not ignore)

- Worktree is intentionally large and dirty because this repo carries both legacy + migration work in-flight.
- Do not run destructive git cleanup commands.
- Do not assume untracked files are disposable without review.

## Next recommended implementation checks

1. Runtime QA on device for athlete profile flow:
   - Change unit to lbs
   - Edit bar/plates in profile
   - Verify warm-up generation and plate modal math
   - Verify copy-previous prefill values
2. Continue remaining Watermelon migration closure in active runtime paths.
3. Complete final contrast audit on all themes (light/dark/amoled/Monet) for action buttons and modal text.

