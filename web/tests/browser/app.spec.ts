import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
async function onboard(page: Page) {
  await page.goto("app/");
  await page.getByLabel("Your name").fill("QA Athlete");
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page
    .getByRole("heading", { name: "What are you training for?" })
    .waitFor();
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page
    .getByRole("heading", { name: "Your training, your pace." })
    .waitFor();
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.getByRole("heading", { name: "A starting point." }).waitFor();
  await page
    .getByRole("button", { name: "Start training", exact: true })
    .click();
  await page.getByRole("heading", { name: "QA Athlete" }).waitFor();
}
test("landing native themes and first launch", async ({ page }, testInfo) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(String(e)));
  await page.goto("./");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Train.Recover.Prove it.",
  );
  await expect(page.locator(".hero h1")).toHaveCSS("font-weight", "350");
  await expect(page.locator(".theme-showcase h2")).toHaveCSS(
    "font-weight",
    "450",
  );
  await expect(page.locator(".brand").first()).toHaveCSS("font-weight", "500");
  await page.screenshot({
    path: `output/playwright/${testInfo.project.name}-landing.png`,
    fullPage: true,
  });
  const logo = page.locator(".brand img").first();
  await expect(logo).toHaveAttribute("src", /ironlog-logo\.svg$/);
  await expect(logo).toHaveCSS("filter", "none");
  await page.getByRole("button", { name: "Light", exact: true }).click();
  await expect(logo).toHaveCSS("filter", "invert(1)");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: `output/playwright/${testInfo.project.name}-transparent-logo-light.png`,
  });
  await page.getByRole("button", { name: "Deep Forest", exact: true }).click();
  await expect(page.locator("html")).toHaveAttribute(
    "data-theme",
    "deep_forest",
  );
  await page.getByRole("link", { name: "Try App in web instead" }).click();
  await expect(
    page
      .frameLocator('iframe[title="IronLog interactive app preview"]')
      .getByRole("heading", { name: "Make it your own." }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Open app full-screen", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Make it your own." }),
  ).toBeVisible();
  // Lighter editorial typography must not leak into the native-style app.
  await expect(
    page.getByRole("heading", { name: "Make it your own." }),
  ).toHaveCSS("font-weight", "800");
  await expect(page.locator("html")).toHaveAttribute(
    "data-theme",
    "deep_forest",
  );
  await page.screenshot({
    path: `output/playwright/${testInfo.project.name}-onboarding.png`,
    fullPage: true,
  });
  expect(errors).toEqual([]);
});
test("workout survives reload, pending warmups never self-log, deletion persists", async ({
  page,
}, testInfo) => {
  await onboard(page);
  await page
    .getByRole("button", { name: "Start freestyle", exact: true })
    .click();
  await page.getByRole("button", { name: "Add exercise", exact: true }).click();
  await page.getByLabel("Exercise name").fill("Barbell Bench Press");
  await page
    .getByRole("button", { name: /^Barbell Bench Press/ })
    .first()
    .click();
  const card = page.locator(".exercise-card").first();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await card.getByLabel("KG", { exact: true }).fill("65");
  await expect(card.getByLabel("KG", { exact: true })).toHaveValue("65");
  await card.getByLabel("Reps", { exact: true }).fill("8");
  await expect(card.getByLabel("KG", { exact: true })).toHaveValue("65");
  await card.getByRole("button", { name: "Insert warmups" }).click();
  await expect(card.getByText("Warmup targets · not logged")).toBeVisible();
  await expect(card.locator(".sets .set-row")).toHaveCount(0);
  await page.reload();
  await expect(page.getByText("Warmup targets · not logged")).toBeVisible();
  await expect(page.locator(".sets .set-row")).toHaveCount(0);
  await page
    .getByRole("button", { name: "Log warmup", exact: true })
    .first()
    .click();
  await expect(page.locator(".sets .set-row")).toHaveCount(1);
  await card.getByLabel("KG", { exact: true }).fill("65");
  await card.getByRole("button", { name: "Log", exact: true }).click();
  await expect(page.locator(".sets .set-row")).toHaveCount(2);
  await card.getByRole("button", { name: /Options for/ }).click();
  await page
    .getByRole("button", { name: "Plate calculator", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toHaveCSS(
    "background-color",
    "rgb(28, 28, 30)",
  );
  await expect(
    page.getByRole("img", {
      name: "20 kg bar, 1 × 20 kg each side, 1 × 2.5 kg each side",
    }),
  ).toBeVisible();
  await page.screenshot({
    path: `output/playwright/${testInfo.project.name}-plates.png`,
    fullPage: true,
  });
  await page.getByRole("button", { name: "Close Plate calculator" }).click();
  await card
    .getByRole("button", { name: "Delete set 2 of Barbell Bench Press" })
    .click();
  await expect(page.locator(".sets .set-row")).toHaveCount(1);
  await page.reload();
  await expect(page.locator(".sets .set-row")).toHaveCount(1);
  await page
    .getByRole("button", { name: "Finish workout", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Save completed workout", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Training log" }),
  ).toBeVisible();
  await page.getByRole("button", { name: /Freestyle workout/ }).click();
  await expect(page.locator(".set-row")).toHaveCount(1);
});
test("every route, themes, narrow and large-text layouts", async ({
  page,
}, testInfo) => {
  await onboard(page);
  for (const route of [
    "home",
    "plans",
    "log",
    "stats",
    "settings",
    "recovery",
    "ledger",
    "body",
    "photos",
    "analytics",
    "intelligence",
    "research",
  ]) {
    await page.goto(`app/#/${route}`);
    await expect(page.locator("h1,h2").first()).toBeVisible();
    await page.screenshot({
      path: `output/playwright/${testInfo.project.name}-${route}.png`,
      fullPage: true,
    });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth + 1,
      ),
      `${route} overflow`,
    ).toBe(true);
  }
  await page.goto("app/#/settings");
  for (const theme of [
    "Obsidian Silver",
    "Deep Forest",
    "Titanium Blue",
    "Monet",
    "Royal Amethyst",
    "Midnight Teal",
    "Crimson Steel",
    "Burnt Terracotta",
    "Electric Lemon",
    "Dark",
    "AMOLED",
    "Light",
  ]) {
    await page
      .getByRole("button", {
        name: theme === "Monet" ? /Monet/ : theme,
        exact: theme !== "Monet",
      })
      .click();
    await expect(
      page.getByRole("button", {
        name: theme === "Monet" ? /Monet/ : theme,
        exact: theme !== "Monet",
      }),
    ).toHaveAttribute("aria-pressed", "true");
  }
  await page.setViewportSize({ width: 320, height: 568 });
  await page.evaluate(() => (document.documentElement.style.fontSize = "22px"));
  await page.screenshot({
    path: `output/playwright/${testInfo.project.name}-settings-large.png`,
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
  ).toBe(true);
  const result = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(
    result.violations.map((x) => ({
      id: x.id,
      nodes: x.nodes.map((n) => n.target),
    })),
  ).toEqual([]);
});
