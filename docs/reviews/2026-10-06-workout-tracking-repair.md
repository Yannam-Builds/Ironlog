# Starter workout logging repair

The screenshot showed a blank tracking type and missing set inputs. Real bundled-template regression reproduces Incline Smith Press starting with no library match, blank tracking and no usable dimensions. The shared start path previously left every unmatched named movement in this state. Kotlin PlanRepository.resolveOrCreateExercise instead creates an unmatched named custom strength movement with normalized tracking.

Web workout creation now resolves or creates those library movements transactionally and snapshots their metadata. Startup also repairs empty tracking in unlogged, non-imported active sessions, preserving slot IDs, prescriptions, notes and session state. The revision advances once, so another open tab cannot silently overwrite the repair. Recorded sets, queued warmups, imported sessions and nonblank unknown types are preserved.

Verification: all native starter templates/days have linked, supported tracking; UI logs a 40 kg × 10 set; startup repairs an existing local session while preserving recorded and imported interpretation. Full local suite: 48 files / 242 tests. TypeScript, production build and 185-file output check pass. Chrome actual catalog/program flow shows all PUSH input controls, logs 40 kg × 10 and retains it on reload. Mobile screenshot: web/output/playwright/workout-tracking-repaired.png. CI and production evidence are recorded in the root handoff.

Native source and APK unchanged. This addresses the workout blocker before continuing the open full-library parity work.
