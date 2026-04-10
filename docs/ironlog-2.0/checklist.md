# IRONLOG 2.0 Implementation Checklist

## Foundation
- [x] Create 2.0 docs
- [x] Add SQLite dependency
- [x] Create SQLite schema and helpers
- [x] Add one-time AsyncStorage to SQLite migration
- [x] Mirror write paths during transition

## Phase 0 + A + B
- [x] Volume interpretation engine
- [x] Exercise profile engine
- [x] Muscle contribution engine
- [x] Progression engine
- [x] Plateau and deload detection
- [x] Substitution ranking
- [x] Session quality summary
- [x] Home screen integration
- [x] Active workout integration
- [x] Volume analytics rebuild
- [x] Recovery map rebuild

## Phase C + D
- [x] PR event engine
- [x] Exercise trend dashboards
- [x] Workout performance score
- [x] Consistency metrics
- [x] Program insights
- [x] Goal modes
- [x] Missed-workout rescheduling

## Phase E + F
- [x] Recovery score
- [x] Manual recovery inputs
- [x] Streaks and milestones
- [x] Weekly summary card
- [x] Notification scheduler foundation

## Phase G + H
- [x] SQLite-backed export and restore
- [x] Share cards expansion
- [x] Faster logging defaults
- [x] Better history compare UX
- [x] Settings expansion

## 2.0 Closure Gates
- [x] Balanced notification policy contract (`max 1/day`, `max 3/week`, quiet hours, per-topic cooldowns)
- [x] Notification arbitration + suppression reasons with local decision log
- [x] Notification control surface: profile, snooze, cap mode, cooldowns, lead-time
- [x] First-launch restore entrypoint for uninstall/reinstall users
- [x] Restore flows for encrypted backup + SQLite export
- [x] Full regression matrix run (migration idempotency, export round-trip, progression/recovery determinism)
- [x] Final release-candidate QA signoff (logic suite + release build verification)
