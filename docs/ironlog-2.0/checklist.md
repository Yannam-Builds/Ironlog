# IRONLOG Android Checklist

## A. Shipped Beta Closure Snapshot

These items are already shipped in 1.1.0-beta and should not appear under upcoming roadmap copy:

- [x] progression, plateau, deload, and substitution suggestions
- [x] muscle analytics and recovery map windows (`Workout`, `7D`, `30D`, `Program`)
- [x] PR/e1RM/exercise trend dashboards
- [x] program insights and goal mode controls
- [x] recovery score plus manual recovery check-ins
- [x] streaks, milestones, weekly summary card
- [x] notification policies, quiet hours, cooldowns, snooze, decision logging
- [x] restore wizard and SQLite export/import foundation
- [x] share cards expansion and in-workout add/edit/delete improvements

## B. Pending Execution Checklist

### Phase 0 - Doc Truth Cleanup
- [ ] remove stale upcoming claims in root README
- [ ] align beta release-note source with shipped scope
- [ ] mark historical 2.0 specs versus pending roadmap docs

### Phase 1 - Portability and Release Trust
- [ ] ship OpenWeight export codec with `ironlog:*` namespaced extras
- [ ] ship OpenWeight import parser with validation report
- [ ] create Import Center for Strong/Hevy/OpenWeight
- [ ] implement dry-run preview, duplicate checks, alias review, and import summary
- [ ] close export/import parity for currently missing app-state domains
- [ ] enforce Play Protect hardening baseline:
- [ ] `blockedPermissions` in `app.json` for unused risky permissions
- [ ] remove `RECORD_AUDIO` and `SYSTEM_ALERT_WINDOW` from Android manifest
- [ ] require release-keystore signing for release builds

### Phase 2 - Program and Adherence Depth
- [ ] block/mesocycle builder and per-exercise progression models
- [ ] optional `%1RM` and RPE/RIR prescriptions
- [ ] deload week builder plus anchor/accessory rule sets
- [ ] busy-week compression and fatigue-aware rescheduling
- [ ] adherence health score with clear rationale

### Phase 3 - Explainability and Hardening
- [ ] confidence/rationale payloads across intelligence surfaces
- [ ] readiness-adjusted context and persistence-based imbalance signals
- [ ] large-history performance profiling and cache/index tuning
- [ ] regression suite for migration/import/export/backup/analytics
- [ ] accessibility and critical error-state polish
