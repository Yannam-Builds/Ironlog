# Native app audit and workspace record — 25 September 2026

## Scope

This is a follow-up to the [31 August native audit](2026-08-31-ironlog-app-audit.md), not a claim that every device path has been certified. I inventoried the Android source and tests, reviewed the earlier highest-priority findings against current implementation, traced date handling across Home, statistics and the shared Ledger, and ran the complete local debug JVM suite, lint and assembly with JDK 21. I also ran the same checks in the separate, in-progress `UnifiedPort` checkout without modifying its existing work.

The GitHub checkout has 251 main Kotlin source files and 179 Kotlin test files after this change. The `UnifiedPort` checkout has dozens of modified and untracked files implementing onboarding, workout controls, assets and tests. Those files were preserved. Its generated build outputs, local SDK configuration, release signing material, diagnostic archive and private artifacts were not copied into this repository or deleted.

## Confirmed and corrected

Home's legacy streak and weekly summaries, and the 30-day/90-day/all-time Stats chart, interpreted UTC timestamp dates as calendar days using string slicing or UTC dates. A late-evening UTC workout can belong to the athlete's next local day. Home now uses the existing qualified-proof streak calculation and the shared local-date parser; Stats groups sessions by the same local calendar day. Focused tests cover UTC day crossings in Asia/Kolkata and America/Los_Angeles, date-only imports and malformed dates.

The README now uses a real native sample Home capture labelled **Athlete**. The two SVG diagrams describe the actual training loop and respect reduced-motion and light/dark browser settings. The README states that the web app is a preview and that records do not automatically sync across Android and web.

## Earlier release blockers checked

The current import path validates payload rows before replacement, requires a fresh database fingerprint, refuses replacement during an active workout and saves a checked recovery snapshot before clearing rows. Photo ownership checks and explicit pain restrictions are present in current source and tests. This narrows the earlier P1 findings; it does not prove every imported legacy payload or physical-device flow is safe. The older audit remains a record of what was found at its original commit, not the current open-issue list.

The set update path now has explicit `rpeUpdate`/`rirUpdate` semantics for clearing stored effort values. Credited workout proof uses a shared training-set policy. These address two earlier consistency findings at the source level; device-level edit/restart coverage is still warranted.

## Remaining investigation

- ObjectBox emitted native warnings during otherwise passing JVM tests about read-only cursors/transactions being destroyed on non-owner threads. `observeQuery` owns and closes each subscription and query, and its lifetime tests pass, but the warnings need a dedicated source-level and runtime investigation before they can be dismissed as test teardown noise.
- The Kotlin compiler reports a small number of deprecated Compose clipboard and icon APIs, and several coroutine-test opt-in warnings. These are maintenance items, not observed runtime crashes.
- The `UnifiedPort` checkout is an active dirty workspace. Do not bulk-clean, reset or move it as a substitute for review; reconcile its native work by feature and test before publishing it. The GitHub checkout ignores local `output/` previews and CI downloads, and keeps generated build files, SDK paths, keystores and diagnostics out of version control.
- The app still needs physical-device regression and accessibility passes, Android API/OEM coverage, release signing/history remediation and store-policy review before a stable public Android release.

## Verification

- GitHub checkout: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` on JDK 21 passed locally (758 tests, zero failures; lint and debug assembly successful). Focused local-date tests are included.
- In-progress `UnifiedPort`: the same three tasks passed locally on JDK 21. This verifies the current dirty tree compiles and its JVM suite passes; it is not a release APK or a physical-device test.
- No phone data was cleared, no local signing key was copied, and no native release was installed during this audit.
