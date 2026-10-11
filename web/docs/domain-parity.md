# Domain and portability fidelity

This implementation is local-first. It does not contact native app storage, contain private backup fixtures, or synchronize with Android automatically. Keep original Android backups when importing them.

## Native rules ported

- `CreditedProof.kt`: eight non-warmup hard sets, three plus twenty minutes, or ten minutes of recognized cardio. Future workout starts do not earn credit.
- `StatsViewModel.kt`: the history clock is workout **startedAt**, including across midnight and ISO week boundaries.
- Native `duration_seconds` is retained independently of start/completion timestamps. `workoutDurationSeconds` uses this canonical duration when present; it does not infer zero length merely because an imported workout has equal timestamps.
- `IronLedgerEngine.kt`: global integrity penalties, same-day anti-spam multipliers, base set XP, first-performance baseline, >2.5% verified PR bonus, cumulative 125 × level² level costs, tenure/week/session/integrity/balance grade gates.
- `GamificationSummary.kt` and `StreakEngine.kt`: current daily and weekly streaks, inactivity break, ISO weeks and one weekly recovery makeup. A recovery circuit can be saved only while exactly one credited workout short of the weekly goal, once per ISO week.
- `RecoveryReadinessEngine.kt`: effort/type/duration dose factors, native exercise factors, two-phase fatigue decay, bounded probabilistic session combination, manual 48-hour wellness offset and pain flags. All muscle template, anchor, library and region constants were mechanically extracted from `MuscleContributionEngine.kt` into `src/data/native-muscles.json`.
- `TrainingIntelligenceEngine.generateWarmupSets`: 40/55/70/85%, 8/5/3/1 reps, nearest 2.5kg, bar floor and duplicate removal. At or below the bar weight there are no lower loadable warmups. Targets are pending, never credited until explicitly logged.
- `WorkoutSuggestionEngine.kt`: token-aware region matching, partial-coverage neutral weighting, stable earliest-day tie breaking.
- Progression uses comparable tracking/equipment occurrences, retained target/effort evidence, resolved exercise/plan/global policy, bounded increments and exact barbell loadability. Advice explains its source and changes inputs only after an explicit action. It remains a coaching heuristic.
- Native badge thresholds are evaluated from credited sessions and verified PRs. Unlocks are persisted additively by `reconcileBadges`; deleting history does not remove already-earned badges.

## Known limits (do not claim complete Android parity)

- Web retains immutable session exercise snapshots, multi-primary metadata, contribution maps and native region vocabulary. Exact IDs/names/aliases plus confidence-ranked fuzzy candidates are supported; unresolved or ambiguous imported exercises require explicit review rather than fabricated metadata.
- Finite physical plate quantities, odd-spare warnings and bounded plate calculation are supported. An unnamed current finite setup is exported as an active native gym named `Current setup`; unlimited-pair profiles remain a web-only compatibility case.
- Cloud activation requires explicit cloud mode, a configured model/validated endpoint and its memory-only session key. Goal-mode badges accumulate canonical modes only when a newly credited workout appears. Durable unlocks survive key removal/history deletion; mode evidence and its workout marker are portable. No Health Connect biometric blending exists on web.
- Native calibration, provenance, baseline reconstruction, badge dates/origins, recovery credit and supported preferences are restored. Canonical rows override stale extension copies. Cached native Ledger events are not replayed as browser event identities: web XP is rebuilt from available history. Retain original backups for unsupported native settings and event history.
- Android JSON includes photo URIs but not photo bytes. Those references are reported as skipped; only web ZIP backups preserve actual web photo blobs. Web ZIP preserves the complete web snapshot and applies compressed/expanded size limits before parsing.
- Android v1 exports retain the required `ironlog_watermelon_export` literal and relational table names. A `webExtension` retains web-only session/check-in/gym fields when roundtripping through this web codec; Android ignores this extension. Android may drop it when re-exporting.
- Native backup measurement fields are bodyweight, waist, chest, arm and thigh. Other web measurement types survive web ZIP / webExtension but are not native relational rows.
- There is no browser notification guarantee, native foreground service, or background timer guarantee in this data layer. Rest deadlines are persisted absolute timestamps.

