# IronLog v2.0 Stable

Release status: Stable release page draft

Target platform: Android

Package: `com.ironlog.app`

Current local APK candidate:

`Z:\ironlog\android\app\build\outputs\apk\release\app-release.apk`

Current candidate SHA256:

`FD1B61B67DC6D1360CDCE5EBAEC958890A358898AC5D604E8B6C8E65E7E56E7D`

Current candidate size:

`78,355,009 bytes`

Important: publish this as v2.0 Stable only after the final stable candidate checklist passes. Until then, this page should be treated as the release draft.

## One-Line Summary

IronLog v2.0 is a serious offline-first Android workout tracker for lifters who want fast gym logging, useful program feedback, recovery insight, and full control over their training data.

## Short Release Description

IronLog v2.0 Stable is the largest release of IronLog so far. It expands the app from a workout logger into a complete lifting system with a large exercise library, built-in program templates, active workout logging, program intelligence, muscle analytics, recovery maps, progress photos, body tracking, smart notifications, and encrypted local-first backup.

The focus of v2.0 is simple: log fast in the gym, understand what is changing over time, and keep ownership of your data.

## Who This Release Is For

IronLog v2.0 is built for:

- Lifters who want a private, offline-first gym log.
- People who use structured programs and want practical next-session guidance.
- Users who care about muscle balance, volume, and recovery.
- Android users who want backup/export control instead of relying only on cloud accounts.
- Beginners who want templates without being locked into one rigid program.
- Intermediate lifters who want analytics without turning the app into a spreadsheet.

## Headline Features

### Fast Workout Logging

- Start workouts from saved plans.
- Log sets, reps, and weight quickly.
- Edit and delete logged sets.
- Add notes to sets and exercises.
- Add or swap exercises during a workout.
- Generate warm-up sets.
- Use rest timers with pause, skip, and add-time controls.
- Use plate calculator support for barbell work.
- Track personal bests.
- Get a themed PR HUD when a new best is logged.
- Finish workouts with session summaries and share cards.

### Program Templates And Planning

- 31 validated built-in program templates.
- Beginner, strength, hypertrophy, aesthetic, glutes/lower-body, specialization, home, bodyweight, and conditioning categories.
- Program metadata for progression model, deload protocol, effort target, block duration, and guardrails.
- Custom plan creation and editing.
- Day-level exercise editing.
- Built-in program picker.
- Program insights tied to real session history.

### Large Exercise Library

- 919 validated resolved exercises in the current local build.
- Canonical exercise naming and alias support.
- MiniSearch-powered search with typo tolerance.
- Favorites.
- Muscle/category/equipment filters.
- Custom exercise creation.
- Custom exercise sharing.
- Tracking type support for weight/reps, duration, and duration/distance exercises.
- YouTube/search metadata for exercise demo discovery.

### Analytics And Progress

- Stats dashboard with sessions, total sets, average time, streak, recent activity chart, and personal bests.
- Exercise progress views.
- Estimated 1RM logic.
- Volume analytics.
- Effective-set and muscle-group analysis.
- Push/pull/legs balance.
- Trend direction and next-week guidance.
- Program insights for adherence and adaptive targets.
- Weekly summaries and milestone tracking.

### Recovery Maps

- Front and back body recovery maps.
- Recovery score.
- Tap-to-explain muscle regions.
- Confidence and data-source labels.
- Train, maintain, or back-off recommendations.
- Manual recovery check-ins for soreness, sleep quality, energy, and notes.

### Body Tracking And Photos

- Bodyweight logging.
- Bodyweight trends.
- Body measurements.
- Progress photo calendar.
- Camera and gallery photo import.
- Side-by-side compare mode.
- Photo consistency reminders.
- Local photo export as ZIP.
- Privacy and cleanup controls.

### Smart Notifications

- Notifee-based Android notifications.
- Balanced notification profile by default.
- Training reminders.
- Recovery suggestions.
- Streak-risk reminders.
- Bodyweight logging reminders.
- Milestone/PR reminders.
- Backup health reminders.
- Quiet hours.
- Daily and weekly caps.
- Per-topic cooldowns.
- Snooze controls.
- Decision log for explainability.
- Test notification support.
- Ongoing workout notification.

### Backup, Restore, And Data Ownership

