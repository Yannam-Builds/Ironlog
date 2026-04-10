# IRONLOG 2.0 QA Closure Report

Date: 2026-04-10

## Executed QA checks

1. Program template integrity
   - Command: `node scripts/validate_program_templates.cjs`
   - Result: `PASS: validated 30 templates`

2. Notification policy (Balanced/anti-annoyance contract)
   - Command: `node scripts/qa_notification_policy.cjs`
   - Result: `PASS: notification policy QA checks completed`
   - Covers:
     - daily cap enforcement,
     - weekly cap enforcement,
     - per-topic cooldown gating,
     - arbitration (highest-score winner),
     - actioned suppression (bodyweight already logged),
     - schedule + sent decision logging.

3. Migration + export/import contract
   - Command: `node scripts/qa_migration_contract.cjs`
   - Result: `PASS: migration and export/import contract checks completed`
   - Covers:
     - migration marker guards present,
     - row-count verification guards present,
     - App boot migration path present,
     - SQLite export schema and validator behavior.

4. Release build verification
   - Command: `gradlew.bat assembleRelease` (in `android/`)
   - Result: `BUILD SUCCESSFUL`

## Key defects found and fixed in closure pass

1. Notification override parsing bug
   - `null` weekly override incorrectly evaluated as `0`, forcing a 1/week cap.
   - Fixed with positive-number parsing guards.

2. Template enrichment equipment drift
   - Auto-enrichment added non-home/non-bodyweight accessories to constrained plans.
   - Fixed by enforcing allowed-equipment filtering and corrected day-focus routing.

## Build artifact

- `Z:\ironlog\android\app\build\outputs\apk\release\IRONLOG-RC-1.1.0-qa100.apk`
