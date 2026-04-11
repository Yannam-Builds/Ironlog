# Play Store Launch Checklist and One-Time Purchase Plan

This plan assumes:

- Android first
- current product is close to launch-ready
- monetization should start with a one-time purchase model
- anti-piracy hardening should happen before or alongside monetization

## 1. Recommendation

The best first commercial model for IRONLOG is:

- free install
- limited free tier
- one-time Pro unlock through Google Play Billing

That is better than a fully paid upfront app for most launches because:

- it reduces install friction
- improves review volume
- gives users a chance to feel the product before paying
- usually converts better for premium fitness tools than a blind paid install

## 2. Recommended initial pricing

For a serious premium Android workout app with your current feature depth, the most sensible starting range is:

- launch test price: `USD 7.99`
- strong default price: `USD 9.99`
- upper-end early premium test: `USD 12.99`

Recommended starting move:

- launch at `USD 9.99`
- use occasional intro promos or region-aware pricing if needed

Why not lower:

- too cheap weakens premium perception
- people may assume it is a hobby app

Why not much higher at first:

- you do not yet have broad public proof, ratings, or market reputation

## 3. Best free vs paid split

Good free tier:

- logging workouts
- plans
- basic history
- basic stats

Good Pro unlock:

- advanced analytics
- recovery maps
- adaptive targets
- premium backups / Drive backup
- advanced export/import
- future premium intelligence features
- premium themes or share tools if needed

The free tier should be useful enough to build trust but incomplete enough that serious users want Pro.

## 4. Crack-proof reality

No Android app is perfectly crack-proof.

The real goal is:

- make piracy harder
- make unpaid premium access harder to maintain
- protect monetized features enough that cracking is not the easy default

## 5. Hardening plan before monetization

### Must do

- use Google Play Billing for the Pro unlock
- use Play Integrity API
- verify purchases on your own backend if possible
- bind entitlement checks to a user/account/device model where practical
- obfuscate release builds with R8 / ProGuard
- avoid a single obvious local boolean like `isPro = true`
- gate premium features in multiple places, not one UI check

### Strongly recommended

- store entitlement state defensively
- validate purchase tokens server-side when the user signs in
- re-check entitlement periodically
- detect tamper/root/debug patterns conservatively
- use signature-aware build validation for release-only premium code paths

### Important note

If you stay fully offline and avoid any backend at all, piracy resistance is much weaker.

If monetization matters, even a lightweight entitlement backend is worth it.

## 6. Launch checklist

### Product readiness

- finish RC QA
- clean crashers and obvious alignment issues
- verify onboarding
- verify restore flows
- verify backup and export behavior
- verify notifications and quiet-hours behavior
- verify accessibility basics and permissions flow

### Compliance

- finalize Privacy Policy
- finalize Terms of Use / EULA
- complete Data Safety disclosure
- verify target API compliance
- verify package signing and Play App Signing setup
- review trademark and licensing posture

### Store listing

- app title
- short description
- long description
- icon
- feature graphic
- screenshots
- category
- contact email
- privacy policy URL

### Release ops

- create internal test track
- run closed testing
- verify billing flow in test environment
- verify upgrade / restore / entitlement checks
- verify post-purchase unlock behavior

## 7. Monetization rollout order

### Phase 1

- launch free
- get installs, ratings, crash data, retention signals

### Phase 2

- add one-time Pro unlock behind Play Billing
- keep generous free core and premium advanced layer

### Phase 3

- monitor conversion rate
- test `7.99`, `9.99`, or `12.99` depending on review quality and retention

## 8. Success metrics for one-time purchase

- install to signup / first workout completion
- first week retention
- trial-to-upgrade conversion if you add a trial gate
- paid conversion from active users
- refund rate
- review quality after monetization

## 9. Suggested launch sequence for IRONLOG

1. finish legal docs and launch assets
2. ship free app first
3. gather retention and quality signals for a short window
4. implement Pro unlock with anti-piracy hardening
5. turn on billing once the free product is stable

## 10. Official Android references

- Play Billing overview: https://developer.android.com/google/play/billing
- Play Integrity overview: https://developer.android.com/google/play/integrity/overview
- Play Console policy center: https://support.google.com/googleplay/android-developer

## 11. Final recommendation

Do not wait for perfect crack-proofing before launch, because perfect crack-proofing does not exist.

Instead:

- launch
- harden entitlement architecture properly
- then add one-time purchase in a measured way

That gives you better odds of real revenue than waiting too long for a perfect defensive setup.
