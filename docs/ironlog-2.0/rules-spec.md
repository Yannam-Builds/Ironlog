# IRONLOG 2.0 Rules Spec

## Volume Interpretation Rules
- Use the 100-object dataset.
- Default maximum comparison weight is `100000 kg`.
- Exclude extreme entries unless the comparison explicitly needs them.
- Use closest-match selection with category rotation and recent-usage blocking.
- Output one sentence only.
- Always combine volume with performance context.
- If no good match exists, fall back to tons.

## Progression Rules
- Barbell upper compounds
  - Success: `+2.5 kg`
  - Partial success: hold
  - Repeated misses: `-5%` or rep reset
- Barbell lower compounds
  - Success: `+5 kg`
  - Repeated misses: `-5%` to `-7.5%`
- Dumbbells
  - Use next pair increment when reasonable
  - Use rep-first progression when the jump is large
- Machine and cable movements
  - Use one stack step or `+2.5 kg`
  - Otherwise hold load and add reps
- Bodyweight movements
  - Add reps until the rep ceiling
  - Then add external load

## Plateau Rules
- Minimum 3 non-deload exposures
- Flag when e1RM, reps, and volume remain meaningfully flat
- Recommend one primary action in this order:
  - microload
  - rep reset
  - variation swap
  - local deload

## Deload Rules
- Never auto-apply deloads
- Surface when repeated underperformance overlaps with low freshness or unusually high recent fatigue
- Keep rationale explainable

## Muscle Contribution Method
- Use layered inference:
  - anchor lift overrides
  - movement-family templates
  - library-muscle expansion
  - equipment, mechanic, and name inference
- Store confidence per exercise-to-muscle assignment
- When confidence is weak, aggregate display to a broader region instead of pretending the split is precise

## Recovery Rules
- Estimate effective stress with time decay
- Report per-muscle state as:
  - fresh
  - recovering
  - fatigued
- Use real workload history plus optional manual recovery inputs later

## Session Quality Rules
- Keep summaries brief and practical
- Count progression wins and dips from repeated lifts
- Include at least one muscle-volume interpretation
- Use one sentence only
