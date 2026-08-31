# OpenGym feature reference: six independent Kotlin improvements

Reviewed 31 August 2026. The useful next step is to make IronLog's existing features work together: a rescheduled day should agree with reminders, a cue should have an explicit lifetime, and a record should show the sets that produced it. This report expands the [earlier comparison](opengym-review-2026-08-31.md) beyond progression and exercise substitution. It proposes requirements, not adopted competitor code or completed native features.

| Order | Improvement | Existing IronLog foundation | Smallest useful release |
| --- | --- | --- | --- |
| 1 | Source-linked exercise records | E1RM/load/reps/volume/history screens | Correct metric labels, eligibility rules and source-set drill-down |
| 2 | One exercise picker contract | Search, aliases, favorites, gym profiles | Same filters and ordering in Library, Plan and Workout |
| 3 | Notes with explicit lifetimes | Plan, session, exercise and set notes | Separate permanent setup cues from dated next-session reminders |
| 4 | Date-specific schedule exceptions | Ordered plan days, history calendar, daily reminders | Skip/assign/reset one date without altering the plan |
| 5 | Reusable day prescriptions | Copy Day, duplicate plan, rest, warmups, supersets | Atomic day-copy preview and an immutable session prescription |
| 6 | Clearly scoped portable data | Native backup/import, plan sharing, local ObjectBox | Show exactly what is shared, backed up and restorable |

The ordering is an implementation recommendation, not a ranking of competitor quality. Correctness work from the previous review remains a prerequisite for expanding the active-workout flow. Native source references below identify files and line numbers in the reviewed local dirty revision; they are not claims about published main.

## 1. Records should say what improved and show the evidence

