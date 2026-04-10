# Changelog

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
