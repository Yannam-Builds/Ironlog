# OpenGym reference audit — 31 August 2026

OpenGym is a useful reference for explainable progression, detailed historical workout entry, and statistics that show the data behind their conclusions. IronLog already has broader native intelligence and gamification surfaces. The strongest opportunities are refinements to those existing capabilities, with independently designed Kotlin implementations.

Recommended order: detailed backfill, auditable progression prescriptions, evidence labels, then small workout interaction improvements. Preserve IronLog's ObjectBox foundation and visual identity. Treat self-hosted sync as a separate product decision.

## Scope and evidence standard

This report concerns the exact project [DuarteSantos8/opengym on GitLab][og-repo].

| Item | Audited snapshot |
|---|---|
| OpenGym revision | `75fb168a03de09f995d05efd4fd2bfda2d595e0f` ([commit][og-commit]) |
| Commit timestamp | `2026-08-31T18:28:50+02:00` |
| Frontend package version | `1.2.14`; the changelog dates that release August 30 ([metadata][og-package], [changelog][og-changelog]) |
| IronLog comparison | Current working-tree source on 2026-08-31, including existing uncommitted work; not just the July installed release |
| Runtime verification | One synthetic fixture executed against OpenGym's pure recovery functions using the existing Node runtime |
| Visual evidence | Author-provided Home, Workout and Stats screenshots inspected; no running-build visual validation |

“Implemented” below means a production source path was inspected. It does not establish that the feature is correct in every case, present in a particular downloadable APK, or usable on the user's device. Architectural risks are code-based inferences. No comparative startup, frame-rate, memory or battery measurements were taken. No app installation, build, emulator, account or real workout data was used for this audit.

The separate native audit covers IronLog defects. This document records competitor behavior and implementation opportunities, rather than duplicating that issue list.

## Feature comparison

| Area | OpenGym source evidence | Useful direction for IronLog |
|---|---|---|
| Home and navigation | Compact weekly schedule, today's Start/Resume/Done state, weight/goal context, weekly streak and a persistent central workout action. Deeper analysis lives in Stats. [Home][og-home], [action bar][og-tabs] | Keep the next workout action prominent. Place optional intelligence behind that immediate task without copying the navigation layout or styling. |
| Progression | Linear, Greyskull, double-progression and timed policies; routine defaults and exercise overrides. Prescriptions return a state and reason, with previous-session context and a tappable explanation in Workout. [Engine][og-progression], [explanation][og-progression-ui] | Extend the existing effort-aware engine with inspectable policy and source-session context where useful. IronLog already provides HOLD/ADD_REPS/ADD_LOAD recommendations. [Native engine][il-progression] |
| Set logging | Warmup/work phases, bodyweight/no-load behavior, per-side repetition handling, timed/cardio modes, supersets, exercise rest, structured drop-set/rest-pause rows and pinned next-session notes. [Workout rows][og-sets], [tracking semantics][og-tracking], [notes][og-notes], [superset flow][og-supersets] | IronLog already has set types, effort, notes, supersets, bodyweight modes and plate loading. Evaluate specific additions such as explicit unilateral totals and next-session notes; do not assume the whole logging feature set is missing. [Native rows][il-setrow], [workout state][il-workout-state] |
| Past workouts | Date/time/duration plus routine or freestyle selection leads into the ordinary set editor. Same-day conflicts offer replace/add/cancel, and saved history is inserted chronologically. Backfills suppress PR celebrations. [Entry flow][og-backfill-ui], [ordering][og-backfill], [PR policy][og-backfill-pr] | IronLog's current calendar dialog saves a summary with empty exercise data. Add a detailed mode while keeping the quick name/duration log. Historical PR and ledger handling need an explicit IronLog policy. [Native calendar][il-calendar] |
| Muscle views | Separate balance, fatigue and strength views. Balance distinguishes all versus hard sets and identifies untrained muscles. Strength uses a training-recency decay heuristic. [Stats views][og-stats], [fatigue][og-recovery], [strength][og-strength] | Explain coverage, assumptions and time windows. A calculated recovery estimate or recency heuristic must not look like a direct measurement of tissue recovery or retained strength. IronLog already has effort-sensitive recovery. [Native recovery][il-recovery] |
| Effort statistics | RPE/RIR history is normalized to a common scale, then displayed in the user's chosen scale. Rated/total counts and a histogram are available; the average is suppressed below five rated sets. [Effort calculations][og-effort] | Show denominators and missing data alongside summaries. Unknown effort must remain distinct from zero, and an average should not imply that unrated sets were measured. |
| Estimated 1RM | Three selectable formulas, a 12-rep eligibility ceiling and the load/reps/date supporting the best estimate. [Estimator][og-onerm] | Give users the source set and estimator eligibility. IronLog's generic estimator has no high-rep ceiling; choose and test an original eligibility policy rather than silently adopting OpenGym's threshold. [Native estimator][il-metrics] |
| Motivation | Weekly streak, activity heatmap, PR recognition and completion summaries. No XP/quest/rank/leaderboard layer was found in the inspected production state and views. [Streak logic][og-streak], [Stats][og-stats], [completion][og-backfill-pr] | Iron Ledger, grades, badges and Forge Fox already give IronLog a distinct direction. Use OpenGym as a reference for a quieter information hierarchy, not as evidence that another gamification system is needed. [Native ledger][il-ledger], [widget presentation][il-widgets] |
| AI integration | Optional local read-only stdio MCP server exposes training facts to an external AI client. It imports the same analysis functions used by the app. [MCP entry point][og-mcp], [shared calculations][og-mcp-tools] | Reuse one typed training-facts projection across UI, exports and AI. MCP here is not an in-app model or generative coach, and a local bridge does not guarantee that a hosted AI client keeps the facts local. |
| Data ownership | React web UI with a Capacitor mobile path; server profiles use passkeys and JSON documents. Mobile source includes local mode and server pairing. Active workouts deliberately stay device-local. [Package][og-package], [passkey registration][og-auth], [pairing][og-mobile-onboarding], [server persistence][og-api], [native mirror][og-mobile] | Keep Compose/ObjectBox as IronLog's foundation. Any sync addition needs conflict, privacy, recovery and multi-device design of its own. |

