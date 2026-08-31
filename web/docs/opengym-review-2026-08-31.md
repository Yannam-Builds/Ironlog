# OpenGym comparison: what IronLog should improve

Reviewed 31 August 2026. **No evidence of copying was found in the inspected material.** OpenGym is a useful comparison for workout interaction and explicit progression policies. IronLog should first close persistence, import and recommendation inconsistencies, then improve those interactions. Adding more scores or copying another recovery formula would not resolve the reported reliability problems.

This is a source/product review, not a certification of either app. No Kotlin files were edited, no competitor code or artwork was incorporated, and no competitor dependencies, containers or APKs were executed. The only product change in this pass is the transparent website logo.

## What the evidence says about copying

OpenGym's GitLab project was created on 23 August, but its retained history begins with commit `29f5f64f8eac8b66af9b5d9ce912f287af7191cf` dated 18 July 2026. Its README describes migration from earlier hosting. Treating the GitLab creation date as the app's beginning would therefore be incorrect. IronLog's available local history reaches 17 May 2026. Commit timestamps show the histories available to this review; they do not establish original conception or prove copying. [GitLab project](https://gitlab.com/DuarteSantos8/opengym), [earliest retained OpenGym commit](https://gitlab.com/DuarteSantos8/opengym/-/commit/29f5f64f8eac8b66af9b5d9ce912f287af7191cf), [migration account](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/README.md).

The inspected implementations are different: React/Zustand/Capacitor and a Node server versus Kotlin/Compose/ObjectBox. A case-insensitive tracked-source search found no references to IronLog, Forge Fox, Iron Ledger or Yannam. OpenGym attributes its body geometry to MuscleMap. This was not an exhaustive code-similarity or image-forensics analysis; an absence of name matches cannot prove independence. Shared concepts such as workout plans, dark themes, body maps, supersets and streaks are insufficient evidence of copying. [Frontend dependencies](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/package.json), [asset provenance](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/NOTICE.md).

## Most valuable changes, in order

| Priority | Improve in Kotlin                                           | Why it matters                                                                                                                                      | Acceptance example                                                                                                                    |
| -------- | ----------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| P1       | Unify progression and unit handling                         | The active screen currently suggests an increase without evaluating target completion; the historical-load path also lacks display-unit conversion. | Missing prescribed reps does not recommend an automatic increase; the same stored kg load displays correctly in lb.                   |
| P1       | Replace ad-hoc CSV import paths with tested source adapters | Current Hevy preview and import disagree about columns; date, unit and duration handling can corrupt interpretation.                                | A real-format synthetic Hevy export with quoted notes, reordered headers, lb and two workouts on one day round-trips correctly.       |
| P1       | Make plan + session substitution one awaited operation      | Session changes can fail after the plan has already changed; logged sets currently block substitution.                                              | Changing a movement preserves completed history, changes only the intended plan slot and cannot partially succeed.                    |
| P1       | Extract superset navigation/rest into a pure state machine  | Existing code uses the last displayed group member, not the last member with unfinished work, to choose rest.                                       | A three-set exercise paired with a two-set exercise still advances/rests correctly in round three.                                    |
| P1       | Make recovery explainable and consistent                    | A number that changes unexpectedly between screens or restarts cannot be trusted, regardless of formula complexity.                                 | Home and Recovery display one snapshot with its calculation time, limiting muscles and inputs; reopening alone adds no training dose. |
| P2       | Add date-specific schedule overrides                        | Moving Tuesday's session should not silently rewrite the recurring plan.                                                                            | Move one workout to Wednesday, keep future Tuesdays intact, and update reminders consistently.                                        |
| P2       | Reduce repeated effort-entry and exercise-search work       | Public feedback identifies tiny effort controls and large exercise lists as friction.                                                               | One-tap effort presets retain an exact-entry alternative; existing favorites and gym filters work in the active-workout picker too.   |
| P2       | Add dated, explicitly pinned technique notes                | Plan cues and one-session observations need different lifetimes.                                                                                    | A pinned setup cue survives a plan change; a temporary pain observation is not silently repeated forever.                             |

