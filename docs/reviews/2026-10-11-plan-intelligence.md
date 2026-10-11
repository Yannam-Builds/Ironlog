# Plans, Intelligence and progression parity closure — 2026-10-11

## Native sources

- `UnifiedPort/app/src/main/java/com/ironlog/app/domain/intelligence/ProgressionRecommendationEngine.kt`: target completion, effort minimum, special/mixed-load hold, policy-based load cap, percent-1RM branch, one-rep fallback.
- `UnifiedPort/app/src/main/java/com/ironlog/app/domain/intelligence/ProgressionLoadability.kt`: both baseline and smallest plate increment must be exactly loadable.
- `UnifiedPort/app/src/main/java/com/ironlog/app/domain/intelligence/ProgressionPolicyResolver.kt` and existing web `program-intelligence.ts`: exercise override → plan rules → global preference → conservative default.
- `UnifiedPort/app/src/main/java/com/ironlog/app/ui/screens/intelligence/TrainingIntelligenceScreen.kt`: TodayDirective priorities, cyclic next planned day, scheduled deload recommendation, quick actions.
- `UnifiedPort/app/src/main/java/com/ironlog/app/ui/screens/plans/PlanEditorScreen.kt`: confirmed deload clone uses `maxOf(2, sets / 2)`, unchanged rep/rest targets and 60%-weight note.

## Acceptance

- Plan exercise handles accept mouse and touch pointers, capture the pointer, reorder the current day with stable exercise identity, animate neighboring rows, visually mark the dragged row and restore original order on pointer cancellation. Existing up/down and across-day controls remain available. Edits persist only on explicit Save plan.
- Generate Deload Day first opens confirmation; cancel changes nothing. Generate adds a draft with fresh IDs, same exercises and native minimum-two half-set rule. Source day remains unchanged. Web preserves extra fields such as warmup/superset metadata in the clone.
- Today's directive reproduces native neural-fatigue → no mapped weekly sets → slowing PR trend → train as planned priorities. Current recovery estimate and recent (48-hour) pain flags remain visible. The recommended session cycles after the most recent completed plan-day, excluding active/discarded/future sessions. Starting uses the store's existing active-workout guard; an existing workout is resumed instead of replaced.
- Scheduled deload displays 60% working load, about 40% fewer sets and unchanged rep ranges, and links to the plan editor.
- Progression uses the latest comparable session's full working-set evidence and current targets. Rep ranges use their upper endpoint before progressing. Incomplete targets, absent effort, near-limit effort, special sets and mixed loads hold. Both RPE/RIR use their stricter margin. Exercise/plan/global policy controls limits. Barbell increases require exact loadability with current plate inventory; otherwise the engine proposes one controlled rep. Unknown/timed/assisted tracking receive no unsupported recommendation.
- Why this suggestion exposes source date, target, working/effort counts, policy provenance, margin/cap, available increment and missing evidence. Use suggestion only fills inputs: no set, workout or plan is automatically saved.

## Validation

- `npm --prefix web run check`: passed after integration with root changes.
- Focused Vitest: progression advice, plan editor, engine, program intelligence and tracking UI; 33 tests passed.
- Playwright CLI with isolated `plan-intel` browser at local Vite port 5183: real mouse pointer reorder, confirmed deload generation and explicit save, directive/scheduled deload display, progression rationale, apply input without changing stored active-workout JSON, and resume action. Chromium CDP touch events at 412 × 915 also reordered the editor correctly.
- Ignored helpers and screenshots: `output/playwright/plan-intelligence-runtime.js`, `progression-runtime.js`, `touch-runtime.js`; `plan-editor-before.png`, `plan-editor-touch-reorder.png`, `intelligence-directive-deload.png`, `workout-progression-rationale.png`, `workout-progression-inputs.png`. Rationale screenshot visually inspected for readable contained dialog.

## Limits

- Heuristics reproduce native coaching intent; they are not a physiological model. Pain does not silently select or overwrite a plan.
- Plate inventory applies to barbell/EZ-bar load recommendations. Dumbbell/machine increments use the native kg/lb default because no dumbbell-stack inventory is modeled.
- Browser touch evidence is Chromium input simulation, not physical-device QA. No native files, release configuration, signing data or deployment were changed.
- Runtime fixture seeding happened only in the isolated local browser database. Initial concurrent Vite updates logged duplicate-root HMR errors; reloading recovered, and completed flows passed. This does not constitute a production release-build assessment.