### Accessible controls and contained media are useful patterns

OpenGym's interactive muscle paths expose a name, button role, selected state, keyboard focus, and Enter/Space activation. That is a concrete reference for making a diagram operable without touch. It does not establish that the entire app passes accessibility testing. A Kotlin equivalent can retain a native Canvas while supplying semantic actions or a synchronized textual region chooser. [Body map interaction][og-bodymap-interaction]

Media can be reduced or hidden, and body geometry loads through a shared cache while its reserved layout space remains stable. These are useful interaction/performance ideas that do not require adopting OpenGym's artwork or component code. [Media controls][og-media], [geometry loading][og-bodymap-loading]

## Original Kotlin implementation priorities

### 1. Add detailed historical logging beside the quick summary

Reuse IronLog's set-entry components in a dated workout draft. The draft should carry the selected start time, duration, plan reference or freestyle status, and historical provenance. Offer a clear choice when another workout exists on that date. Save the session and sets transactionally.

Define what happens to charts, PRs, progression baselines and ledger rewards before implementing the screen. OpenGym's unconditional backfill PR suppression is a visible policy, not a universally correct one to inherit. IronLog could instead calculate whether a set was a record as of its historical date, while separately deciding whether importing it earns present-day rewards.

Acceptance checks: date-only summary remains available; detailed sets survive reopening/export; out-of-order history is ordered consistently; duplicate handling is explicit; retrying a save does not create a second session or reward.

### 2. Make each progression suggestion auditable

Extend the existing recommendation type with policy, eligible previous session, proposed target, reason, missing inputs and loadable increment. Add named policy selection only where a plan requires it. The UI should let the athlete inspect why the suggestion exists without reading an algorithm description every set.

Exclude warmups and intentionally excluded deload sessions from the relevant baseline. Preserve manual edits and completed sets when settings or policies change. OpenGym already distinguishes excluded deload history from an eligible “last time,” which is a useful invariant to examine. [Baseline selection][og-baseline]

