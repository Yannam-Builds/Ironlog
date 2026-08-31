// App-independent diagnostic: no Vite, React, Workbox, or IronLog assets.
// Run: node diagnostics/service-worker-offline-repro.mjs
import { createServer } from "node:http";
import assert from "node:assert/strict";
import { chromium, webkit, devices } from "@playwright/test";

const html = `<!doctype html><title>Offline minimal reproduction</title>
<h1>Cached document</h1><script>
navigator.serviceWorker.register('/sw.js');
</script>`;
const worker = `
self.addEventListener('install', event => {
  event.waitUntil(caches.open('minimal-v1').then(cache => cache.add('/')));
});
self.addEventListener('activate', event => event.waitUntil(clients.claim()));
self.addEventListener('fetch', event => {
  const path = new URL(event.request.url).pathname;
  if (path === '/worker-probe') {
    event.respondWith(new Response('handled-by-worker'));
  } else if (path === '/') {
    event.respondWith(caches.match('/'));
  }
});`;

async function run(browserType, mode) {
  const requests = [];
  const server = createServer((request, response) => {
    requests.push(request.url);
    response.setHeader("Cache-Control", "no-store");
    response.setHeader(
      "Content-Type",
      request.url === "/sw.js" ? "application/javascript" : "text/html",
    );
    response.end(request.url === "/sw.js" ? worker : html);
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const url = `http://127.0.0.1:${server.address().port}/`;
  const browser = await browserType.launch();
  const context = await browser.newContext({ ...devices["iPhone 13"] });
  const result = {
    browser: browserType.name(),
    version: browser.version(),
    mode,
    url,
  };
  try {
    const page = await context.newPage();
    page.setDefaultNavigationTimeout(10_000);
    await page.goto(url);
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.waitForFunction(() =>
      Boolean(navigator.serviceWorker.controller),
    );
    result.before = await page.evaluate(async () => ({
      controller: navigator.serviceWorker.controller.scriptURL,
      cached: Boolean(await caches.match("/")),
      workerProbe: await fetch("/worker-probe").then((response) =>
        response.text(),
      ),
    }));
    assert.equal(result.before.cached, true);
    assert.equal(result.before.workerProbe, "handled-by-worker");
    await page.reload();
    result.onlineReload = await page.locator("h1").textContent();
    assert.equal(result.onlineReload, "Cached document");
    if (mode === "setOffline") await context.setOffline(true);
    else {
      server.closeAllConnections();
      await new Promise((resolve) => server.close(resolve));
    }
    result.offlineProbe = await page.evaluate(async () => ({
      onlineHint: navigator.onLine,
      workerProbe: await fetch("/worker-probe")
        .then((response) => response.text())
        .catch(String),
      uncachedRequestFailed: await fetch("/network-only", {
        cache: "no-store",
      }).then(
        () => false,
        () => true,
      ),
    }));
    assert.equal(result.offlineProbe.uncachedRequestFailed, true);
    await page.reload();
    result.offlineReload = await page.locator("h1").textContent();
    assert.equal(result.offlineReload, "Cached document");
    await page.close();
    const fresh = await context.newPage();
    await fresh.goto(url, { timeout: 10_000 });
    result.offlineFreshPage = await fresh.locator("h1").textContent();
    assert.equal(result.offlineFreshPage, "Cached document");
  } catch (error) {
    result.error = String(error);
  } finally {
    result.serverRequests = requests;
    await browser.close();
    server.closeAllConnections();
    if (server.listening) await new Promise((resolve) => server.close(resolve));
  }
  console.log(JSON.stringify(result, null, 2));
  return result;
}

const results = [];
for (const browserType of [chromium, webkit]) {
  for (const mode of ["setOffline", "serverStopped"]) {
    results.push(await run(browserType, mode));
  }
}
if (results.some((result) => result.error)) process.exitCode = 1;
