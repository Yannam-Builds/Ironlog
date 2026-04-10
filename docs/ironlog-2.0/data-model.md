# IRONLOG Data Model: Current Reality and Pending Delta

## Status Note

SQLite-backed core training entities are already in place and shipping in beta.
This document captures what remains to complete parity and interoperability.

## Stable Baseline (Already Present)

- sessions, exercises, and sets
- plans and plan days
- bodyweight and measurements
- custom exercises
- derived analytics entities used by shipped intelligence surfaces

## Pending Data Deltas

### 1) Import/Interop Domain

Add durable entities for deterministic import workflows:

- `import_jobs`
  - source (`strong_csv`, `hevy_csv`, `openweight_json`)
  - created_at, status, dry_run flag, summary payload
- `import_job_items`
  - row/session references, mapping decisions, duplicate flags, warning/error payload
- `exercise_aliases`
  - alias text, canonical exercise id, source, confidence, verified flag

### 2) Export/Restore Parity Domain

Close versioned parity gaps so shipped 1.1.0-beta state can round-trip safely:

- notification policy and scheduler decision state
- manual recovery entries and related metadata
- streak and milestone state
- weekly summary state and related derived pointers

### 3) Portability Contract

OpenWeight is an interchange layer only:

- IRONLOG internal SQLite remains source of truth
- OpenWeight export/import maps core entities deterministically
- richer IRONLOG fields are attached as `ironlog:*` extension payloads

## Migration and Compatibility Notes

- preserve idempotent legacy migration markers and verification checks
- never delete user history on app updates
- uninstall continuity relies on backup/export restore, not local sandbox retention
- reject imports with invalid schema or incompatible versions before writes