Acceptance checks: explain HOLD as well as increases; handle plate availability and units; distinguish no history from a failed target; make warmup/deload/import eligibility explicit; never rewrite logged sets.

### 3. Put supporting data next to statistics and coaching

Add rated/total working-set coverage to effort summaries, source load/reps/date to e1RM, and concise input provenance to recovery and AI summaries. Use one shared estimator and eligibility rule across screens. Label unavailable inputs rather than silently substituting zero.

OpenGym's five-rated-set minimum and 12-rep ceiling are implementation choices, not evidence that those exact thresholds are optimal for IronLog. Select thresholds through an explicit product/training rationale, then test the resulting behavior.

Acceptance checks: mixed RPE/RIR, unrated sets, warmups, high-rep sets, bodyweight exercises, edited/deleted sessions and out-of-order imports produce consistent results across UI and AI facts.

### 4. Refine the workout flow in small, testable increments

Candidates worth checking against the current native behavior:

- A note pinned specifically for the next session, separate from a permanent exercise note.
- Propagation of a weight edit only to subsequent unlogged sets in the same warmup/work phase.
- Clear “per side” versus total-repetition semantics.
- Rest behavior that follows the superset round instead of starting an unnecessary full rest after every member.
- Persistently collapsible media and an opt-in timer flash.

The relevant OpenGym mechanisms are in [notes][og-notes], [phase propagation][og-cascade], [Workout][og-sets], [superset flow][og-supersets], [media][og-media] and [timer flash][og-timer-flash]. These are candidates, not a claim that each is absent from IronLog. Validate one change with representative workout tasks before expanding the scope.

### 5. Share training facts before adding another AI surface

Define a typed, read-only projection for recent sessions, eligible working sets, effort coverage, estimated records and recovery inputs. The UI, export and AI prompt builder should consume the same facts and carry the same provenance.

OpenGym's MCP adapter demonstrates reuse of analysis functions; it does not justify introducing MCP or self-hosted infrastructure into IronLog by itself. Add a transport only when a real user workflow requires it. If schedule exceptions are pursued later, keep date overrides separate from recurring plans and derive Home/reminder state from the same resolved schedule.

## Performance: useful tactics, important limits

OpenGym limits initial exercise-library rendering to 40 rows, lazy-loads language/body data, and permits reduced or hidden media. Those choices reduce some work. They are not proof of fast startup, smooth scrolling or low energy use on an Android phone. [Library][og-library], [languages][og-i18n], [geometry][og-bodymap-loading], [media][og-media]

Its persistence model has different scaling tradeoffs from IronLog:

- Store updates deep-clone the whole state with JSON and synchronously serialize it to localStorage. Whole-state consumers are then notified. Native file writes debounce 800 ms; server pushes debounce 1,500 ms. [Store][og-store]
- Library best-performance calculations scan workout history for displayed exercises. [Library][og-library]
- The server replaces the profile's entire JSON document. The inspected PUT handler has no record-level merge or revision precondition, and removes the active session from the server copy. This creates a concurrent-edit design risk; no multi-device data-loss incident was reproduced. [API handler][og-api]
- Routes are eagerly imported even though language/body data is split. [App imports][og-app]

Do not replace ObjectBox with whole-state JSON based on this comparison. Measure IronLog using a release build, frame timelines and representative large histories before assigning performance targets or judging whether an optimization helped. This audit supports no comparative FPS, startup-time or battery claims.

## Reproduced discrepancy: warmup-only history produces fatigue

The README claims that warmups do not affect the fatigue map. At the pinned revision, however, session-local intensity and tonnage helpers include completed warmup rows. The strength-recency calculation in the same file does exclude them. [README][og-readme], [intensity helper][og-recovery], [tonnage helper][og-tonnage], [strength filter][og-strength]

The following audit-authored synthetic fixture was executed against those unmodified functions with the preinstalled Node runtime. No dependencies were installed. Run it as an ES module from an OpenGym checkout at the pinned revision; it imports the module directly and does not start the app.

