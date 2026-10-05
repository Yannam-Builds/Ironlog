# Gym setup parity checkpoint — 5 October 2026

This slice follows P14 and the gym-specific part of P15 in the [parity plan](../superpowers/plans/2026-09-14-web-native-parity.md). The Kotlin gym editor and profile list are the interaction reference. This checkpoint does not close the full parity backlog.

## Changes

The web gym editor now has individual plate rows, pair counters, a native six-color palette, unit-aware weights and unavailable-equipment checkboxes. Saved profiles support activation, editing, duplication, confirmed deletion and default seeding. The exercise chooser excludes unavailable equipment for the active gym; existing workout records remain intact. Custom plate colors appear in the workout diagram.

Gym saves and active setup updates commit in one IndexedDB transaction. Editing an active gym refreshes its calculator setup. Deleting it selects another saved gym, or clears its identity when none remain. Legacy unlimited-stock profiles remain supported. The editor displays pairs while storage retains total physical quantities, including an odd spare. Opening and saving a pounds profile preserves the original kilogram bar precision if its field is unchanged.

Android JSON transfers now retain plate colors, equipment exclusions and the explicit active gym. Canonical native edits and deletions take precedence over an older web extension. Odd web spares survive only while the native pair stock is unchanged. An identified synthetic current setup remains a profile setup rather than becoming an extra named gym on round trip.

The Kotlin calculator used a greedy choice that missed reachable targets: a 20 kg bar plus two 6 kg plates per side reaches 44 kg, even when a 10 kg plate is also available. It now searches bounded combinations, selects the closest achievable load below the target and prefers fewer plates. It preserves colors and rejects nonfinite inputs. A saved empty inventory stays empty in both calculator consumers instead of silently using default stock. The native editor rejects NaN and infinity before saving.

The photo-calendar UI fixtures now pin their September test clock. Their former reliance on the current month caused failures after October began. The longer gym integration flow has an explicit 15-second test budget; it still asserts committed data and visible state.

## Verification

- Web: 47 files / 229 tests pass with `npm test -- --maxWorkers=2`. TypeScript, production build and the 185-file output check pass.
- Android: 761 JVM tests pass; debug lint reports zero errors. Signed release assembly passes. Existing ObjectBox transaction-owner warnings remain an investigation item from the September audit.
- Browser: the new editor was exercised at 320 px, including color selection, equipment exclusion and saving. Document width remains 320 px. Local screenshot: ignored `web/output/playwright/gym-320.png`.
- Browser acceptance: all 46 Chromium/WebKit scenarios pass, including swipe navigation, plan dragging, offline resume, multi-tab persistence, backups, themes and large text.
- APK `0.1.0-pre-alpha.9` / code 10 verifies with Signature Scheme v2 and one signer. It installs, completes sensible-default onboarding and reaches Home as Athlete on the fresh API 36.1 emulator. A second `adb install -r` succeeds, retains firstInstallTime `2026-10-05 23:45:09` and resumes Home. No AndroidRuntime fatal entries were recorded.
- APK SHA-256: `b5e7d1adcf4d3e541da0bf50da932e60d58e623b961b9024a2437650193082fe`.
- The existing emulator refused installation due to storage pressure; its data was preserved. A separate `IronLog_Gym_QA_20261005` AVD was created under ignored `output/android/avd/` on Z:. Local evidence is in `output/android/`.
- The copied calculator tests also pass in UnifiedPort. Its other in-progress native changes were not included in the GitHub release.

## Workspace and next slice

Work began from GitHub main `796c37a` in the separate `IronLogWeb` checkout. `UnifiedPort` remains a dirty working project. The calculator, gym editor and calculator tests matched the pre-change GitHub blobs before copying these reviewed files there. Only the two empty-stock fallback expressions were patched in its already-modified workout screen; no other edits were replaced.

The full exercise library/detail/editor remains the next P14/P03 task. Other open items remain in the parity audit, including optional cloud AI, workout/history editing, plan interactions, portability and differential visual evidence. Android-only APIs are excluded from the browser target. Do not infer complete parity or a production Android certification from this checkpoint.