- Local-first SQLite storage.
- Encrypted local backup snapshots.
- Google Drive backup support.
- Drive AppData and visible folder modes.
- SQLite export/import.
- JSON backup import/export.
- CSV import/export.
- OpenWeight import/export path.
- Restore summary model.
- Backup health status.
- Android OS auto-backup documentation.
- Legacy compatibility bridges for older installs.

### Themes And Polish

- AMOLED theme.
- Dark theme.
- Light theme.
- Monet-inspired Android theme.
- Floating bottom navigation.
- Floating active workout pill.
- Premium haptics.
- Reanimated alerts/toasts.
- Skeleton loading states.
- Empty states.
- Theme-safe alpha handling for Android dynamic colors.
- Rounded controls and updated visual system tokens.

## What Changed Since Earlier Builds

- Migrated toward bare React Native Android runtime.
- Removed active Expo runtime assumptions from the source path.
- Hardened exercise library canonicalization and aliases.
- Added MiniSearch exercise search.
- Added favorite exercises.
- Added custom exercise creation/share paths.
- Expanded program templates and metadata.
- Added smart notification decision policy.
- Added recovery maps with confidence and tap-to-explain.
- Added progress photo compare mode.
- Added backup center trust/status improvements.
- Added SQLite export/import support.
- Added OpenWeight interoperability path.
- Added PR HUD and improved celebration feedback.
- Added Monet-safe color utilities.
- Improved active workout keyboard/search/bottom-sheet UX.
- Improved bottom overlay spacing for floating nav and workout pill.

## Upgrade Notes

Existing users should be able to update in place without deleting the app as long as the package name and signing key remain consistent.

Important data behavior:

- In-place updates should preserve Android app data.
- Uninstalling the app can delete local app data.
- Before uninstalling or changing phones, create an encrypted backup or SQLite export.
- Google Drive backup is a backup target, not a real-time sync system.
- Android OS auto-backup may restore some app data depending on device/account settings, but app-managed backups remain the recommended recovery path.

## Install Instructions

Download the APK from the release assets and install it on Android.

If installing manually with ADB:

```powershell
adb install -r app-release.apk
```

If replacing an older build, use the same signing key so Android accepts the update.

## Recommended First-Run Setup

1. Open IronLog.
2. Choose a theme.
3. Pick a starter program or build a custom plan.
4. Set weight unit, rest timer defaults, bar weight, and gym profile.
5. Enable encrypted backup.
6. Optionally connect Google Drive backup.
7. Send a test notification if using reminders.
8. Log the first workout.

## Suggested Screenshots For GitHub Or Play Store

Use current high-quality screenshots for:

- Home dashboard.
- Active workout logging.
- Program picker.
- Exercise library search.
- Volume analytics.
- Recovery map front view.
- Bodyweight tracker.
- Progress photo compare.
- Backup center.
- Settings/theme view.

Recommended screenshot captions:

- "Log fast while you train."
- "Follow structured programs or build your own."
- "Search 900+ exercises with aliases and favorites."
- "See weekly volume and training balance."
- "Understand recovery by muscle group."
- "Track bodyweight, measurements, and photos."
- "Own your data with encrypted backups."

## Known Limitations

- Android only.
- No Apple Watch, Wear OS, or web app yet.
- No social feed or public community layer.
- No built-in professional video library for every exercise.
- Google Drive is backup-oriented, not live multi-device sync.
- Monet theme should still be tested on real Android 12+ devices before broad release.
- Old backup formats should be tested with real files before calling this final stable.

## Stable Release Gate

Publish this as `v2.0 Stable` only when all items below pass:

- `npm run validate:plans` passes.
- Android `assembleRelease` passes.
- APK installs over the previous release using the production signing key.
- App launches without fatal logcat crash.
- Active workout starts.
- Add exercise works from Active Workout.
- Add exercise works from Plan Editor.
- Create Exercise works from Library, Active Workout, and Plan Editor paths.
- Search input is not covered by keyboard.
- Rest timer works.
- PR HUD works after a new best.
- Workout completion works.
- Home screen loads promptly.
- Stats screen loads.
- Volume analytics loads without clipped charts.
- Recovery map tap-to-explain works.
- Progress photo add and compare works.
- Backup Center opens.
- Encrypted backup export works.
- SQLite export works.
- JSON restore path previews correctly.
- SQLite restore path previews correctly.
- CSV import path previews or imports correctly.
- Monet theme has no invalid color crash.
- Light theme remains readable.
- AMOLED theme remains readable.
- No active Expo imports in `src`.

