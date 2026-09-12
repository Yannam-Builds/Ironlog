import { test, expect } from "@playwright/test";

test("a workout can be logged and completed inside the website phone", async ({
  page,
}, info) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(String(error)));
  await page.goto("./");
  await page
    .getByRole("link", { name: "Try App in web instead", exact: true })
    .click();
  const app = page.frameLocator(
    'iframe[title="IronLog interactive app preview"]',
  );
  await app.getByLabel("Your name").fill("Embedded Workout QA");
  for (const next of [
    "What are you training for?",
    "Your training, your pace.",
    "A starting point.",
  ]) {
    await app.getByRole("button", { name: "Continue", exact: true }).click();
    await expect(app.getByRole("heading", { name: next })).toBeVisible();
  }
  await app
    .getByRole("button", { name: "Start training", exact: true })
    .click();
  await app
    .getByRole("button", { name: "Start freestyle", exact: true })
    .click();
  await app.getByRole("button", { name: "Add exercise", exact: true }).click();
  await app.getByLabel("Exercise name").fill("Barbell Bench Press");
  await app
    .getByRole("button", { name: /^Barbell Bench Press/ })
    .first()
    .click();
  const card = app.locator(".exercise-card").first();
  // Wait for the saved selection to close its modal; background controls are
  // intentionally inert until that transaction finishes.
  await expect(app.getByRole("dialog")).toHaveCount(0);
  await card.getByLabel("KG", { exact: true }).fill("65");
  await expect(card.getByLabel("KG", { exact: true })).toHaveValue("65");
  await card.getByLabel("Reps", { exact: true }).fill("8");
  await expect(card.getByLabel("KG", { exact: true })).toHaveValue("65");
  await card.getByRole("button", { name: "Log", exact: true }).click();
  await expect(card.locator(".sets .set-row")).toHaveCount(1);
  await card.getByRole("button", { name: /Options for/ }).click();
  await app
    .getByRole("button", { name: "Plate calculator", exact: true })
    .click();
  await expect(
    app.getByRole("img", {
      name: "20 kg bar, 1 × 20 kg each side, 1 × 2.5 kg each side",
    }),
  ).toBeVisible();
  await page
    .locator(".phone-frame")
    .screenshot({
      path: `output/playwright/${info.project.name}-embedded-plates.png`,
    });
  await app.getByRole("button", { name: "Close Plate calculator" }).click();
  await app
    .getByRole("button", { name: "Finish workout", exact: true })
    .click();
  await app
    .getByRole("button", { name: "Save completed workout", exact: true })
    .click();
  await expect(
    app.getByRole("heading", { name: "History", exact: true }),
  ).toBeVisible();
  await app.getByRole("button", { name: /Freestyle workout/ }).click();
  await expect(app.locator(".set-row")).toHaveCount(1);
  await expect(page).toHaveURL(/\/Ironlog\/#try-app$/);
  expect(errors).toEqual([]);
});

test("website launches the real app inside a phone without navigating away", async ({
  page,
}, info) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("./");
  await expect(page.locator(".phone-frame")).toBeVisible();
  await expect(page.getByTitle("IronLog interactive app preview")).toHaveCount(
    0,
  );
  await page
    .getByRole("link", { name: "Try App in web instead", exact: true })
    .click();
  await expect(page).toHaveURL(/\/Ironlog\/#try-app$/);
  const app = page.frameLocator(
    'iframe[title="IronLog interactive app preview"]',
  );
  await expect(
    app.getByRole("heading", { name: "Make it your own." }),
  ).toBeVisible();
  await app.getByLabel("Your name").fill("Phone Preview QA");
  await app.getByRole("button", { name: "Continue", exact: true }).click();
  await expect(
    app.getByRole("heading", { name: "What are you training for?" }),
  ).toBeVisible();
  await expect
    .poll(() =>
      app
        .getByRole("heading", { name: "What are you training for?" })
        .evaluate((node) => node.getBoundingClientRect().top),
    )
    .toBeGreaterThanOrEqual(0);
  await page.getByRole("button", { name: "Light", exact: true }).click();
  await expect(app.locator("html")).toHaveAttribute("data-theme", "light");
  // The app's viewport remains phone sized; dialogs and fixed navigation use
  // that viewport, not the surrounding desktop window.
  const frame = page.getByTitle("IronLog interactive app preview");
  const bounds = await frame.boundingBox();
  expect(bounds!.width).toBeGreaterThanOrEqual(320);
  expect(bounds!.width).toBeLessThanOrEqual(430);
  await page.locator(".phone-preview").screenshot({
    path: `output/playwright/${info.project.name}-phone-preview.png`,
  });
  await page.reload();
  await page
    .getByRole("link", { name: "Try App in web instead", exact: true })
    .click();
  await expect(
    app.getByRole("heading", { name: "What are you training for?" }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Open app full-screen", exact: true })
    .click();
  await expect(page).toHaveURL(/\/Ironlog\/app\/$/);
  await expect(
    page.getByRole("heading", { name: "What are you training for?" }),
  ).toBeVisible();
});

test("phone preview fits a narrow mobile page and can open full-screen", async ({
  page,
}) => {
  await page.goto("./");
  await page
    .getByRole("link", { name: "Try App in web instead", exact: true })
    .click();
  await expect(
    page.getByTitle("IronLog interactive app preview"),
  ).toBeVisible();
  for (const width of [320, 360, 700, 768]) {
    await page.setViewportSize({ width, height: 800 });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      `${width}px outer page`,
    ).toBe(true);
    const app = page.frameLocator(
      'iframe[title="IronLog interactive app preview"]',
    );
    await expect(app.getByLabel("Your name")).toBeVisible();
    expect(
      await app
        .locator("html")
        .evaluate((node) => node.scrollWidth <= node.clientWidth),
      `${width}px embedded app`,
    ).toBe(true);
  }
  await page
    .getByRole("link", { name: "Open app full-screen", exact: true })
    .click();
  await expect(page.getByLabel("Your name")).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
