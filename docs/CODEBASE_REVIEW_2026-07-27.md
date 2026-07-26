# IronLog codebase review — 2026-07-27

## Executive status

IronLog is past the prototype stage. The native Android app builds, its JVM suite and lint gate pass, signed minified APK/AAB outputs have been produced, and the latest signed APK has been smoke-tested on both an API 36.1 emulator and a physical Android device. The main remaining work is release operations and broader device-level regression coverage, not a ground-up feature rewrite.

## Where development left off

The stabilization pass closed the highest-risk correctness work across persistence, workout lifecycle, import/export, recovery, widgets, QR sharing, Health Connect, and the body map:

- ObjectBox startup no longer destroys a database after an initialization failure.
- Workout creation, completion, discard, history cascades, and import writes use transactions where cross-entity consistency matters.
- Imported workouts carry stable provenance so append/restore behavior does not collapse distinct sessions.
- Workout completion metrics are idempotent and a dismissed foreground notification no longer destroys resumable workout state.
- Readiness clocks, inactivity streak semantics, reminder keys, widget history ordering, bounded QR decoding, and partial Health Connect permissions were corrected.
- Body-map drawing and hit testing now share the same origin-centered transform and the full-screen map owns a constrained viewport below its controls.
- Automatic Android backup/device transfer is disabled for sensitive fitness/photo data; the explicit IronLog backup path remains available.
- Compile/target SDK, Android Gradle Plugin, Gradle, and ObjectBox were moved to the current project baseline documented in the root build files.

## Verification evidence

Current local gate:

- 134 JVM tests, 0 failures, 0 errors.
- `lintDebug`: 0 errors.
- `assembleDebug`: APK produced successfully.
- Signed minified release APK and AAB previously built successfully with private local signing material.
- Fresh onboarding, starter-plan selection, Home, Recovery Map front/back views, muscle hit testing, and landscape recreation smoke-tested on an API 36.1 emulator.
- Stable signed release installed on the physical device with `adb install -r`, preserving app data.
- No actionable `TODO`, `FIXME`, `NotImplementedError`, or `error("TODO")` markers in compiled app source.

## What should be removed from the public repository

1. **Release secrets and machine configuration.** Never publish `local.properties`, `*.jks`, API keys, databases, user exports, photos, or device logs. The old local Git history contains a release keystore and local properties; publish through a sanitized branch that does not share that history.
2. **Generated output.** Keep `build/`, `app/build/`, Gradle caches, reports, APKs, AABs, and local validation artifacts out of source control.
3. **Historical quarantine source.** `app/src/main/quarantine_java` contains 108 noncompiled port-reference files. It is not a Gradle source set and should be omitted from the public source snapshot. If historical parity research is still useful locally, retain it outside the published tree.
4. **The stale landing-page implementation.** The existing public `main` branch describes ObjectBox and recovery work as planned/prototype work. Replace that site snapshot with the current Android repository and accurate README rather than maintaining two conflicting product states.

## What should remain, but be edited carefully

- Keep `docs/superpowers/` as historical engineering evidence, with old checklist documents clearly labeled superseded rather than presented as the live backlog.
- Keep ObjectBox's generated model files in source control and review model IDs/UIDs as migration-critical data whenever entities change.
- Keep the personal-use license from the existing public repository unless the owner explicitly chooses a different distribution model.
- Keep public screenshots sanitized and generated from a clean test profile.
- Keep release signing local; public CI should test, lint, and assemble only the unsigned/debug path.

## What is left to do now

### Release blockers

- Rotate the Android release key/passwords before any Play Store or public binary distribution because prior local Git history tracked signing material.
- Confirm whether `com.ironlogpro.app` has ever been published. That determines whether a signing-key rotation is free or must follow Play App Signing recovery/upgrade procedures.
- Add Play Console/service-account delivery only after the signing decision; no Play Publisher configuration exists in this repository today.
- Run a physical-device regression matrix covering workout start/resume/complete/discard, process death, notification actions, camera/photos, QR import, Health Connect permission variants, widgets, backup/restore, and orientation.

### High-value engineering work

- Add Android instrumentation/Compose UI tests. The current automated suite is JVM-only, so navigation, permission UI, widgets, services, camera, and rendering still depend on manual/device QA.
- Add migration fixtures that open representative older ObjectBox databases, not only unit-level import fixtures.
- Add a deterministic end-to-end backup/restore test with photos and calibration/profile data on an emulator.
- Add crash and performance observability only with an explicit privacy decision; do not silently introduce cloud telemetry into a local-first product.

### Product and polish

- Complete accessibility review for TalkBack semantics, large font scales, touch targets, contrast across every selectable theme, and reduced motion.
- Verify Forge Fox widget sizing and cropping across common launcher grids and OEMs.
- Capture a sanitized, consistent screenshot set after the next physical-device regression pass.
- Reconcile public release notes and versioning once distribution is configured.

## Recommended order

1. Publish the sanitized source/README branch and let CI prove the public build path.
2. Complete the read-only independent source audit and fix only independently reproduced findings.
3. Run the physical-device regression matrix.
4. Resolve package publication history and rotate signing credentials.
5. Configure distribution, tag the first supported public build, and publish checksums/release notes.

This document is the current operational backlog. Older implementation plans explain how features were built; they should not override this status unless a newer dated review explicitly replaces it.