These priorities are recommendations, not changes completed in this pass. P1 here means important correctness work before expanding intelligence, not proof of a crash on the installed phone.

## Concrete Kotlin findings

### 1. The active workout recommendation is too simplistic—and has a unit boundary problem

`ActiveWorkoutScreen.kt:3463`, `buildProgressionSuggestion`, selects the highest previous load and adds 2.5 for kg or 5 for lb; bodyweight uses maximum reps plus two. It does not receive prescribed targets, missed-set outcomes, a progression policy or a deload flag. This is the actual suggestion shown under LAST SESSION, even though other training-intelligence features exist elsewhere.

`loadGhostData` at line 377 copies `WorkoutSetEntity.weight` directly into `GhostSet`. The repository returns stored rows, and the card passes that ghost unchanged. The suggestion then adds an lb increment to the stored kg number and labels the result lb. For a stored 100 kg set, the current helper produces `105.0 lb`; a display-space +5 lb suggestion would be approximately 225.5 lb before equipment rounding. This is a source-traced defect, not a phone reproduction. The COPY path and last-session ordering should be tested alongside it.

OpenGym derives named policies from historical outcomes and saved target snapshots: hold, progress, deload, rep-range and timed progression. Its advice includes reasons. Independently implement a Kotlin policy engine with equipment-aware rounding, an explicit OFF option, manual overrides and immutable historical prescriptions. Start with linear and double progression; more named programs can follow. Do not present a particular increment or stall threshold as universally correct. [OpenGym progression implementation](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/progression.js).

### 2. Existing Hevy support needs repair, not another Hevy button

IronLog already offers Hevy import. In `DataPortabilityScreen.kt:408`, preview requires header substrings including `date` and evaluates columns 0–3 positionally. The importer at line 587 resolves column indexes independently and also accepts `start_time`/`start`. A file accepted by one interpretation can be rejected or miscounted by the other.

The import path takes the first ten date characters, groups by that value, uses raw weight without source-unit conversion and assigns every imported workout 1,800 seconds. It does not preserve effort, warmup types or notes in this path. Invalid date parsing falls back to the current clock. A fabricated 30-minute duration can also affect duration-based rewards downstream; this needs an end-to-end regression.

Use one parser for preview and commit. Preserve source session IDs/timestamps, explicit units, notes, set types and unknown-duration status. Preview unresolved exercises and let users map or create them. Never invent a current date or duration to make an import succeed.

OpenGym offers useful format adapters, per-row unit handling and unresolved-exercise reporting. However, its CSV parser groups workouts by day and merge skips days already present. **Do not copy that deduplication rule:** two real sessions on the same day are valid. [Import guide](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/docs/DATA_IMPORTS.md), [CSV parser and merge](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/import-csv.js).

### 3. Substitution should preserve work already done

IronLog already has custom-exercise selection and THIS SESSION / SESSION + PLAN choices. But `swapExercise` at line 943 rejects any logged sets. The plan branch at line 1916 updates the plan first, then starts an unawaited session mutation. These operations can diverge. Its row lookup also tries the current display index before exercise identity, which deserves a reordered/duplicate-slot regression.

OpenGym preserves a logged occurrence and, after confirmation, inserts the replacement beside it; it never relabels the old sets. Use that product behavior with IronLog's stable workout/plan-slot IDs, not index-based mutation. The UI can say: “Keep your completed bench sets and continue with dumbbells?” Plan scope should affect future prescriptions while keeping historical exercise names intact. [Swap behavior and tests](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/active-exercise-swap.js).

### 4. Superset logic needs round awareness

At `ActiveWorkoutScreen.kt:1466`, rest is enabled only for the last displayed member of a group. At line 1501, auto-navigation finds the next member after the current index; it does not wrap to unfinished earlier members. It also schedules navigation immediately after calling an asynchronous save. These conditions expose unequal-set-count, failed-write and round-transition cases.