## Integrity and verification

Database version 2 stores the bundled exercise catalog as one structured-clone row to avoid thousands of indexed startup writes in WebKit. Existing version-1 exercise rows are retained untouched and take precedence as user overrides; snapshot reads and workout resolution merge both sources. Full restores retain all supplied exercise metadata as overrides. This migration does not delete user data.

Dexie transactions serialize writes across tabs. Workout recipes use expected revisions; stale writes fail visibly. Finish waits earlier local queued writes and refuses to complete over a failed pending write. Successful unrelated mutations never clear that warning: the UI must explicitly confirm acknowledgment through `acknowledgeFailedMutation` before finishing. The current failed-write marker lives in the running tab; it is not a durable pending-write journal across browser reloads.

Import/restore validates before clearing, and commits all tables atomically. A full Android backup must include exercise, plan, plan-day, plan-exercise, workout, workout-exercise and workout-set tables; missing core tables are rejected, not treated as empty data. Missing non-core sections produce explicit partial-backup warnings. Malformed table rows, unknown lifecycle values, invalid workout timestamps, and web extensions that omit canonical workouts/sets/measurements are rejected.

Web photo manifests are validated before any mapping: missing/null/malformed arrays, duplicate IDs or paths, missing bytes and unreferenced photo files reject the archive. Nested plan IDs, workout slots, globally unique logged-set IDs, pending/logged warmup collisions, completion chronology, and singleton active workout are checked during full restore.

Synthetic fixtures only: `tests/data.test.ts`, `tests/engine.test.ts`, `tests/codecs.test.ts`. The focused suite includes concurrent-start/revision/finish tests, failure rollback, warmup order, swap scope, native XP/recovery/streak clocks, backup bytes and malformed/orphan import reporting. Browser/device QA is separate and must not be inferred from these unit tests.

## 6 September 2026 parity slice

- Recovery dose is region-local, failure encodings are equivalent, and timed sets do not inherit a high-repetition multiplier. Exact half-lives remain product heuristics rather than medical measurements.
- Tracking-aware logging/editing distinguishes external load, bodyweight, added load, assistance, duration, weighted duration and distance. Unknown imported tracking stays visible and preserves its original values.
- Recent performance uses stable exercise identity, completed nonfuture workouts and explicit all-workout/plan-day scope. It labels setup changes instead of presenting unlike records as direct targets.
- Persistent exercise setup reminders use portable `exercise_next_note:<id>` keys and stay separate from per-session notes. Rest context shows the last performed set.
- Android export now includes the active finite plate setup even when the user left its optional gym name blank.

Canonical Android workout/set values, immutable metadata and exercise associations override stale web-extension copies during restore. Extension-only drafts remain available; moving a canonical set to another extension exercise is rejected.

Verification for this slice: 24 unit/component files, 138 tests, production build and the scoped output verifier pass. Browser acceptance is recorded separately after the final multi-engine run; these checks do not establish physical Safari/Home Screen behavior or literal platform identity.

## 6 September 2026 visual parity slice

The five primary routes now follow the current Compose screen hierarchy and navigation geometry. Web Home derives its 30-day session count, working-set count, average duration and weekly volume from the same credited-proof and typed-tracking rules already used by the domain engine.

The September local builder description below was superseded by the manual prompt/catalog/paste review workflow and optional explicit Cloud AI generation. Browser Gemini Nano is not provided. Provider settings are portable; credentials are memory-only and excluded from every backup. Built-in mode hides cloud insights/generation while preserving the configuration. Significant plan changes are reviewed before atomic import.

## Current closure evidence

The [remaining parity validation](../../docs/reviews/2026-10-11-remaining-parity-validation.md) records the current implementation, runtime evidence, release gates and environmental limits. Earlier dated verification counts below are historical, not current acceptance. No literal cross-platform pixel identity, live-provider availability, physical Safari/OEM certification or guaranteed background execution is claimed.
