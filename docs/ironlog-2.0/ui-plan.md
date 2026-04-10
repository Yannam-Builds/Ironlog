# IRONLOG UI Plan: Pending Android Delta

## Status Note

Home intelligence, active workout targets, volume analytics, and recovery map are already live.
This UI plan tracks only new screens or meaningful pending deltas.

## 1) Import Center (New)

Purpose:
- serious migration and portability entrypoint.

Core UI:
- source picker (`Strong CSV`, `Hevy CSV`, `OpenWeight JSON`)
- dry-run preview card
- unmatched exercise review with manual mapping
- duplicate report and final import summary

Entry points:
- Settings
- Restore wizard
- optional onboarding fast path

## 2) Backup/Restore Trust Delta (Existing Screens, Expanded)

Purpose:
- make continuity behavior explicit and understandable.

Core UI:
- clearer backup-health status
- explicit local backup vs Drive backup mode copy
- restore list and validation feedback improvements
- Play Protect and signing guidance in docs/release notes, not noisy in-app warnings

Screens:
- Backup Center
- Restore Data

## 3) Program Builder and Adherence Delta (Existing + New Sections)

Purpose:
- add coach-grade structure without slowing core logging.

Core UI:
- block/mesocycle builder sections in program editor
- per-exercise progression model settings
- busy-week compression and reschedule suggestion cards
- adherence health explanation rows in Program Insights

Screens:
- Plan Editor / Plans
- Program Insights
- Home (adherence prompts)

## 4) Explainability Delta (Existing Screens)

Purpose:
- make recommendations understandable and trustworthy.

Core UI:
- short rationale chips on Home/Workout/Recovery/Volume views
- confidence-safe display rules (hide weak precision)

Screens:
- Home
- Active Workout
- Recovery Map
- Volume Analytics