Keep superset membership separate from the current round and completed set count. Advance only after a successful save, skip exhausted members, allow a configured inter-exercise transition, and start one appropriate round rest. Editing or rechecking an old set must not create another transition. OpenGym has focused tests for these cases, including preserving an already-running timer. [Superset state machine](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/supersetFlow.js), [regression cases](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/supersetFlow.test.js).

## Recovery: improve trust before adding precision

IronLog's current `RecoveryReadinessEngine` already includes effort, exercise factors, a two-phase decay, accumulated sessions, pain flags and a bounded wellness adjustment. OpenGym uses a different workload model with a fixed 36-hour fatigue half-life and a separate time-since-training “strength” model. Neither implementation demonstrates validation of its exact percentages against measured recovery in this review. More parameters do not establish greater accuracy.

OpenGym's best transferable contribution is its test discipline: unit equivalence, history deletion, old imports, saturation, pure recomputation and time decay. Its treatment of future workouts and warmups differs from IronLog's contracts: future workouts can contribute immediately, and completed warmups contribute fatigue. Those are documented/tested choices, not behavior to port accidentally. [Recovery engine](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/recovery.js), [recovery tests](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/recovery.test.js).

Recommended IronLog design:

- One versioned recovery snapshot for Home, map, next-workout suggestion and widgets, calculated with one clock and timezone.
- Show “estimated recovery,” recent relevant workout, affected muscles, calculation time, check-in age and data completeness. Do not invent a statistical confidence interval.
- Explain why 78 appeared: workload contribution and wellness adjustment, not a claim that the muscle is biologically 78% recovered.
- Keep unknown custom exercises visibly unmapped until resolved. `WorkoutSuggestionEngine` currently relies on name keywords even though richer muscle metadata exists elsewhere; route it through the same resolver as the map.
- Test identical output before/after restart, kg/lb equivalence, future proof exclusion, delete/edit monotonicity and pain overrides. Test that deleting a hard set cannot make recovery worse.
- Distinguish a recovery estimate from medical clearance. New or persistent pain should not be summarized as a reassuring green score.

Finer muscle display can make the map more informative, but finer anatomy alone does not make the estimate more accurate. Individual calibration against repeated check-ins and performance would be a separate research/validation project.

## UX lessons supported by screenshots and public feedback

The repository's workout screenshot places the current exercise, previous performance, recommendation reason and set controls together. That is useful hierarchy. Its Home screenshot makes today's planned session prominent. IronLog should preserve its own typography, monochrome mark, Forge Fox and Ledger while adopting that focus—not reproduce the competitor screen pixel for pixel. These are repository screenshots, not a live-device usability evaluation. [Workout screenshot](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/assets/screenshots/workout.png), [Home screenshot](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/assets/screenshots/home.png).

