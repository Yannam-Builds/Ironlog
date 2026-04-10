# IRONLOG Android Master Blueprint (Post 1.1.0-beta Audit)

This document is the single source of truth for what is genuinely still pending after the shipped 1.0 baseline and 1.1.0-beta work.

## Current Product Truth

Already shipped in meaningful form:

- logging, plans, program picker, exercise library
- volume analytics, recovery heatmap, bodyweight analytics
- progression/plateau/deload/substitution suggestions
- PR/e1RM/trend dashboards
- program insights, goal mode, missed-day guidance
- streaks, milestones, weekly summary, recovery score, manual check-ins
- notification controls (quiet hours, cooldowns, snooze, policy caps, decision log)
- restore wizard, SQLite export/import foundation, share cards
- in-workout set edit/delete and add-exercise flows

This roadmap tracks only missing deltas.

## Phase 0 - Audit and Public-Doc Cleanup (Mandatory)

Objective:
- remove stale roadmap claims and align public messaging with shipped Android reality.

Scope:
- root README truth map (`1.0`, `1.1.0-beta`, `pending`)
- rewrite stale "upcoming" sections
- tighten beta release-note source
- label historical docs versus pending roadmap docs

Acceptance:
- no shipped feature remains mislabeled as upcoming
- docs and release-note source tell the same story

## Phase 1 - Portability and Release Trust

Objective:
- make migration and release trust the next clear strength.

Scope:
- OpenWeight interoperability layer (import/export only)
- Import Center for Strong CSV, Hevy CSV, OpenWeight JSON
- pre-import validation, duplicate detection, dry-run preview, alias review, import summary
- deterministic mapping first; no silent exercise renaming
- preserve richer IRONLOG metadata with `ironlog:*` namespaced fields
- close export/import parity for all shipped app-state domains
- Play Protect hardening baseline:
  - block risky unused permissions in `app.json`
  - remove `RECORD_AUDIO` and `SYSTEM_ALERT_WINDOW` from Android manifest
  - require release keystore signing (no debug cert release builds)

Acceptance:
- OpenWeight round-trip preserves core logs and namespaced IRONLOG extras
- Strong and Hevy imports complete with deterministic mapping and clear unmatched review
- release builds are keystore-signed and permission hygiene is documented

## Phase 2 - Program and Adherence Depth

Objective:
- move from recommendation layer to coach-grade planning depth.

Scope:
- advanced program builder: blocks/mesocycles, progression model per exercise
- rep-range templates, optional `%1RM`, optional RPE/RIR prescriptions
- anchor lift and accessory pool logic, deload-week builder
- adherence v2: busy-week compression, fatigue-aware reshuffle, adherence health score

Acceptance:
- users can build advanced plans without losing quick logging flow
- missed-day handling becomes actionable rescheduling, not only narrative guidance

## Phase 3 - Explainability and Quality

Objective:
- raise trust and stability for large-history users.

Scope:
- confidence/rationale copy across Home, Workout, Recovery, and Analytics
- undertraining persistence and readiness-adjusted context
- large-history performance profiling and cache/index hardening
- regression matrix for migration, import/export, analytics windows, and backup restore
- accessibility and error-state cleanup

Acceptance:
- no fake precision; weak-confidence outputs degrade safely
- logging and analytics remain responsive with large histories
- RC checklist can hit 100% with reproducible evidence

## Explicit Non-Goals

- iOS planning or implementation
- calorie tracking, meal planning, social feed, fake AI coach
- OpenWeight as internal source of truth
- full live cloud sync platform in this roadmap window
