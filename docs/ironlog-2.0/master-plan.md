# IRONLOG 2.0 Master Plan

## Product Goal
IRONLOG 2.0 upgrades the current offline-first workout logger into a serious training intelligence system. The release keeps logging fast, keeps recommendations deterministic, and makes analytics explain something useful instead of showing disconnected charts.

## Release Structure
1. Milestone 1: Phase 0 + A + B
2. Milestone 2: Phase C + D
3. Milestone 3: Phase E + F
4. Milestone 4: Phase G + H

## Milestone 1 Scope
- SQLite-backed training persistence and migration bridge
- Volume interpretation engine with realistic comparison dataset and anti-repetition rules
- Exercise profile classification
- Deterministic progression, plateau, deload, and substitution logic
- Fine-grained muscle contribution inference with confidence-aware fallbacks
- Rebuilt muscle analytics, recovery windows, and imbalance detection
- UI integration on Home, Active Workout, Volume Analytics, and Recovery Map

## Architecture
- `AppContext` remains the UI facade.
- Training data lives in SQLite and is mirrored to the legacy AsyncStorage keys during the transition.
- Business logic moves into reusable domain services:
  - `src/domain/storage`
  - `src/domain/intelligence`
- Existing screens consume structured payloads instead of screen-local heuristics.

## Data Flow
1. App boot runs legacy AsyncStorage migrations.
2. Boot then ensures the SQLite schema exists.
3. Legacy data is migrated into SQLite one time and marked with `@ironlog/v2SqliteMigrated`.
4. `AppContext` hydrates plans, history, and bodyweight from SQLite.
5. User actions write through repositories, then mirror back to legacy keys for backward-compatible export and restore.
6. Intelligence engines derive:
  - exercise profiles
  - muscle contributions
  - progression suggestions
  - recovery snapshots
  - session summaries
  - comparison usage history

## Rollout Order
1. Docs and specs
2. SQLite foundation and migration
3. Exercise profile + muscle contribution catalog
4. Progression and volume interpretation engines
5. Analytics and recovery aggregation
6. Home and workout logging integration
7. Analytics and recovery screen upgrade
8. Verification and stabilization