Public issues read on 31 August report effort-entry friction, requests for favorites, resume/navigation confusion and sync problems. These are individual reports, not prevalence estimates, and an open issue may refer to an older version. In particular, active-swap functionality exists in the inspected source despite a still-open exchange request. [Effort entry #32](https://gitlab.com/DuarteSantos8/opengym/-/work_items/32), [favorites #6](https://gitlab.com/DuarteSantos8/opengym/-/work_items/6), [resume #21](https://gitlab.com/DuarteSantos8/opengym/-/work_items/21), [sync #33](https://gitlab.com/DuarteSantos8/opengym/-/work_items/33).

IronLog already has favorites and equipment profiles; first reuse them consistently in every picker. Offer labeled effort presets plus exact input, and never infer precise RIR from a broad category without retaining that uncertainty. Keep a visible current-exercise/resume action, distinguish pending from logged rows, and make delete/undo reliable with 48 dp targets.

OpenGym's date override and pinned-note helpers are useful interaction models. A rescheduled workout should be an exception, not a rewrite of the recurring program. A pinned cue should show its source date and have an explicit unpin action. [Schedule and pinned-note helpers](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/history.js).

## What not to bring into IronLog

- **Do not transplant its persistence architecture.** OpenGym serializes broad state to localStorage, mirrors mobile state to a file, and uses timestamp-based server reconciliation. IronLog should retain transactional ObjectBox writes and explicit failures. If sync is ever added, design conflict resolution independently; it remains out of scope for the current web release. [Store](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/store/useStore.js), [mobile persistence](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/mobile.js).
- **Do not copy AGPL source into the current Personal Use licensed app casually.** OpenGym's notice identifies AGPLv3 and an app-store exception; GNU explains the additional network-source obligations. Use independently authored implementations of product requirements, and obtain a license review before any code adaptation. This is a reuse precaution, not a legal opinion on a specific combined work. [License](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/LICENSE), [GNU explanation](https://www.gnu.org/licenses/why-affero-gpl.html).
- **Do not import its exercise animations.** OpenGym's own notice explicitly says the media ownership is unresolved and not licensed to downstream users by its software license. Downloading at runtime does not establish permission. Use owned or separately cleared assets. [Media warning](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/NOTICE.md).
- **Do not make feature count the release gate.** Stable update migrations, trustworthy retained logs, consistent recovery and fast one-handed logging directly address this app's reported problems. Accounts, coach roles, writable MCP and additional metrics can wait.

## Proposed Kotlin verification slices

1. **Correctness:** synthetic Hevy/Strong fixtures, kg/lb ghost rows, missed-target progression, reordered duplicate substitutions, failed plan/session write rollback.
2. **Workout flow:** unequal supersets, timer resumption, custom replacements after partial completion, pending warmups, note edits, rapid log/delete and finish ordering.
3. **Recovery:** shared-snapshot parity across screens/widgets, injected clocks, restart and timezone cases, invalid metadata, future records and pure monotonic properties.
4. **UX:** small screens, 200% font scale, TalkBack, opaque overlays, keyboard-aware controls, one-handed effort entry and schedule exceptions.

Run these on synthetic data in the separate QA package. Any physical-phone update still requires a verified signed release installed with data-preserving replacement. No such build/install was performed here.

## Review provenance and limits

OpenGym: `aac9f377d5edfd68b589a392724028ecc7a1502f` (v1.2.14). Examined README/history/notices, dependency manifests, progression, recovery, import, swap, completion, superset, schedule, persistence and selected tests; viewed repository Home/workout screenshots and current public issues. This is a deep targeted comparison, not an audit of every file or a passed OpenGym test run.

IronLog: read-only dirty working tree at `Z:/KOTLIN/UnifiedPort`, HEAD `b851eb0575a07d6ca9487aa3a1ad710d189e6457`. Findings refer to working files, not necessarily the installed APK or public main. SHA-256 anchors:

| Native file                                      | SHA-256                                                            |
| ------------------------------------------------ | ------------------------------------------------------------------ |
| `ui/screens/workout/ActiveWorkoutScreen.kt`      | `EF514D624FEE9611F898FA0BFD115D3973FCA588FD13A2CE2CF929C209020C64` |
| `ui/screens/settings/DataPortabilityScreen.kt`   | `E3923C78582B989BEB6FF46AEF66DF2C94D3E1E600BE1A5AA26CCFAB5C382515` |
| `domain/intelligence/RecoveryReadinessEngine.kt` | `650E62DA7F717E54A083BBA903ACC5D04932EEC53C89F0B365178E4D92603BAF` |
| `domain/intelligence/WorkoutSuggestionEngine.kt` | `E6027BB3B3F01D8C01AAB5440ADDC38D267DD47F1D8869EA9AAF487D1803B76E` |

Paths in the table are relative to `app/src/main/java/com/ironlog/app/`. Agent Reach supported source discovery; code-review and competitive-brief workflows separated source evidence, user reports and recommendations. No plagiarism accusation or external issue/comment was published.
