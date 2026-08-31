// Supplemental origin-unavailable check. This does NOT replace the enabled
// context.setOffline test, simulate airplane mode, or verify a physical iPhone.
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, resolve, sep } from "node:path";
import { test, expect } from "@playwright/test";

test("cached app resumes and finishes with its HTTP origin stopped", async ({
  page,
  context,
}, testInfo) => {
  test.setTimeout(120_000);
  const root = resolve(import.meta.dirname, "../dist");
  const types: Record<string, string> = {
    ".html": "text/html",
    ".js": "application/javascript",
    ".css": "text/css",
    ".json": "application/json",
    ".webmanifest": "application/manifest+json",
    ".png": "image/png",
    ".webp": "image/webp",
    ".svg": "image/svg+xml",
    ".ttf": "font/ttf",
  };
  const server = createServer(async (request, response) => {
    try {
      const url = new URL(request.url || "/", "http://localhost");
      if (!url.pathname.startsWith("/Ironlog/"))
        throw new Error("Outside base");
      const relative = decodeURIComponent(
        url.pathname.slice("/Ironlog/".length),
      );
      const file = resolve(
        root,
        relative + (relative.endsWith("/") || !relative ? "index.html" : ""),
      );
      if (!file.startsWith(root + sep)) throw new Error("Outside dist");
      const body = await readFile(file);
      response.writeHead(200, {
        "Content-Type": types[extname(file)] || "application/octet-stream",
        "Cache-Control": "no-store",
      });
      response.end(body);
    } catch {
      response.writeHead(404);
      response.end("Not found");
    }
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  if (!address || typeof address === "string")
    throw new Error("No server address");
  const appUrl = `http://127.0.0.1:${address.port}/Ironlog/app/`;
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(String(error)));
  try {
    await page.goto(appUrl);
    await page.getByLabel("Your name").fill("Origin Offline QA");
    for (const heading of [
      "What are you training for?",
      "Your training, your pace.",
      "A starting point.",
    ]) {
      await page.getByRole("button", { name: "Continue", exact: true }).click();
      await expect(page.getByRole("heading", { name: heading })).toBeVisible();
    }
    await page
      .getByRole("button", { name: "Start training", exact: true })
      .click();
    await page
      .getByRole("button", { name: "Start freestyle", exact: true })
      .click();
    await page
      .getByRole("button", { name: "Add exercise", exact: true })
      .click();
    await page.getByLabel("Exercise name").fill("Barbell Bench Press");
    await page
      .getByRole("button", { name: /^Barbell Bench Press/ })
      .first()
      .click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await page.getByLabel("KG", { exact: true }).fill("65");
    await page
      .getByRole("button", { name: "Insert warmups", exact: true })
      .click();
    await expect(
      page.getByRole("button", { name: "Log warmup", exact: true }),
    ).toHaveCount(4);
    await expect(page.locator(".sets .set-row")).toHaveCount(0);
    await expect
      .poll(() =>
        page.evaluate(
          async () =>
            (await navigator.serviceWorker.getRegistration())?.active?.state,
        ),
      )
      .toBe("activated");
    await page.reload();
    await expect
      .poll(() =>
        page.evaluate(() => Boolean(navigator.serviceWorker.controller)),
      )
      .toBe(true);
    const cachesBefore = await page.evaluate(async () =>
      Promise.all(
        (await caches.keys()).map(async (name) => ({
          name,
          urls: (await (await caches.open(name)).keys()).map(
            (request) => request.url,
          ),
        })),
      ),
    );
    expect(
      cachesBefore
        .flatMap((cache) => cache.urls)
        .map((url) => new URL(url).pathname),
    ).toContain("/Ironlog/app/index.html");
    await testInfo.attach("precache-before-origin-stopped", {
      body: JSON.stringify(cachesBefore, null, 2),
      contentType: "application/json",
    });

    // Close the actual server and all existing sockets, not merely route mocks.
    server.closeAllConnections();
    await new Promise<void>((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
    expect(server.listening).toBe(false);
    await page.reload();
    await expect(
      page.getByRole("button", { name: "Log warmup", exact: true }),
    ).toHaveCount(4);
    await page.close();
    const resumed = await context.newPage();
    resumed.on("pageerror", (error) => errors.push(String(error)));
    await resumed.goto(appUrl + "#/home");
    await expect(
      resumed.getByRole("heading", { name: "Origin Offline QA" }),
    ).toBeVisible();
    const probe = await resumed.evaluate(async () => ({
      onlineHint: navigator.onLine,
      uncachedRequestFailed: await fetch(
        `/Ironlog/uncached-${crypto.randomUUID()}`,
        { cache: "no-store" },
      ).then(
        () => false,
        () => true,
      ),
    }));
    await testInfo.attach("origin-unavailable-probe", {
      body: JSON.stringify(probe),
      contentType: "application/json",
    });
    expect(probe.uncachedRequestFailed).toBe(true);
    await resumed
      .getByRole("button", { name: "Resume workout", exact: true })
      .click();
    await expect(
      resumed.getByRole("button", { name: "Log warmup", exact: true }),
    ).toHaveCount(4);
    await expect(resumed.locator(".sets .set-row")).toHaveCount(0);
    await resumed
      .getByRole("button", { name: "Log warmup", exact: true })
      .first()
      .click();
    await expect(resumed.locator(".sets .set-row")).toHaveCount(1);
    await resumed.getByLabel("KG", { exact: true }).fill("65");
    await resumed.getByLabel("Reps", { exact: true }).fill("8");
    await resumed
      .locator(".exercise-card")
      .getByRole("button", { name: "Log", exact: true })
      .click();
    await expect(resumed.locator(".sets")).toContainText("65 kg × 8");
    await resumed.reload();
    await expect(resumed.locator(".sets .set-row")).toHaveCount(2);
    await expect(
      resumed.getByRole("button", { name: "Log warmup", exact: true }),
    ).toHaveCount(3);
    await resumed
      .getByRole("button", { name: "Finish workout", exact: true })
      .click();
    await resumed
      .getByRole("button", { name: "Save completed workout", exact: true })
      .click();
    await expect(
      resumed.getByRole("heading", { name: "Training log" }),
    ).toBeVisible();
    await resumed.getByRole("button", { name: /Freestyle workout/ }).click();
    await expect(resumed.locator(".set-row")).toHaveCount(2);
    await expect(
      resumed.getByRole("button", { name: "Log warmup", exact: true }),
    ).toHaveCount(0);
    await resumed.reload();
    await expect(resumed.locator(".set-row")).toHaveCount(2);
    expect(errors).toEqual([]);
  } finally {
    server.closeAllConnections();
    if (server.listening)
      await new Promise<void>((resolve) => server.close(() => resolve()));
  }
});
