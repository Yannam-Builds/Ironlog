# Program intelligence audit — 2026-08-31

## Scope and status

This audit covers native Android training/program recommendations, muscle-volume interpretation,
recovery estimates, progression prompts, and exercise substitutions. It does not validate a clinical
readiness score or prescribe individual medical care. No user workout data was sent to research services.

The recommendation-engine patch preserves the existing APIs and unrelated workspace changes:

- Workout suggestions classify leg curls as Legs, leg/knee raises as Core, and recognize common plural
  names. Specific regions precede ambiguous generic movement words.
- Nonfinite readiness is missing evidence; each valid value is bounded before scoring. Unknown exercise
  coverage cannot yield an affirmative recovery claim. Explanations identify estimated workload as the source.
- Program home eligibility comes from recognizable exercise requirements, never a title such as
  “Minimalist.” Labels disclose dumbbells, a bench, bands, a pull-up bar, or support where needed.
  Unknown requirements remain unverified; gym equipment is not described as home-friendly.
- Recommendation reasons state the actual schedule and template focus. The arbitrary experience-based
  high-frequency score bonus is removed. Schedule fit and stable tie-breaking remain.

Home feasibility is conditional: the profile contains a gym-access boolean, not an inventory of home
equipment. An inferred equipment label is not proof that the athlete owns it. Exercise names without
equipment metadata may remain unverified, including some otherwise practical home variants.

### Verification

