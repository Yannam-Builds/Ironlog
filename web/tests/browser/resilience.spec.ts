import { test as base, expect, type Page } from "@playwright/test";
import { startTestOrigin } from "../helpers/test-origin";
import { completeOnboarding } from "./onboarding";

const test = base.extend<{
  testOrigin: Awaited<ReturnType<typeof startTestOrigin>>;
}>({
  testOrigin: async ({}, use) => {
    const origin = await startTestOrigin();
    try {
      await use(origin);
    } finally {
      await origin.stop();
    }
  },
});

// Synthetic UI-created data only. These are desktop browser-engine checks,
// not a claim of physical iPhone or installed Home Screen verification.
async function onboard(page: Page, appUrl = "app/") {
  await page.goto(appUrl);
  await completeOnboarding(page, "Resilience QA Athlete");
}

async function startBenchSession(page: Page) {
  await page
    .getByRole("button", { name: "Start freestyle", exact: true })
    .click();
  await page.getByRole("button", { name: "Add exercise", exact: true }).click();
  await page.getByLabel("Exercise name").fill("Barbell Bench Press");
  await page
    .getByRole("button", { name: /^Barbell Bench Press/ })
    .first()
    .click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.locator(".exercise-card")).toHaveCount(1);
}

async function logWorkingSet(page: Page, weight: string, reps: string) {
  const card = page.locator(".exercise-card");
  await card.getByLabel("KG", { exact: true }).fill(weight);
  await card.getByLabel("Reps", { exact: true }).fill(reps);
  await card.getByRole("button", { name: "Log", exact: true }).click();
  await expect(card.locator(".sets")).toContainText(`${weight} kg × ${reps}`);
}

test("cached offline startup resumes pending warmups and saves with its origin unavailable", async ({
  page,
  context,
  browserName,
  testOrigin,
}, testInfo) => {
  test.setTimeout(120_000);
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(String(error)));
  await onboard(page, testOrigin.appUrl);
  await startBenchSession(page);
  const card = page.locator(".exercise-card");
  await card.getByLabel("KG", { exact: true }).fill("65");
  await card
    .getByRole("button", { name: "Insert warmups", exact: true })
    .click();
  await expect(
    card.getByRole("button", { name: "Log warmup", exact: true }),
  ).toHaveCount(4);
  await expect(card.locator(".sets .set-row")).toHaveCount(0);

  // Check registration and cache contents independently: a broken worker can
  // activate without successfully installing its precache handlers.
  await expect
    .poll(
      () =>
        page.evaluate(async () => {
          const registration = await navigator.serviceWorker.getRegistration();
          return registration?.active?.state;
        }),
      { timeout: 30_000 },
    )
    .toBe("activated");
  await page.reload();
  await expect
    .poll(() =>
      page.evaluate(() => Boolean(navigator.serviceWorker.controller)),
    )
    .toBe(true);
  await testInfo.attach("service-worker-cache", {
    body: JSON.stringify(
      await page.evaluate(async () => ({
        url: location.href,
        controller: navigator.serviceWorker.controller?.scriptURL,
        registrations: await Promise.all(
          (await navigator.serviceWorker.getRegistrations()).map(
            async (registration) => ({
              scope: registration.scope,
              active: registration.active?.scriptURL,
            }),
          ),
        ),
        caches: await Promise.all(
          (await caches.keys()).map(async (name) => ({
            name,
            keys: (await (await caches.open(name)).keys()).map(
              (request) => request.url,
            ),
          })),
        ),
      })),
      null,
      2,
    ),
    contentType: "application/json",
  });
  const cachedPaths = await page.evaluate(async () => {
    const requests = await Promise.all(
      (await caches.keys()).map(async (name) =>
        (await caches.open(name)).keys(),
      ),
    );
    return requests.flat().map((request) => new URL(request.url).pathname);
  });
  expect(
    cachedPaths,
    "the activated worker must precache the app HTML",
  ).toContain("/Ironlog/app/index.html");
  expect(
    cachedPaths,
    "offline startup needs the bundled exercise catalog",
  ).toContain("/Ironlog/data/exercises.json");
  const appUrl = new URL("../", page.url().split("#")[0]);
  appUrl.pathname = `${appUrl.pathname}app/`;
  appUrl.hash = "/home";

  // WebKit's setOffline breaks even synthetic worker responses in the
  // app-independent reproduction; see diagnostics/OFFLINE-WEBKIT.md.
  // Both engines lose their real HTTP origin. Chromium additionally exercises
  // browser offline emulation and the application's connectivity banner.
  await testOrigin.stop();
  expect(testOrigin.listening).toBe(false);
  if (browserName === "chromium") {
    await context.setOffline(true);
    await expect(page.locator(".offline")).toBeVisible();
  }
  await testInfo.attach("offline-transport", {
    body: JSON.stringify({
      origin: testOrigin.appUrl,
      originListening: testOrigin.listening,
      browserOfflineEmulation: browserName === "chromium",
    }),
    contentType: "application/json",
  });
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Log warmup", exact: true }),
  ).toHaveCount(4);
  await page.close();
  const resumed = await context.newPage();
  resumed.on("pageerror", (error) => errors.push(String(error)));
  await resumed.goto(appUrl.toString());
  await expect(
    resumed.getByRole("heading", { name: "Resilience QA Athlete" }),
  ).toBeVisible();
  const offlineProbe = await resumed.evaluate(async () => ({
    // navigator.onLine is a connectivity hint, not proof that requests work.
    onlineHint: navigator.onLine,
    uncachedRequestFailed: await fetch(
      `/Ironlog/offline-probe-${crypto.randomUUID()}`,
      { cache: "no-store" },
    ).then(
      () => false,
      () => true,
    ),
  }));
  await testInfo.attach("offline-network-probe", {
    body: JSON.stringify(offlineProbe),
    contentType: "application/json",
  });
  expect(offlineProbe.uncachedRequestFailed).toBe(true);
  if (!offlineProbe.onlineHint) {
    await expect(resumed.locator(".offline")).toBeVisible();
  }
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
  await expect(
    resumed.getByRole("button", { name: "Log warmup", exact: true }),
  ).toHaveCount(3);
  await logWorkingSet(resumed, "65", "8");
  await resumed.reload();
  await expect(resumed.locator(".sets .set-row")).toHaveCount(2);
  await expect(
    resumed.getByRole("button", { name: "Log warmup", exact: true }),
  ).toHaveCount(3);
  await expect(resumed.locator(".sets")).toContainText("65 kg × 8");

  await resumed
    .getByRole("button", { name: "Finish workout", exact: true })
    .click();
  await resumed
    .getByRole("button", { name: "Save completed workout", exact: true })
    .click();
  await expect(
    resumed.getByRole("heading", { name: "History", exact: true }),
  ).toBeVisible();
  await resumed.getByRole("button", { name: /Freestyle workout/ }).click();
  await expect(resumed.locator(".set-row")).toHaveCount(2);
  await expect(
    resumed.getByRole("button", { name: "Log warmup", exact: true }),
  ).toHaveCount(0);
  await resumed.reload();
  await expect(resumed.locator(".set-row")).toHaveCount(2);
  expect(errors).toEqual([]);
});