OpenGym distinguishes loaded repetition work, unloaded repetition work, timed holds and cardio. Its Stats screen shows reps instead of an empty weight chart when an exercise has never had a positive logged load. It hides estimated 1RM for timed/cardio entries, and adds effort context only when at least three sessions have effort data. The 1RM helper returns the source load, reps and date, rejects nonpositive/nonfinite inputs and more than 12 reps, and excludes warmups. These are concrete display/eligibility rules, not proof of physiological accuracy. [Metric selection and effort display, Stats.jsx:345–419](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/views/Stats.jsx#L345), [estimate and source-set helpers, onerm.js:15–82](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/onerm.js#L15).

Tests cover formulas, invalid inputs, warmups and intensifier treatment. The Stats test file partly checks source text, so its existence does not establish correct interactive chart behavior. [1RM tests](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/onerm.test.js), [Stats contract tests](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/views/Stats.test.js#L9).

IronLog already has six progress tabs. A specific source inconsistency deserves repair before more charts: `buildExerciseTrendLocal` stores each session's **maximum** reps, but `computeMetricConfig` labels their average **AVG REPS/SET**. Two sessions with sets [10, 2] and [8, 2] therefore produce 9, whereas the actual per-set mean is 5.5. The local helper also applies an unbounded Epley expression; the generic `estimateOneRM` helper uses another unrestricted path. These are source findings, not results reproduced on the installed APK. Trend aggregation (`app/src/main/java/com/ironlog/app/ui/screens/stats/ExerciseProgressScreen.kt:109`), metric label (`app/src/main/java/com/ironlog/app/ui/screens/stats/ExerciseProgressScreen.kt:163`), generic estimate (`app/src/main/java/com/ironlog/app/ui/screens/stats/StatsScreen.kt:967`).

Independent acceptance requirements:

- A record specifies its metric: load, reps at a load, duration, or estimated 1RM. Tapping it opens the original workout and set, including units, effort and warmup status.
- A shared calculation contract powers live celebration, history and charts. Editing/deleting the source set recomputes the record; an equal result is not another all-time record.
- Choose and document the estimate's eligible rep range. Do not inherit the competitor's 12-rep cap as a scientifically established universal threshold. Show the formula and source set, and label an estimate as an estimate.
- Keep reps and added-load series separately selectable when a bodyweight exercise becomes weighted. OpenGym automatically switches the whole history's main metric after a positive load appears; that can obscure earlier rep-only progress.
- Test the [10, 2]/[8, 2] example, high-rep sets, bodyweight-only history, duplicate exercise occurrences, kg/lb display, warmups and deleted PRs.

Body measurements are not an obvious competitor gap to fill: the inspected OpenGym Stats view has bodyweight points and a first-to-last delta within 30 days, not a demonstrated broad circumference-analysis system. IronLog already has body measurement entities, trends, goals and photos. Improve missing-data and source-date explanations in those existing screens rather than claim another measurement tracker is needed. [OpenGym bodyweight delta, Stats.jsx:285–288](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/views/Stats.jsx#L285), IronLog measurements (`app/src/main/java/com/ironlog/app/data/repository/BodyMeasurementRepository.kt:24`).

## 2. Exercise discovery should preserve the user's intent across screens

OpenGym's live Library calls `matchExercise`, not merely the separate scoring helper present in the same module. This matcher requires every query token to appear somewhere in a cached corpus containing canonical/localized names, muscles, equipment and description. It removes accents and invalidates the cache when language changes. Tests explicitly cover reversed token order, mixed metadata, accents and language switching. [Actual Library call and filters, Library.jsx:23–28](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/views/Library.jsx#L23), [matcher, exercises.js:156–196](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/exercises.js#L156), [search tests](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/exercises.test.js#L20).

The Library exposes the active equipment profile and a temporary show-all action. Equipment chips derive from available results; an equipment filter that disappears is dropped. These are source-observed interactions, not a tested claim that its ranking is better. Favorites were requested publicly, but no favorites control appears in this inspected Library component. [Profile override and chips, Library.jsx:36–49](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/views/Library.jsx#L36).

IronLog is already stronger than a plain name search: it includes aliases, metadata and ranked fallbacks. However, Library subsequently sorts the returned results by favorite status and name, discarding the search helper's relevance order. Its normalizer strips non-ASCII letters rather than folding accented letters, and its broad fallback accepts any matching long token. Those behaviors should be explicit product choices. Search scoring (`app/src/main/java/com/ironlog/app/util/ExerciseUiFilters.kt:50`), Library filters and final ordering (`app/src/main/java/com/ironlog/app/ui/screens/settings/ExerciseLibraryScreen.kt:156`).

Independent acceptance requirements:

- Share one query/filter/result model across all pickers; preserve exact-name relevance before favorite boosts. A preferred exercise that fails an explicit filter stays excluded.
- Display the active gym and hidden-result count, with a temporary show-all action that does not rewrite the gym profile.
- Offer clearly separated broader matches if an all-token query finds nothing; do not mix them invisibly with exact matches.
- Preserve supported accents/scripts and custom aliases. Tests cover reversed word order, renamed custom exercises, gym switching and clearing filters.
- Keep existing favorites. Do not import competitor media or its catalogue assets as part of this work.

## 3. A session observation and a permanent setup cue need different lifetimes

OpenGym's note editor exposes three concepts: today's observation, a flag to surface it later, and a standing note keyed by exercise. The helper searches saved workouts backwards and returns the first matching pinned entry with nonblank text. Completion preserves note and pin only when there is content. [Editor, sheets.jsx:1530–1577](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/sheets.jsx#L1530), [lookup, history.js:242–253](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/history.js#L242), [lookup tests, history.test.js:825–854](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/history.test.js#L825), [completion tests](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/finish-workout.test.js#L60).

Important limits: this is not a consumable next-session reminder. No expiry/acknowledgment occurs in the helper. A newer unpinned note does not cancel an older pin; removing a newer pin can reveal an earlier one. “Newest” means array position, and `.find` selects only the first occurrence of an exercise within a workout. The inspected tests do not establish correct behavior for out-of-order imports or repeated exercise slots.

IronLog already stores notes on plan exercises, workout exercises and sets; it also exposes a global exercise-note settings map keyed by trimmed exercise name. Active-workout state inherits saved occurrence notes. A new cue feature must not overwrite or relabel these existing observations. Entity notes (`app/src/main/java/com/ironlog/app/data/objectbox/Entities.kt:114`), active note restoration (`app/src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt:581`), global name-keyed map (`app/src/main/java/com/ironlog/app/ui/viewmodel/AppDataViewModel.kt:203`).

Independent acceptance requirements:

- Keep session notes attached to their workout occurrence. A separate cue uses a stable exercise ID, optional plan-slot scope, source note/date and explicit active/dismissed state.
- Offer a permanent setup cue and a next-session reminder as distinct choices. Define whether acknowledgment consumes the reminder; do not imply expiry that never happens.
- Show the source date and edit/dismiss actions. Dismissing a cue must not resurrect an older cue without an explicit user choice.
- Test rename, plan deletion, reordered import, duplicate exercise occurrences, empty edits and backup restoration. Pain observations remain historical facts and are not automatically repeated as permanent technique instructions.

## 4. Reschedule a date without changing every following week

OpenGym resolves a date by checking a separate override map first: explicit rest wins; a valid routine override wins next; otherwise it uses the weekday assignment. The date sheet can assign a routine, mark rest or remove the override. A separate weekday sheet edits the recurring baseline. This is an assignment mechanism, not evidence of an atomic two-date move command. [Resolver, history.js:276–285](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/history.js#L276), [date and weekday sheets, sheets.jsx:1223–1264](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/sheets.jsx#L1223).

Its native reminder builder expands 60 future dates using the same resolver, suppresses dates with any completed workout, and skips today's elapsed reminder time. Tests cover rest overrides, routine overrides, weekly rest dates, completion suppression and elapsed time. Suppression is date-wide: one completed session can suppress another planned session that day. A rolling 60-day horizon also depends on future rescheduling. [Reminder builder, mobile.js:58–92](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/mobile.js#L58), [reminder tests](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/mobile.test.js#L14).

In the inspected Kotlin paths, plan days have ordering but no weekday field; Workout Calendar displays history and adds historical workouts; ReminderScheduler creates a daily periodic job. No date-override model was found in the searched main-source paths. This is a targeted absence finding, not a proof that no scheduling-related setting exists anywhere. Plan-day fields (`app/src/main/java/com/ironlog/app/data/objectbox/Entities.kt:95`), calendar (`app/src/main/java/com/ironlog/app/ui/screens/workout/WorkoutCalendarScreen.kt:103`), daily scheduler (`app/src/main/java/com/ironlog/app/services/ReminderScheduler.kt:10`).

Independent acceptance requirements:

- Preserve the user's choice between ordered rotation and explicit weekdays; adding exceptions must not silently convert a rotating plan into a weekly program.
- Resolve each planned occurrence through one domain function consumed by Home, Calendar and reminder generation.
- A move writes source skip and destination assignment in one transaction. If the destination is occupied, ask whether to add another session or replace that occurrence.
- Reset restores the baseline; completed history remains unchanged. Reminder suppression uses occurrence identity, not simply “some workout exists today.”
- Test midnight, timezone changes, daylight-saving boundaries, plan deletion, two sessions per day and moves undone after a reminder was scheduled.

## 5. Reuse the whole day prescription, including rest and warmups

OpenGym has reusable routines assigned to weekdays. At session creation, it builds set rows from the routine, applies a prescription, then the intensifier, while storing a target snapshot. Its share bundle preserves explicit rest, superset membership, note, warmup count and selected intensifier configuration. Tests check those fields through export/import and validate malformed rest/warmup values. This supports a complete prescription concept; it does not establish a separate training-day-template marketplace or versioned template system. [Routine scheduling, Plan.jsx:16–46](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/views/Plan.jsx#L16), [session builder](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/session-start.js#L9), [share fields](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/plan-share.js#L20), [round-trip tests](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/plan-share.test.js#L12).

IronLog already offers Copy Day and duplicate plan. The inspected `copyDay` appends exercises one at a time and copies the existing superset-group strings. It has no enclosing transaction in that function; whether an individual repository call is transactional does not make the whole multi-row operation atomic. Existing destination groups can also collide unless the copy remaps group identity. Copy Day implementation (`app/src/main/java/com/ironlog/app/ui/viewmodel/PlansViewModel.kt:205`), plan exercise prescription fields (`app/src/main/java/com/ironlog/app/data/objectbox/Entities.kt:124`).

Independent acceptance requirements:

- First improve Copy Day: preview append versus replace, perform one atomic repository operation, and assign fresh occurrence/group IDs while retaining exercise IDs.
- Capture prescribed sets, tracking mode, warmup status, rest, group membership and cues in the session snapshot. Future plan edits never rewrite performed work.
- Represent inherited rest, explicit rest and timer-off distinctly; zero must not ambiguously mean all three.
- Round-trip every supported prescription field through day-copy, plan-share and full backup. Reject or visibly report unsupported fields rather than silently flatten them.
- Use the existing superset state-machine recommendation for round-aware navigation. Test unequal set counts, completed warmups, failed saves and a running timer; this is correctness work, not a reason to add another superset toggle.

## 6. Make portable data scope visible; retain native local durability

OpenGym separates a plan bundle from a full-state backup. A plan bundle contains routines, weekly assignments and only referenced custom exercises; importing adds fresh routine IDs, while applying its schedule is optional and replaces the entire week. Full backup export serializes state, and restore warns that it replaces current data. These are useful scope distinctions. [Bundle and merge, plan-share.js:91–188](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/plan-share.js#L91), [backup export/restore, Settings.jsx:28–48](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/views/Settings.jsx#L28).

Limits to avoid inheriting: the inspected plan bundle carries raw weights but no top-level unit field; custom exercises are exported with reduced metadata; import can reuse customs by name/body part and drops unresolved exercise IDs. Those choices need stronger identity/unit contracts for IronLog. The prior report separately covers CSV same-day deduplication and preview/import inconsistencies.

On Android, OpenGym mirrors state to a private JSON file in addition to localStorage; normal saves are debounced 800 ms with a background flush. Its restore rule preserves a device-local active workout and rejects an older remote snapshot or dirty local replacement. Tests cover those branches and offline pull failure. This remains whole-state timestamp reconciliation, not demonstrated conflict-safe merging. File-save failures are swallowed; the inspected tests do not simulate Android process death, disk exhaustion or two offline devices editing the same record. [File mirror and separate connection data, mobile.js:20–53](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/lib/mobile.js#L20), [persist/restore, useStore.js:35–84](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/store/useStore.js#L35), [restore tests](https://gitlab.com/DuarteSantos8/opengym/-/blob/aac9f377d5edfd68b589a392724028ecc7a1502f/frontend/src/store/useStore.restore.test.jsx#L24).

IronLog should retain ObjectBox and its explicit mutation failures. Its database export already has a versioned envelope and photo records, but the inspected photo rows contain file URIs, not image bytes. A database JSON export must therefore not be described as a complete portable photo backup without checking the surrounding archive workflow. Native database export (`app/src/main/java/com/ironlog/app/data/repository/ImportExportRepository.kt:119`).

Independent acceptance requirements:

- Separate “Share a plan,” “Export workout history” and “Full backup.” Each preview lists included and excluded categories; personal notes, measurements, photos and credentials are never silently included in a public plan share.
- Specify canonical units and format versions. Remap imported identities explicitly; identical display names are not sufficient identity for two different movements.
- A full backup either embeds and verifies photo files or clearly says photos are excluded. A restore preview reports missing files and incompatible records before commit.
- Verify restore using synthetic data in a separate QA package, preserving the user's real database. A successful export is not evidence that restoration works.
- Keep workout logging available offline. If sync becomes a separately authorized project, add per-record revisions, deletion tracking, conflict visibility and a defined active-session ownership policy; do not copy whole-state last-writer-wins behavior.

## Evidence boundaries

OpenGym source was inspected at `aac9f377d5edfd68b589a392724028ecc7a1502f`, including the implementation and the specific tests cited. Primary links pin that revision. Jina Reader supplied a primary GitLab page; the existing temporary checkout supplied the remaining source reads. Competitor dependencies, tests, builds, containers and APKs were not executed. Test descriptions in this report mean tests inspected, not tests passed in this review.

IronLog references are repository-relative source paths and line numbers from the locally reviewed dirty working tree, not links to published main and not necessarily the installed release. No Kotlin source, user workout data, phone state, competitor source or media was changed. This document is the only file authored by this research subtask. Requirements and examples are independently written. Existing licensing/media cautions remain in the earlier review.

Agent Reach was used for primary-source acquisition; the writing and information-hierarchy skills shaped the decision-first structure. Agent Reach's update check reported installed v1.5.0 current. This was a targeted feature review, not a full audit or a runtime usability study.