```javascript
import { fatigueOf, strengthOf } from './frontend/src/lib/recovery.js';

const now = Date.parse('2026-08-31T12:00:00Z');
const warmupOnly = [{
  d: '2026-08-31',
  start: now,
  entries: [{
    id: 'audit-custom',
    bp: 'chest',
    tg: 'pectorals',
    sets: [{ w: 50, r: 5, done: true, phase: 'warmup', warmup: true }],
  }],
}];

console.log(JSON.stringify({
  emptyChestFatigue: fatigueOf([], now).chest,
  warmupChestFatigue: fatigueOf(warmupOnly, now).chest,
  warmupChestStrength: strengthOf(warmupOnly, now).chest,
}, null, 2));
```

Observed output:

```json
{
  "emptyChestFatigue": 0,
  "warmupChestFatigue": 0.09443390426562792,
  "warmupChestStrength": 0.5
}
```

This establishes a discrepancy in the pure calculation for that fixture. It does not establish what a released APK displays, how common the case is in real histories, or whether other fixtures are correct. For IronLog, the actionable lesson is to test set eligibility across every consumer—progression, recovery, volume, records and AI facts—rather than trusting a README statement or the mere presence of tests.

## Licensing and provenance boundary

The frontend package declares AGPL-3.0-or-later. OpenGym's notice distinguishes original code, MIT-derived body geometry, upstream exercise metadata, translations and third-party exercise media. It also flags unresolved media ownership and does not grant the reader rights to those images through the project's license. Its app-store exception addresses a distribution channel; it is not blanket permission to relicense code or assets. [Package declaration][og-package], [provenance notice][og-notice]

This report is a behavior and architecture reference. It does not copy OpenGym implementation code, translations, branding, body geometry or exercise media; the fixture above is original audit input. Implement the selected ideas independently in Kotlin. Any future reuse of source or assets requires a separate review of the exact material and its license.

## Evidence map

All OpenGym links below use the audited commit, except the project landing page. IronLog relative links identify the current files inspected on the audit date; their line numbers may move as development continues.

| Topic | Primary sources |
|---|---|
| Identity/version | [Project][og-repo], [commit][og-commit], [package][og-package], [changelog][og-changelog], [README][og-readme] |
| Home/action hierarchy | [Home.jsx, from line14][og-home], [TabBar.jsx, from line18][og-tabs] |
| Progression | [progression.js, from line24][og-progression], [Workout.jsx, from line199][og-progression-ui], [history.js baseline selection, from line313][og-baseline] |
| Set modes/intensifiers/notes | [Workout.jsx, from line65][og-sets], [history.js modes, from line36][og-tracking], [history.js notes, from line229][og-notes], [history.js propagation, from line473][og-cascade], [supersetFlow.js, from line69][og-supersets] |
| Historical entry | [sheets.jsx, from line1396][og-backfill-ui], [PR suppression, from line1668][og-backfill-pr], [backfill.js, from line19][og-backfill] |
| Effort/e1RM | [effort.js, from line17][og-effort], [onerm.js, from line15][og-onerm] |
| Muscle views/calculations | [Stats.jsx, from line100][og-stats], [recovery.js intensity, from line164][og-recovery], [tonnage, from line226][og-tonnage], [strength, from line317][og-strength] |
| Body interaction/loading | [BodyMap.jsx interaction, from line29][og-bodymap-interaction], [shared loading, from line10][og-bodymap-loading] |
| Motivation | [history.js weekly streak, from line455][og-streak], [Stats.jsx][og-stats], [completion/PR flow][og-backfill-pr] |
| Store/deployment | [useStore.js, from line25][og-store], [server.js passkeys, from line548][og-auth], [server.js persistence, from line725][og-api], [mobile.js, from line20][og-mobile], [MobileOnboarding.jsx, from line40][og-mobile-onboarding] |
| Performance/interaction structure | [Library.jsx, from line14][og-library], [i18n.js, from line18][og-i18n], [Media.jsx, from line7][og-media], [TimerFlash.jsx][og-timer-flash], [App.jsx imports, from line19][og-app] |
| AI integration | [MCP stdio entry point][og-mcp], [shared analysis imports, from line9][og-mcp-tools] |
| Rights/provenance | [NOTICE.md][og-notice], [package license][og-package] |
| Author screenshots inspected | [Home][og-shot-home], [Workout][og-shot-workout], [Stats][og-shot-stats]—author-provided, not captures from this audit |
| Native comparison anchors | [ProgressionRecommendationEngine.kt][il-progression], [ActiveWorkoutScreen.kt advice formatting][il-progression-ui], [WorkoutCalendarScreen.kt][il-calendar], [RecoveryReadinessEngine.kt][il-recovery], [Metrics.kt][il-metrics], [SetRow.kt][il-setrow], [WorkoutState.kt][il-workout-state], [IronLedgerEngine.kt][il-ledger], [ForgeFoxWidgetPresentation.kt][il-widgets] |

