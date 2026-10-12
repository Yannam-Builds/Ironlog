# Portability final browser verification

Executed 2026-10-12 Europe/Warsaw (2026-10-11 UTC) against fresh Vite source at `http://127.0.0.1:5183/Ironlog/app/`.

Used Playwright CLI in a newly created named Chromium session, `portability-closure`. All browser records, import files, photo bytes and credential markers were synthetic. No user browser session, private configuration, actual API key or phone data was accessed. No application source was edited and no broad build/test gates were run.

## Results

| Check | Evidence | Result |
| --- | --- | --- |
| Strong CSV preview and review gate | A two-row CSV with `Weight (lb)`, quoted comma and quoted newline showed `1 workouts · 2 sets`; append was disabled until the review checkbox was selected. | Pass |
| Reviewed append | Imported one completed session with two sets. 100 lb became 45.35923699997481 kg; 105 lb became 47.62719884997355 kg. Notes retained both the comma and newline. | Pass |
| Preserve existing data | Compared the entire active workout and measurement records before and after append. Both remained identical. | Pass |
| Duplicate import | Repeated the same CSV through preview/review/append. UI showed `These workouts are already present; nothing was changed.` Completed workout count remained 1, active count remained 1, and the existing measurement remained present. | Pass |
| Legacy SQLite preview and cancel | Preview showed 0 plans, 1 workout, 0 measurements and 0 photos. Closing the replacement dialog preserved the existing imported workout and measurement. | Pass |
| Legacy SQLite commit | UI replacement restored `Legacy Runtime`, 12 training-age months and 20 baseline pushups; profile stayed onboarded. | Pass |
| Canonical native JSON commit | Restored a native-format JSON without a web extension through the real file picker and replacement dialog. Profile was onboarded; 24 months / four days per week rebuilt 417 baseline sessions; pushups were 30; native lowercase `strength` remained valid. | Pass |
| Native badges and recovery | Native first-workout date 1735689600000 survived restore with `native_import` origin. Recovery week `2026-W40` with positive count survived; zero-count `2026-W39` was excluded. | Pass |
| Actual downloaded ZIP roundtrip | Seeded a synthetic 68-byte PNG photo; downloaded a complete browser ZIP through the UI; deleted that photo; selected the downloaded ZIP through the file picker and committed replacement. The photo returned with all 68 bytes exactly equal. | Pass |
| Credential exclusion | Synthetic markers in a direct credential field, an array token field and stringified JSON authorization field were absent from the restored ZIP profile. Ordinary array and stringified preferences survived. | Pass |
| Stale extension precedence | Browser-evaluated codecs on a synthetic snapshot with stale extension baseline (24 months, seed 1000) and changed canonical calibration (0 months, updated-at 2000). Decoded baseline had 0 estimated sessions and seed 2000. Canonical first-workout/comeback dates and `native_import` origins survived subsequent baseline migration. | Pass |

No remaining portability blocker was observed in these checks. This is a Chromium source-runtime verification; it does not establish Safari, mobile-device, or built-distribution behavior.

## Reproduction artifacts

Ignored runtime artifacts are under `web/output/playwright/`:

- `portability-closure-seed.js`: synthetic browser seed.
- `portability-append.js`: CSV interaction harness. Its final duplicate-warning locator matched both the top alert and dialog alert, so the final database verification used an explicit browser evaluation; the application append and duplicate flows themselves completed successfully.
- `portability-legacy.json`, `portability-restore.js`, `portability-legacy-preview.png`: legacy preview/cancel/commit fixture, assertions and screenshot.
- `portability-native.json`, `portability-native-restore.js`, `portability-native-restored.png`: canonical native fixture, assertions and screenshot.
- `portability-browser.zip`, `portability-zip.js`, `portability-zip-restored.png`: actual UI download, byte/credential assertions and screenshot.

CLI snapshots and console records are in ignored `web/.playwright-cli/`. Browser console records contained React development-tool information; no uncaught application error appeared during these flows.