test("two tabs share one active session and retain writes, deletion, and completion", async ({
  page,
  context,
}) => {
  test.setTimeout(120_000);
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(String(error)));
  await onboard(page);
  await startBenchSession(page);
  const second = await context.newPage();
  second.on("pageerror", (error) => errors.push(String(error)));
  await second.goto("app/#/home");
  await second
    .getByRole("button", { name: "Resume workout", exact: true })
    .click();
  await expect(second.locator(".exercise-card")).toHaveCount(1);

  await logWorkingSet(page, "65", "8");
  await expect(second.locator(".sets .set-row")).toHaveCount(1);
  await expect(second.locator(".sets")).toContainText("65 kg × 8");
  await logWorkingSet(second, "70", "6");
  await expect(page.locator(".sets .set-row")).toHaveCount(2);
  await expect(page.locator(".sets")).toContainText("70 kg × 6");

  await page
    .locator(".sets .set-row")
    .first()
    .getByRole("button", { name: /^Delete set 1 of / })
    .click();
  await expect(page.locator(".sets .set-row")).toHaveCount(1);
  await expect(second.locator(".sets .set-row")).toHaveCount(1);
  for (const tab of [page, second]) {
    await tab.reload();
    await expect(tab.locator(".sets .set-row")).toHaveCount(1);
    await expect(tab.locator(".sets")).toContainText("70 kg × 6");
    await expect(tab.locator(".sets")).not.toContainText("65 kg × 8");
  }

  await second
    .getByRole("button", { name: "Finish workout", exact: true })
    .click();
  await second
    .getByRole("button", { name: "Save completed workout", exact: true })
    .click();
  await expect(
    second.getByRole("heading", { name: "History", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "No active workout" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Log", exact: true }).click();
  await expect(page.locator(".history-row")).toHaveCount(1);
  await page.locator(".history-row").click();
  await expect(page.locator(".set-row")).toHaveCount(1);
  await expect(page.locator(".set-row")).toContainText("70 kg × 6");
  expect(errors).toEqual([]);
});
