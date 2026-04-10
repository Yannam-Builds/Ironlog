# IRONLOG Rules Spec: Shipped vs Pending Delta

## Status Note

The deterministic rules below are already shipped in 1.1.0-beta.
This file now tracks only refinement deltas still pending.

## Shipped Rule Families (Baseline)

- Volume interpretation with dataset-driven comparisons, anti-repetition, one-line output, and tons fallback.
- Progression by exercise profile (barbell, dumbbell, machine/cable, bodyweight).
- Plateau and deload recommendation gates (suggestion-first, never auto-apply).
- Muscle contribution inference with confidence-aware fallback behavior.
- Recovery state classification (`fresh`, `recovering`, `fatigued`) from workload and recency.
- Session quality summary generation.

## Pending Rule Deltas

### 1) OpenWeight Interop Mapping Rules
- deterministic exercise alias matching first
- explicit unresolved-exercise review; no silent renaming
- namespaced metadata preservation for richer IRONLOG-only fields:
  - `ironlog:recovery`
  - `ironlog:manualRecovery`
  - `ironlog:milestones`
  - `ironlog:analytics`
  - `ironlog:muscleContribution`
  - `ironlog:notificationPolicy`

### 2) Program/Adherence Rule Expansion
- busy-week compression with minimum-effective-session logic
- fatigue-aware reshuffle constraints
- optional `%1RM` and optional RPE/RIR target integration

### 3) Explainability/Confidence Rules
- every surfaced recommendation must carry a concise rationale
- low-confidence recommendations should degrade to broader guidance or stay hidden
- no fake precision for ambiguous inputs

### 4) Release Trust Rules (Android)
- release builds must use non-debug keystore signing
- release manifests must not include blocked high-risk unused permissions
- docs must describe Drive as backup target behavior, not real-time sync
