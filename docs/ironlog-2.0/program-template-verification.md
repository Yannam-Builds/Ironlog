# Program Template Verification (Picker Catalog)

Date: 2026-04-10
Scope: all preloaded templates in `src/data/programTemplates.js` (`PROGRAM_TEMPLATE_CATALOG`, 30 templates).

## External Evidence Anchors (web verification)

1. ACSM progression and frequency guidance for resistance training (novice/intermediate/advanced frequencies and progression ranges):  
   https://pubmed.ncbi.nlm.nih.gov/19204579/
2. Recent review/meta evidence supporting split-routine flexibility when weekly volume is equated (full body vs split):  
   https://pubmed.ncbi.nlm.nih.gov/38595233/
3. Practical weekly set-volume ranges used in contemporary hypertrophy guidance (10-20 hard sets/muscle/week mentioned in evidence summaries):  
   https://www.t3.com/active/the-most-underrated-strength-training-method-is-also-the-fastest
4. Canonical novice barbell movement base (squat/bench/deadlift/row/press family):  
   https://stronglifts.com/stronglifts-5x5/
5. Novice program archetype references from Starting Strength ecosystem:  
   https://startingstrength.com/get-started/programs

## What was verified

- All picker templates were validated for:
  - `daysPerWeek` consistency with generated day count.
  - Day exercise integrity (minimum 3 exercises/day).
  - Template equipment compatibility vs generated day exercises.
  - Program generation stability for all 30 templates.
- Structural validation script:
  - `scripts/validate_program_templates.cjs`
  - Result: `PASS: validated 30 templates`.

## Fixes made during verification

1. Accessory enrichment was over-injecting incompatible equipment into bodyweight/home/band plans.
2. Day-focus detection order was causing calisthenics days to be treated as push/pull gym days.
3. Template equipment lists now derive from actual generated day content so picker metadata stays accurate.

## Verdict

- All preloaded picker templates now pass structural verification and align with accepted resistance-training programming patterns (frequency, split flexibility, progression foundations).