[og-repo]: https://gitlab.com/DuarteSantos8/opengym
[og-commit]: https://gitlab.com/DuarteSantos8/opengym/-/commit/75fb168a03de09f995d05efd4fd2bfda2d595e0f
[og-package]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/package.json
[og-changelog]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/CHANGELOG.md
[og-readme]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/README.md
[og-home]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/views/Home.jsx#L14
[og-tabs]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/components/TabBar.jsx#L18
[og-progression]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/progression.js#L24
[og-progression-ui]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/views/Workout.jsx#L199
[og-baseline]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/history.js#L313
[og-sets]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/views/Workout.jsx#L65
[og-tracking]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/history.js#L36
[og-notes]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/history.js#L229
[og-cascade]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/history.js#L473
[og-supersets]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/supersetFlow.js#L69
[og-backfill-ui]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/sheets.jsx#L1396
[og-backfill-pr]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/sheets.jsx#L1668
[og-backfill]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/backfill.js#L19
[og-effort]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/effort.js#L17
[og-onerm]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/onerm.js#L15
[og-stats]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/views/Stats.jsx#L100
[og-recovery]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/recovery.js#L164
[og-tonnage]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/recovery.js#L226
[og-strength]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/recovery.js#L317
[og-bodymap-interaction]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/components/BodyMap.jsx#L29
[og-bodymap-loading]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/components/BodyMap.jsx#L10
[og-streak]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/history.js#L455
[og-store]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/store/useStore.js#L25
[og-auth]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/api/server.js#L548
[og-api]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/api/server.js#L725
[og-mobile]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/mobile.js#L20
[og-mobile-onboarding]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/views/MobileOnboarding.jsx#L40
[og-library]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/views/Library.jsx#L14
[og-i18n]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/lib/i18n.js#L18
[og-media]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/components/Media.jsx#L7
[og-timer-flash]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/components/TimerFlash.jsx#L5
[og-app]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/frontend/src/App.jsx#L19
[og-mcp]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/mcp/src/index.js#L1
[og-mcp-tools]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/mcp/src/tools.js#L9
[og-notice]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/NOTICE.md
[og-shot-home]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/assets/screenshots/home.png
[og-shot-workout]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/assets/screenshots/workout.png
[og-shot-stats]: https://gitlab.com/DuarteSantos8/opengym/-/blob/75fb168a03de09f995d05efd4fd2bfda2d595e0f/assets/screenshots/stats.png
[il-progression]: ../../app/src/main/java/com/ironlog/app/domain/intelligence/ProgressionRecommendationEngine.kt#L6
[il-progression-ui]: ../../app/src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt#L3430
[il-calendar]: ../../app/src/main/java/com/ironlog/app/ui/screens/workout/WorkoutCalendarScreen.kt#L407
[il-recovery]: ../../app/src/main/java/com/ironlog/app/domain/intelligence/RecoveryReadinessEngine.kt#L49
[il-metrics]: ../../app/src/main/java/com/ironlog/app/util/Metrics.kt#L10
[il-setrow]: ../../app/src/main/java/com/ironlog/app/ui/components/SetRow.kt#L63
[il-workout-state]: ../../app/src/main/java/com/ironlog/app/ui/state/WorkoutState.kt#L46
[il-ledger]: ../../app/src/main/java/com/ironlog/app/domain/gamification/IronLedgerEngine.kt
[il-widgets]: ../../app/src/main/java/com/ironlog/app/widget/ForgeFoxWidgetPresentation.kt
