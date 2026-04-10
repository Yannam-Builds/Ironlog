# Changelog

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
