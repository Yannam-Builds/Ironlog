# Android Auto-Backup Behavior (IRONLOG)

## Decision
IRONLOG currently keeps Android OS auto-backup enabled for:
- `database`
- `sharedpref`
- `file`

Rules live in:
- `/android/app/src/main/res/xml/backup_rules.xml`
- `/android/app/src/main/res/xml/data_extraction_rules.xml`

## Why this exists
- It improves update and device-transfer continuity for users who rely on Android restore.
- It complements (not replaces) IRONLOG encrypted backup/restore.

## What this does not replace
- Encrypted snapshot backups remain the primary trust path for deterministic restore.
- Drive backup targets remain optional and user-controlled.

## QA expectations
1. Update install (same package/signing) keeps local data.
2. Device transfer via Android restore does not break app startup or migration.
3. Encrypted snapshot restore still works after Android auto-restore.
4. If Android auto-restore and encrypted restore disagree, encrypted restore should win when user explicitly restores.

## Product messaging
- Android OS backup is continuity help.
- IRONLOG encrypted backups are the reliable disaster-recovery path.
