# Home final runtime closure

Verified on 2026-10-12 with Playwright CLI and Chromium against the fresh Vite server at `http://127.0.0.1:5183/Ironlog/app/`. The new isolated `home-final-oct12` session used only synthetic browser-local fixtures; no user browser data or application source was changed.

- Actual mouse click on the next visible year row changed 1995 to 1996. Keyboard Home then ArrowUp changed 1900 to 1901, confirming the corrected spinbutton direction.
- Actual mouse-wheel scrolling wrapped below 1900 to 2026 and above 2026 to 1900, with the selected value centered after settling.
- Home at 320, 411 and 1280 CSS pixels had document width equal to viewport width. DailyProof remained contained and displayed the expected first-proof state. The 320px Home screenshot was visually inspected; cards, action text and navigation were readable without overlap.
- DailyProof's Start workout action scrolled to the workout card. Selecting carousel Day C stored `dayId=firstC`. Canceling the discard confirmation preserved the active workout; confirming discard returned Home to the saved plan card.
- Chromium CDP touch input swiped Home to Plans. A vertical touch drag moved the second plan to the top; the stored order became `second, first`, the active plan became `second`, and Home displayed the new active plan. The resulting plan screenshot was visually inspected.
- The Home round start control opened Day A of the newly active plan. DailyProof displayed the active Day A session, and its Resume workout action reopened that session.
- Both completed browser scripts captured zero application `pageerror` events.

Artifacts under ignored `web/output/playwright/`: `home-final-review.js`, `home-final-touch.js`, `home-final-wheel.png`, `home-final-320.png`, `home-final-411.png`, `home-final-1280.png`, and `home-final-touch-plans.png`. The wheel and touch-plan screenshots were also visually inspected. Initial automation attempts required correcting helper argument/locator mistakes; the completed runs passed.

Limits: this is Chromium development-server evidence with simulated touch, not physical-device or production-build verification. It exercises first-proof and active-session DailyProof states, not every coaching branch. Broader automated tests and release checks remain outside this pass.

Final source reconciliation: Kotlin `showDailyProofAction` displays the primary button only for SETUP/RECOVER_SMART. The web now follows that rule; first-proof/active states use the workout card's controls rather than an extra Daily Proof button. A component regression verifies the single round resume control. The earlier extra Daily Proof action observations above describe the pre-reconciliation browser pass, not the final control hierarchy. Durability now uses `timerStartedAt`, including deletion of the earliest/all sets and reload/completion.