Regression tests were added before production changes. The RED run was:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.ironlog.app.domain.intelligence.WorkoutSuggestionEngineTest' --tests 'com.ironlog.app.domain.intelligence.ProgramRecommendationEngineTest' --tests '*IntelligenceInputIntegrityTest' --no-daemon
```

Result: 42 tests, 22 expected failures: all 16 new recommendation tests and six input-integrity tests
owned by the parallel implementation pass. Compilation succeeded; failures reproduced the intended
defects. The final integrated GREEN run passed 247 JVM tests across 53 suites with zero failures,
errors or skips. Debug lint passed (158 warnings, 22 hints, zero errors); debug and instrumentation
APKs built successfully. ObjectBox test transaction-cleanup diagnostics remain and need separate
investigation; a green test result is not evidence those diagnostics are harmless.

Focused API 36.1 emulator checks cover two-line pill stability at normal/200% text size, cloud-summary
cancellation/re-enable, configuration changes and synthetic credential refresh. Home and independent
spacing controls were inspected from emulator screenshots. No physical-phone update or release is part
of this pass. This is bounded regression verification, not a claim that every app journey was retested.

## Findings and bounded follow-up

### 1. Equipment eligibility and misleading explanations — patched and tested

`ProgramRecommendationEngine.isHomeFriendly()` previously matched “minimal” in a title. Built-in
`minimalist_full_body_23` requires barbell squat/bench, cable row, lat pulldown and pushdown, yet received
the no-gym score bonus. The old no-gym test checked eligibility using the same defective predicate.
The regression now checks actual known exercise requirements and the specific minimalist template.

Reasons previously described every nonmatching frequency as “Closest fit” and attributed the requested
goal to every plan. They now identify the actual day count and template category. Higher frequency is
not intrinsically superior when volume and intensity are matched; see Colquhoun below.

### 2. Exercise mapping and confidence — patched and tested

`WorkoutSuggestionEngine` previously matched generic “curl” before “leg,” routing seated/lying leg curls
to Arms; generic “leg” routed hanging leg raises to Legs. Exact token matching also missed Dips and
Shrugs. Tests cover these examples and the actual ranking consequence when Legs are fatigued but Arms
are fresh. Invalid scores and missing readiness now reduce evidence coverage rather than contaminating
or dominating the ranking.

Still remaining: plan-day scores use exercise names equally, not planned working-set dose, secondary
muscles, equipment metadata, pain restrictions, or the intended program sequence. A broader replacement
should share exercise classification with the muscle model and make these inputs explicit. Do not
interpret the current suggestion as approval to train through pain.

### 3. Unconditional progression — patched and tested

The audited `ActiveWorkoutScreen.buildProgressionSuggestion()` always added 2.5 kg/5 lb to the heaviest
previous set, or two bodyweight reps. It did not check target achievement, effort, tracking type, or
available equipment increments. The ghost adapter retained RPE but discarded set type and RIR.

`ProgressionRecommendationEngine` now returns explained hold/add-reps/add-load advice. It excludes
warmups and invalid values, requires completed set/rep targets and recorded effort, and holds when
effort is missing, near failure, set types are special, or prior loads are inconsistent. Timed movements
receive no weight/repetition advice. Ghost sets preserve type and RIR; displays convert canonical kg.

An increment larger than 5% of the baseline offers another rep instead. Rep advice advances from
performed reps rather than repeatedly suggesting the same old plan target. The 5% cap and two-reps-in-
reserve margin are conservative product heuristics, not thresholds validated by these papers. Advice
does not silently edit a plan. Remaining: actual plate inventory, multi-session trends, restrictions and
pain inputs are not integrated. Plotkin supports both progression paths; Helms/Zourdos support using
reported effort without claiming it is an exact physiological measurement.

### 4. Volume units and incomplete-week interpretation — broader work remains

`MuscleContributionEngine.resolveContribution()` normalizes anatomical fractions to sum to one across
all muscles. `TrainingIntelligenceEngine.setsByGroup()` multiplies working sets by those fractions and
compares the result with conventional-looking target bands. Ten barbell bench sets, for example,
contribute six Chest exposure units, not ten direct chest sets. Arms and Legs also aggregate several
muscles. This is a heuristic exposure visualization, not a validated per-muscle set prescription.

Separate direct working sets from indirect exposure before calibrating targets. Keep the visual map
fractions separate so changing the target metric does not silently rewrite recovery behavior.
The screen now labels these values as weighted set equivalents and bands as heuristic references,
not personal minimums. A low Monday/partial-week total no longer generates an instruction to add
volume. Direct-versus-indirect dose calibration remains future work.

Case-insensitive warmup filtering, finite/valid set checks, unknown-muscle omission, and an injected
Clock/ZoneId snapshot are implemented. Future history cannot establish training age or PR baselines;
date-only imports cannot invent a nighttime performance advantage. India/Los Angeles ISO-week tests
cover boundary differences. The screen's legacy JSON-draft reader was removed: numeric draft indices
were treated as exercise names and unknowns then credited to Core. Weekly insights now explicitly use
completed history only, so abandoned/in-progress drafts cannot add phantom exposure.

### 5. Missing substitution metadata — patched and tested

Nullable muscle and movement-pattern comparisons awarded points for null/null matches in
`SubstitutionEngine`, enough to label unrelated unknown exercises “best matches.” Only nonblank known
fields now count as evidence. Nonpositive result limits return empty results. Best-match eligibility
also requires known matching tracking types. Movement-purpose and restriction-aware equivalence still
requires richer metadata; an alternative is not guaranteed to be interchangeable for every athlete.

### 6. Recovery model calibration and input validity — mixed status

Future manual check-ins were not rejected by `scoreFromManualInput()`, and biometric blending accepted
nonfinite numbers. These guards are implemented and tested; finite region values are bounded, stale
or future check-ins are ignored, and invalid biometric values do not contaminate the score.

The larger limitations remain: fixed half-lives, inferred muscle-dose fractions, effort multipliers,
and population-wide HRV cutoffs are heuristics. Empty history is not evidence of verified full readiness.
Refalo supports a directional relationship between proximity to failure and fatigue, not IronLog's
specific percentages or decay constants. Individual baseline HRV, observation freshness, movement-specific
performance, and reported symptoms would be needed for more defensible personalization.

## Verified primary research

Discovery used Agent Reach/Exa. Title, first author, publication year and DOI were independently checked
against Crossref records; claims were checked against primary abstracts or publisher text. PubMed
occasionally returned browser challenges; Europe PMC supplied the complete Plotkin and Colquhoun
abstracts. This is a targeted evidence review, not a systematic review or claim of exhaustive coverage.

### Load or repetition progression

**Plotkin D et al. (2022). Progressive overload without progressing load? The effects of load or repetition
progression on muscular adaptations. PeerJ 10:e14142.** DOI: `10.7717/peerj.14142`.
[PubMed](https://pubmed.ncbi.nlm.nih.gov/36199287/) · [Publisher](https://doi.org/10.7717/peerj.14142)

Randomized 43 resistance-trained men and women to load or repetition progression for eight weeks of
lower-body training. Both strategies produced viable adaptations; most between-group differences were
small, with uncertain practical significance. Application: offer rep and load progression pathways.
Limitation: the study does not establish exact app decision thresholds, upper-body equivalence, or
long-term superiority of either approach.

### Effort-based load selection

**Helms ER et al. (2018). RPE vs. Percentage 1RM Loading in Periodized Programs Matched for Sets and
Repetitions. Frontiers in Physiology 9:247.** DOI: `10.3389/fphys.2018.00247`.
[PubMed](https://pubmed.ncbi.nlm.nih.gov/29628895/) · [Publisher](https://doi.org/10.3389/fphys.2018.00247)

Twenty-one trained men completed eight weeks of squat/bench programming with RPE-based or percentage-1RM
loading. Both groups improved; between-group differences were not statistically significant. Application:
use recorded effort as one transparent input to load selection. Limitation: do not claim established
superiority from a small study or convert its magnitude-based inferences into app confidence percentages.

### Proximity to failure and recovery

**Refalo MC et al. (2023). Influence of Resistance Training Proximity-to-Failure, Determined by
Repetitions-in-Reserve, on Neuromuscular Fatigue in Resistance-Trained Males and Females.
Sports Medicine - Open 9:10.** DOI: `10.1186/s40798-023-00554-y`.
[PubMed](https://pubmed.ncbi.nlm.nih.gov/36752989/) · [Publisher](https://doi.org/10.1186/s40798-023-00554-y)

Twenty-four trained participants completed six bench-press sets under failure, one-RIR and three-RIR
conditions. Failure produced greater acute velocity loss and worse perceptual responses; between-protocol
differences diminished at 48 hours. Application: effort can influence estimated fatigue directionally.
Limitation: this specific protocol does not validate whole-body recovery percentages, medical readiness,
or universal 24–72-hour recovery rules.

### Training volume and goal differences

**Schoenfeld BJ et al. (2019). Resistance Training Volume Enhances Muscle Hypertrophy but Not Strength
in Trained Men. Medicine & Science in Sports & Exercise 51(1):94–103.**
DOI: `10.1249/MSS.0000000000001764`.
[PubMed](https://pubmed.ncbi.nlm.nih.gov/30153194/) · [Publisher](https://doi.org/10.1249/MSS.0000000000001764)

Thirty-four trained men completed eight weeks of differing set volumes. Higher-volume conditions favored
hypertrophy at some measured sites, without significant between-group strength differences. Application:
interpret volume in the context of training goals. Limitation: this is not evidence for universal muscle
minimum/maximum landmarks, normalized anatomy fractions, or continued benefit from unlimited volume.

### Frequency as a schedule choice

**Colquhoun RJ et al. (2018). Training Volume, Not Frequency, Indicative of Maximal Strength Adaptations
to Resistance Training. Journal of Strength and Conditioning Research 32(5):1207–1213.**
DOI: `10.1519/JSC.0000000000002414`.
[PubMed](https://pubmed.ncbi.nlm.nih.gov/29324578/) · [Publisher](https://doi.org/10.1519/JSC.0000000000002414)

Twenty-eight trained men completed six weeks at three or six weekly sessions with matched volume and
intensity. No group-by-time differences were found in strength/body-composition outcomes. Application:
rank schedule feasibility rather than reward experienced athletes merely for training more days.
Limitation: six weeks in trained men cannot establish equivalence for every population or program.

### Interpreting RIR-based RPE

**Zourdos MC et al. (2016). Novel Resistance Training–Specific Rating of Perceived Exertion Scale Measuring
Repetitions in Reserve. Journal of Strength and Conditioning Research 30(1):267–275.**
DOI: `10.1519/JSC.0000000000001049`.
[PubMed](https://pubmed.ncbi.nlm.nih.gov/26049792/) · [Publisher](https://doi.org/10.1519/JSC.0000000000001049)

Twenty-nine novice/experienced squatters reported RIR-based RPE during loading tests. Ratings were
inversely associated with lifting velocity, with experience-related differences. Application: retain RPE/RIR
as reported effort and explain uncertainty. Limitation: the findings do not make an athlete's self-report
an exact physiological measurement or prove a universal fatigue conversion.

## Evidence boundary

Papers support selected training principles. They do not validate the implementation, exact score weights,
muscle fractions, readiness percentages, or every built-in plan. Regression tests establish software
behavior; physiological validity would require a separately designed validation study. No recommendation
should imply diagnosis, injury clearance, guaranteed progress, or superiority beyond the studied evidence.
