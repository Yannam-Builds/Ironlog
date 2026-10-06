# Native exercise catalog and search parity

The web extractor copied raw tracking values and omitted seeded metadata. Ab Crunch Machine therefore exposed distance tracking even though Kotlin ExerciseSeed applies ExerciseTrackingTypeNormalizer and stores weight_reps. This slice exports the normalized native catalog with categories, bodyweight/external-load flags, primary and secondary muscles, contribution fractions, movement patterns and difficulty. Regex lists are consumed directly from Kotlin and included in native provenance; seed precedence and 70/30 contribution rules match ExerciseSeed.

The shared chooser now searches normalized names, aliases and metadata using ExerciseUiFilters relevance tiers and 180 ms debounce. Existing equipment exclusion and 50-result chooser limit remain. Android JSON now transfers canonical movement_pattern, difficulty and aliases_json fields. Existing workout snapshots and user/restored library overrides retain precedence when the bundled catalog updates.

Verification: 51 files / 250 tests; TypeScript, production build and all 185 output files pass. Regressions cover crunch correction, all 1731 entries' flags/taxonomy, timed/cardio/weighted-duration precedence, contribution weights, canonical native transfer, existing session preservation and alias/metadata search UI. Closing browser/deployment evidence is recorded in the root handoff.

P03/P14 remain open: separate management destination, favorites and scopes, filter sheet, recent searches, full library results, native tutorial/detail routes and guarded custom deletion/editing. No native source or APK changed in this web slice.
