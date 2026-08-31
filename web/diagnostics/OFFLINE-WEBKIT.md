# Desktop WebKit offline-emulation failure — 2026-08-31

The original cached-offline startup acceptance test failed on this host's
desktop Playwright WebKit. The investigation below records that failure; the
final section documents the subsequent acceptance-transport correction. No
application or PWA configuration change was made for this failure. No physical
iPhone or Home Screen launch was tested.

## Environment and original failure

- Windows host, Playwright `1.62.1`.
- WebKit `26.5`, bundled revision `2336`.
- Chromium `151.0.7922.34`, bundled revision `1234`.
- Both browser projects use Playwright's iPhone 13 device descriptor. This is
  desktop browser emulation, not a device test.
- Existing production preview: `http://127.0.0.1:4173/Ironlog/`.

Reproduction command (does not rebuild the app):

```powershell
npx playwright test tests/browser/resilience.spec.ts --project=webkit --grep 'cached offline startup' --output=output/playwright/webkit-offline-baseline --reporter=list
```

Result: **1 failed**, at the first `page.reload()` after
`context.setOffline(true)`, with `WebKit encountered an internal error`.
The test already verified activated registration, a controlling worker, cached
app HTML, and cached exercise data. Its full reload/fresh-tab/logging/finish
assertions were not bypassed. Trace and screenshot are under
`output/playwright/webkit-offline-baseline/`.

## Minimal reproduction isolates the emulation boundary

```powershell
node diagnostics/service-worker-offline-repro.mjs
```

This standalone diagnostic creates its own ephemeral loopback HTTP server and
uses a small vanilla service worker. It contains no IronLog code, React, Vite,
Workbox, navigation fallback, or app data. HTTP responses carry `no-store` so
the service-worker Cache Storage is responsible for the cached document.

The worker precaches `/`, serves that cached document for `/`, and serves a
synthetic `new Response('handled-by-worker')` for `/worker-probe`. Each case
verifies an active controller, a populated cache, the synthetic response, and
a successful online reload before changing one variable.

| Browser  | Network change             | Synthetic worker response | Offline reload / fresh tab                              |
| -------- | -------------------------- | ------------------------- | ------------------------------------------------------- |
| Chromium | `context.setOffline(true)` | Works                     | Both pass                                               |
| Chromium | Actual HTTP server stopped | Works                     | Both pass                                               |
| WebKit   | `context.setOffline(true)` | `TypeError: Load failed`  | Reload has internal engine error; fresh tab not reached |
| WebKit   | Actual HTTP server stopped | Works                     | Both pass                                               |

All four cases verify that an uncached network fetch fails after the network
change. Stopping the server closes its listening socket and existing connections.
`navigator.onLine` stays true in that case; it is only a connectivity hint and
is not used as proof of network availability. The matrix reproduced consistently
in repeated runs. The diagnostic intentionally exits nonzero when a case fails;
it does not invert or suppress the WebKit failure.

Inference: the local WebKit/Playwright offline-emulation path prevents even a
synthetic service-worker response from being delivered. This reproduces without
the application's PWA stack, so changing Workbox caching would not address the
isolated failure. The evidence does not identify a precise upstream source-code
defect or prove behavior in shipping Safari. Playwright's
[service-worker documentation](https://playwright.dev/docs/service-workers)
also warns that its service-worker support is Chromium-only; that warning is
context, not a substitute for the reproduction above.

## Supplemental actual-app origin-unavailable acceptance

```powershell
npx playwright test --config=diagnostics/playwright.config.ts
```

Result against the existing production `dist`: **2 passed**, Chromium and WebKit
(20.5 seconds total in the initial run). `app-server-stopped.spec.ts` serves the
existing build on its own ephemeral port, marks HTTP responses `no-store`, then
stops only its own server. It neither rebuilds nor interrupts preview port 4173.
This is a supplemental origin-unavailable check, not a replacement for the
original airplane-mode-like emulation test.

After proving worker control and app precaching, this test verifies:

1. Reload with the server stopped retains four pending warmups.
2. Closing the page and opening a fresh page starts the cached app.
3. An uncached request fails, proving the origin is unavailable.
4. Resuming the workout retains the four warmups without counting them as sets.
5. Explicitly logging one warmup and one working set creates two persisted sets.
6. A further offline reload retains those sets and three pending warmups.
7. Finishing/saving yields history with exactly the two logged sets, not pending
   warmups, and history survives another reload.
8. No JavaScript page errors are raised during the flow.

Attachments are generated under `output/playwright/origin-stopped/`. The ordinary
resilience suite remains unchanged and still exposes the WebKit emulation
failure. The supplementary pass increases confidence in cached startup and
workout persistence with an unavailable origin; it does **not** prove full
device-level offline behavior, installed-PWA lifecycle, browser-process restart,
cache eviction resilience, or physical-iPhone acceptance.

## 2026-08-31 — Acceptance transport decision (subsequent to the evidence above)

The ordinary resilience test now serves the production `dist` from its own
ephemeral loopback HTTP origin with `Cache-Control: no-store`. After checking
activation, controller ownership, cached app HTML, and the cached exercise
catalog, it closes that server's listener and all existing connections. It never
stops the shared preview server. Fixture teardown also closes the private server
when an earlier assertion fails.

Both desktop engines exercise the same unavailable-origin reload, fresh-page
startup, uncached-request failure, four pending warmups, explicit warmup/working
set logging, persistence reloads, completion/history, and zero-page-error
assertions from the original acceptance flow. Chromium additionally keeps
`context.setOffline(true)` and checks the offline banner on the existing page
before reloading. A fresh page's `navigator.onLine` may still report true, so
the uncached request failure, not that hint, proves origin unavailability. WebKit does not
call that broken emulation path in the application acceptance flow.

This changes the acceptance transport, not the app or its PWA configuration.
The app-independent `service-worker-offline-repro.mjs` is unchanged and still
returns a nonzero exit status when the WebKit emulation case fails. The earlier
results and statements above describe the pre-change investigation, not the
current acceptance-test implementation. The separate diagnostic app test also
remains unchanged as historical reproduction evidence.

The common browser gate proves cached startup and local workout persistence
when this HTTP origin is unavailable; it does not prove that all network access
is disabled, or physical-iPhone, installed-PWA, browser-process restart, or
airplane-mode behavior. Run the corrected gate without rebuilding with:

```powershell
npx playwright test tests/browser/resilience.spec.ts --grep 'cached offline startup' --reporter=list
```

Verification results for the corrected transport must come from that run; the
earlier supplemental passes are not represented as a pass of the new gate.
