# IRONLOG 1.1.0-beta (Android)

## What this beta actually includes

- Program Insights with adherence tracking, adaptive targets, and missed-day guidance.
- Goal Mode controls (`Hypertrophy`, `Strength`, `General Fitness`) wired into recommendation logic.
- Exercise Progress dashboard expansion (`E1RM`, `Load`, `Reps`, `Volume`, `Consistency`, `History`).
- Recovery score and manual recovery check-ins (soreness, sleep, energy, notes).
- Streaks, milestones, and weekly summary surfacing on Home.
- Smart notification policy engine with profile caps, quiet hours, cooldowns, snooze, and decision logging.
- Restore wizard plus versioned SQLite export/import foundation.
- Share card expansion for bodyweight, exercise progress, and weekly summary.
- Active workout quality-of-life updates: inline set edit/delete and add exercise during session.
- Crash and reliability fixes for analytics and recovery program-window paths.

## Data and backup notes

- IRONLOG remains local-first on Android.
- Google Drive support is backup-target behavior (AppData or folder mode), not live sync.
- Uninstall continuity depends on restore from encrypted backup or exported file.

## Release trust notes

- release builds must be signed with a real keystore (never debug certs).
- blocked unused high-risk permissions should remain enforced for production packaging.

## APK naming

- recommended beta artifact pattern: `IRONLOG-v1.1.0-beta-<build>.apk`
