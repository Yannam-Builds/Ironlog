# Native audit remediation — 1 September 2026

**Approved scope:** implement the remedies in [the native audit](../../reviews/2026-08-31-ironlog-app-audit.md) and its concrete original-Kotlin product improvements. The audit remains a historical record, not a claim that these changes are already verified.

**Execution:** test-driven, file-isolated implementation with independent specification/code review. Work in the existing dirty `feat/fatigue-suggestion` checkout because the audited implementation is not present in clean HEAD. Preserve unrelated work; no commits, publishing, website edits, phone installation, or private fixture publication in this pass. Root coordinates all Gradle and emulator operations.

## 1. Data safety

- [ ] D1/D2: validate normalized backup rows before writes; explicit merge/replacement consent; accurate valid/skipped/deletion counts; pre-replacement recovery snapshot; block restore during an active workout. Files: `ImportExportRepository.kt`, new backup validation model, settings import/restore screens. Tests: malformed/empty/duplicate/orphan payloads, older backup merge versus replacement, rollback, active-session guard.
- [ ] D3/U2: canonical photo ownership and shared cleanup. Files: `ProgressPhotosScreen.kt`, new photo-storage helper, import boundary. Tests: outside file, traversal/symlink, owned file, external gallery URI, individual/all removal failures.
- [ ] U3/U4: independent single/compare viewer state and real modal dismissal/focus. Tests: compare A/B then view C; Back closes viewer without leaving screen.
- [ ] D4: validate the actual latest readable backup, not a synthetic health flag. Files: `BackupCenterScreen.kt`, backup services/repository. Tests: missing, unreadable, malformed and valid backup.
- [ ] D5: one-time provider-bound legacy key migration; cleared/unconfigured providers stay empty. Files: `CloudAiKeyStore.kt`, key migration tests using synthetic credentials.

## 2. Shared training facts

- [ ] T1: explicit effort unchanged/clear/value update and persistence regression.
- [ ] T2/T3/T4: normalized tracking contract, finite completed working-set predicate, typed PR/volume/cardio eligibility and added-load progression. Test real exercise adapters, warmups, holds, zero/invalid reps, cardio and edits/deletes.
- [ ] I1/I2/I3/I4/I5: typed persisted check-in and shared clock-aware recovery/restriction projection for app, Daily Proof and widgets; unknown muscles remain unknown. Test process/observer refresh, pain precedence, clock expiry and full-fidelity widget mapping.
- [ ] G1/G3: canonical local dates and credited proof in calendar, streak and widget surfaces; test Kolkata/Los Angeles midnight, DST and ISO-year boundaries.
- [ ] G2/G4: transactional Recovery Circuit eligibility and deterministic historical badge reconstruction, with additive unlocks. Test ineligible/double completion, week changes and history-only imports.

## 3. UI and lifecycle

- [ ] U1: stable-ID plan reordering plus accessible move controls; header-independent tests.
- [ ] U5: consistent pain region mapping and accessible muscle-list alternatives; immutable body-map dataset loaded off main thread.
- [ ] U6: full weekday/selected semantics and large-text scrolling for onboarding/program selection.
- [ ] PERF1/PERF2/PERF3: owned ViewModel scopes, shared history projection/narrow invalidation, lifecycle collection instead of screen polling, explicit query ownership. Verify observer cancellation and synthetic history performance without claiming unmeasured gains.
- [ ] Home resume hierarchy, cold-workout loading state, consistent spacing and existing glass/IME/opaque-overlay regression checks.

## 4. Original Kotlin improvements

- [ ] Detailed dated historical workout entry using the existing set editor and transaction contracts; duplicate-date choice and historical PR semantics.
- [ ] Explainable progression: source session, policy, reason, missing evidence and loadable increment; expose effort coverage and estimate provenance without claiming clinical validation.
- [ ] Next-session notes and safe unlogged-target convenience where the current model supports it; explicit unilateral load semantics. Do not transplant OpenGym code/art or introduce a cloud/MCP backend.

## 5. Acceptance

- [ ] Record RED and GREEN results per slice; full JVM suite and debug lint.
- [ ] Build debug/test APKs and run focused then full Compose tests on `emulator-5554` only; preserve QA database.
- [ ] Exercise restore/photo safety on synthetic data, logging/resume/finish and cross-screen recovery/gamification, narrow/large-text UI and modal focus.
- [ ] Independent spec and code-quality review; resolve findings.
- [ ] Update audit remediation status with exact verified changes, counts and any remaining device/performance/accessibility limits. No assertion of perfect behavior or full physical-device certification.

## Verification log

Baseline from previous pass: 289 JVM tests; 39 distinct Compose cases including separately executed motion-disabled coverage; debug lint 0 errors. These are historical baseline results, not verification of new changes.

### Final verification — 2026-09-01

- Implementation sections 1–4 were completed. The unchecked boxes above are retained as the original planning snapshot; this log is the authoritative completion record.
- `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` completed successfully. XML reports: 506 tests, 0 failures, 0 errors, 1 opt-in benchmark skip. Lint: 0 errors, 156 warnings, 20 hints. `git diff --check` was clean.
- The opt-in synthetic benchmark completed at 0/1,000/10,000 workouts. At 10,000 workouts (40,000 exercise rows, 120,000 sets), combined query/projection/recovery/Ledger median was 1,893.7 ms and p95 was 2,049.1 ms on the desktop JVM. This is not UI FPS or release-device performance.
- Both debug APKs installed on `emulator-5554`. A cold launch of `0.1.0-pre-alpha.7-qa` resumed `MainActivity` with no `AndroidRuntime` fatal entry.
- Focused UI verification passed 14/14, followed by the complete 58-case runner. The long full run had one emulator frame-capture timeout in `effectFailureUsesOpaqueCompatibilityMaterial`; all other cases passed except the expected motion-disabled assumption skip. The timed-out case passed 1/1 after reboot, and the motion-disabled case passed 1/1 with Android animations set to zero. This is emulator renderer instability, not a clean single-run 58/58 result.
- ObjectBox JVM fixtures still emit non-owner-thread read-transaction teardown warnings from worker-owned test cursors. Assertions pass, but the harness cleanup limitation remains explicit.
- Independent final review found and resolved stale ghost-history races, legacy-XP migration ordering, nested pending-navigation loss/duplication, exported-route validation, and widget `statusWindow` compatibility. No remaining high-confidence blocker was reported in the reviewed scope.
- No physical-device installation, signed release publication, website change, Git commit, push, or destructive data action was performed in this pass.