## Build Verification Commands

```powershell
npm run validate:plans
cd android
.\gradlew.bat assembleRelease
adb install -r "Z:\ironlog\android\app\build\outputs\apk\release\app-release.apk"
adb shell monkey -p com.ironlog.app -c android.intent.category.LAUNCHER 1
adb logcat -d -t 500 | Select-String -Pattern "FATAL EXCEPTION|ReactNativeJS|com.ironlog.app"
```

## Release Asset Checklist

- `app-release.apk`
- SHA256 checksum.
- 8-10 screenshots.
- Release notes.
- Privacy policy link.
- Terms link.
- License link.
- Support email: `ironlogsupport@gmail.com`

## GitHub Release Title

`IronLog v2.0 Stable - Offline-first lifting tracker for Android`

## GitHub Release Tag

`v2.0.0-stable`

Before creating the tag, confirm package version, Android versionCode, and Android versionName are aligned with the release.

## GitHub Release Body

IronLog v2.0 Stable turns IronLog into a complete Android lifting tracker: fast workout logging, 900+ validated exercises, 31 built-in program templates, program intelligence, recovery maps, progress tracking, smart notifications, progress photos, body tracking, and encrypted local-first backup.

This release is for lifters who want serious tracking without giving up control of their data.

Highlights:

- Fast active workout logging with set editing, rest timer, warmups, plate calculator, PR detection, and workout summaries.
- 919 validated resolved exercises with aliases, favorites, filters, and custom exercises.
- 31 built-in program templates across beginner, strength, hypertrophy, aesthetic, home, calisthenics, specialization, and conditioning categories.
- Muscle analytics, volume trends, push/pull/legs balance, and next-week guidance.
- Front/back recovery maps with confidence labels and train/maintain/back-off recommendations.
- Bodyweight, body measurements, progress photos, and side-by-side photo comparison.
- Smart notifications with quiet hours, caps, cooldowns, snooze, and decision logs.
- Encrypted local backups, Google Drive backup targets, SQLite export/import, CSV import/export, and OpenWeight interoperability.
- AMOLED, dark, light, and Monet-inspired themes.

Upgrade safety:

- Updating over an existing install should preserve data if the package and signing key are unchanged.
- Uninstalling can remove local data. Create an encrypted backup or SQLite export first.
- Google Drive backup is optional and backup-oriented.

Install:

- Download the APK from release assets.
- Install normally on Android, or use `adb install -r app-release.apk`.

Support:

- Email: `ironlogsupport@gmail.com`

License:

- IronLog is source-available for personal and non-commercial use. Commercial use requires separate permission.

## Play Store Short Description Draft

Offline-first gym tracker with programs, recovery maps, analytics, and encrypted backups.

## Play Store Full Description Draft

IronLog is a serious lifting tracker for Android. Log workouts quickly, follow structured programs, track progress, understand recovery, and keep control of your data.

Built for lifters who want more than a simple set log, IronLog combines fast gym logging with program templates, muscle analytics, recovery maps, body tracking, smart reminders, and encrypted backup.

Key features:

- Fast workout logging for sets, reps, weight, notes, and rest.
- 900+ validated exercises with search, aliases, filters, favorites, and custom exercises.
- Built-in program templates for strength, hypertrophy, beginner training, home workouts, calisthenics, conditioning, and specialization blocks.
- Program insights, adaptive targets, and progression context.
- Volume analytics, personal best tracking, estimated 1RM, and weekly summaries.
- Front and back recovery maps with practical train, maintain, or back-off guidance.
- Bodyweight, body measurements, and progress photos with compare mode.
- Smart notifications with quiet hours and anti-spam limits.
- Encrypted local backups, SQLite export/import, CSV import/export, and optional Google Drive backup.
- AMOLED, dark, light, and Monet-inspired themes.

IronLog is local-first. Your training data stays on your device unless you choose to export or back it up.

Support: `ironlogsupport@gmail.com`

## Final Publisher Note

Do not publish this release page until the stable release gate passes on a real Android device.
