import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
async function onboard(page: Page) {
  await page.goto("app/");
  await page
    .getByRole("button", { name: "Build my training system", exact: true })
    .click();
  await page
    .getByRole("heading", { name: "What should your ledger call you?" })
    .waitFor();
  await page.getByLabel("Your name").fill("QA Athlete");
  await page
    .getByRole("button", { name: "Continue as QA Athlete", exact: true })
    .click();
  await page
    .getByRole("heading", { name: "Tell us where training begins." })
    .waitFor();
  await page
    .getByRole("button", { name: "Use this baseline", exact: true })
    .click();
  await page
    .getByRole("heading", { name: "How should progression begin?" })
    .waitFor();
  await page
    .getByRole("button", { name: "Use this progression", exact: true })
    .click();
  await page
    .getByRole("heading", { name: "Choose days you can actually protect." })
    .waitFor();
  await page
    .getByRole("button", { name: "Save weekly rhythm", exact: true })
    .click();
  await page
    .getByRole("heading", { name: "What should the plan optimize first?" })
    .waitFor();
  await page.getByRole("button", { name: "Use this goal", exact: true }).click();
  await page.getByRole("heading", { name: "Local by default." }).waitFor();
  await page
    .getByRole("button", { name: "Continue with local coaching", exact: true })
    .click();
  await page.getByRole("heading", { name: "Browser capabilities" }).waitFor();
  await page
    .getByRole("button", { name: "Continue without integrations", exact: true })
    .click();
  await page
    .getByRole("heading", { name: "Your provisional profile is ready." })
    .waitFor();
  await page
    .getByRole("button", { name: "Save my baseline", exact: true })
    .click();
  await page.getByRole("heading", { name: /Start with structure/i }).waitFor();
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
      .getByRole("heading", { name: "Train with evidence. Progress like a game." }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Open app full-screen", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Train with evidence. Progress like a game." }),
  ).toBeVisible();
  // Lighter editorial typography must not leak into the native-style app.
  await expect(
    page.getByRole("heading", { name: "Train with evidence. Progress like a game." }),
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
  await expect(page.locator(".rest-banner")).toContainText("Barbell Bench Press");
  await expect(page.locator(".rest-banner")).toContainText("65 kg × 8");
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
  await page.locator(".exercise-card").first().getByLabel("KG", { exact: true }).fill("65");
  await page.locator(".exercise-card").first().getByLabel("Reps", { exact: true }).fill("8");
  await page.locator(".exercise-card").first().getByRole("button", { name: "Log", exact: true }).click();
  await expect(page.locator(".sets .set-row")).toHaveCount(2);
  await page
    .getByRole("button", { name: "Finish workout", exact: true })
    .click();
  await expect(page.getByRole("heading", { name: "Session complete" })).toBeVisible();
  await expect(page.locator(".completion-stats")).toContainText("Work sets");
  await page
    .getByRole("button", { name: "Save completed workout", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "History", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: /Freestyle workout/ }).click();
  await expect(page.locator(".set-row")).toHaveCount(2);
  await page.getByRole("link", { name: "Home", exact: true }).click();
  await page.getByRole("button", { name: "Start freestyle", exact: true }).click();
  await page.getByRole("button", { name: "Add exercise", exact: true }).click();
  await page.getByLabel("Exercise name").fill("Barbell Bench Press");
  await page.getByRole("button", { name: /^Barbell Bench Press/ }).first().click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.getByRole("button", { name: "Recent performance", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Recent performance" })).toBeVisible();
  await expect(page.getByText("Same tracking and equipment")).toBeVisible();
  await expect(page.getByText("65 kg × 8")).toBeVisible();
});
test("active workout exercise cards reorder by pointer and persist", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 1800 });
  await onboard(page);
  await page.getByRole("button", { name: "Start freestyle", exact: true }).click();
  for (const name of ["Barbell Bench Press", "Barbell Row"]) {
    await page.getByRole("button", { name: "Add exercise", exact: true }).click();
    await page.getByLabel("Exercise name").fill(name);
    await page.getByRole("button", { name: new RegExp(`^${name}`) }).first().click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
  }
  await expect(page.locator(".exercise-card")).toHaveCount(2);
  await page.locator(".exercise-card").last().evaluate(async (card) => {
    await Promise.all(card.getAnimations().map((animation) => animation.finished));
  });

  const dragged = page.getByRole("button", { name: "Drag to reorder Barbell Row" });
  const target = page.getByRole("button", { name: "Drag to reorder Barbell Bench Press" });
  const draggedBox = await dragged.boundingBox();
  const targetBox = await target.boundingBox();
  expect(draggedBox).not.toBeNull();
  expect(targetBox).not.toBeNull();
  await page.mouse.move(draggedBox!.x + draggedBox!.width / 2, draggedBox!.y + draggedBox!.height / 2);
  await page.mouse.down();
  await page.mouse.move(targetBox!.x + targetBox!.width / 2, targetBox!.y + targetBox!.height / 2, { steps: 8 });
  await page.mouse.up();

  await expect(page.locator("[data-exercise-id]").first()).toContainText("Barbell Row");
  await expect(page.locator(".message")).toContainText("Exercise order saved");
  await page.reload();
  await expect(page.locator("[data-exercise-id]").first()).toContainText("Barbell Row");
});
test("plan cards reorder by pointer and the top plan becomes active", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 2200 });
  await onboard(page);
  await page.getByRole("link", { name: "Plans", exact: true }).click();

  for (const name of ["Plan One", "Plan Two"]) {
    await page.getByRole("button", { name: "New plan", exact: true }).click();
    await page.getByLabel("Plan name").fill(name);
    await page.getByRole("button", { name: "Save plan", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Plans", exact: true })).toBeVisible();
  }

  const dragged = page.getByRole("button", { name: "Drag to reorder Plan Two" });
  const target = page.getByRole("button", { name: "Drag to reorder Plan One" });
  await expect(dragged).toBeEnabled();
  const draggedBox = await dragged.boundingBox();
  const targetBox = await target.boundingBox();
  expect(draggedBox).not.toBeNull();
  expect(targetBox).not.toBeNull();
  await page.mouse.move(draggedBox!.x + draggedBox!.width / 2, draggedBox!.y + draggedBox!.height / 2);
  await page.mouse.down();
  await page.mouse.move(targetBox!.x + targetBox!.width / 2, targetBox!.y + targetBox!.height / 2, { steps: 8 });
  await page.mouse.up();

  const first = page.locator("[data-plan-id]").first();
  await expect(first).toContainText("Plan Two");
  await expect(page.locator(".message")).toContainText("Plan order saved");
  await page.reload();
  await expect(page.locator("[data-plan-id]").first()).toContainText("Plan Two");
  await expect(page.locator("[data-plan-id]").first()).toContainText("Active program");
  await page.getByRole("link", { name: "Home", exact: true }).click();
  const shine = await page.locator(".today-card .card-shine-layer").evaluate((svg) => {
    const gradient = svg.querySelector("linearGradient");
    const animations = gradient?.querySelectorAll("animate") ?? [];
    return {
      preserveAspectRatio: svg.getAttribute("preserveAspectRatio"),
      x1: gradient?.getAttribute("x1"),
      x2: gradient?.getAttribute("x2"),
      durations: [...animations].map((animation) => animation.getAttribute("dur")),
    };
  });
  expect(shine).toEqual({
    preserveAspectRatio: "none",
    x1: "-600",
    x2: "300",
    durations: ["10s", "10s"],
  });
  const cardLayout = await page.locator(".today-card").evaluate((card) => {
    const shineLayer = card.querySelector<SVGSVGElement>(".card-shine-layer")!;
    const eyebrow = card.querySelector<HTMLElement>(".eyebrow")!;
    const cardBounds = card.getBoundingClientRect();
    const shineBounds = shineLayer.getBoundingClientRect();
    return {
      layerPosition: getComputedStyle(shineLayer).position,
      layerTop: Math.round(shineBounds.top - cardBounds.top),
      headerTop: Math.round(eyebrow.getBoundingClientRect().top - cardBounds.top),
    };
  });
  expect(cardLayout.layerPosition).toBe("absolute");
  expect(cardLayout.layerTop).toBeLessThanOrEqual(2); // card's one-pixel border
  expect(cardLayout.headerTop).toBeLessThan(60);
});
test("primary tabs swipe one page at a time in both directions", async ({ page }) => {
  await onboard(page);
  const swipe = async (fromX: number, toX: number, fromY = 120, toY = 120) => {
    await page.mouse.move(fromX, fromY);
    await page.mouse.down();
    await page.mouse.move(toX, toY, { steps: 8 });
    await page.mouse.up();
  };

  await swipe(330, 70);
  await expect(page.getByRole("heading", { name: "Plans", exact: true })).toBeVisible();
  await swipe(330, 70);
  await expect(page.getByRole("heading", { name: "History", exact: true })).toBeVisible();
  await swipe(330, 70);
  await expect(page.getByRole("heading", { name: "Stats", exact: true })).toBeVisible();
  await swipe(330, 70);
  await expect(page.getByRole("heading", { name: "Training Console", exact: true })).toBeVisible();
  await swipe(330, 70);
  await expect(page.getByRole("heading", { name: "Training Console", exact: true })).toBeVisible();

  await swipe(70, 330);
  await expect(page.getByRole("heading", { name: "Stats", exact: true })).toBeVisible();
  await swipe(70, 330);
  await expect(page.getByRole("heading", { name: "History", exact: true })).toBeVisible();
  await swipe(70, 330);
  await expect(page.getByRole("heading", { name: "Plans", exact: true })).toBeVisible();

  await swipe(190, 180, 250, 440);
  await expect(page.getByRole("heading", { name: "Plans", exact: true })).toBeVisible();
  await swipe(70, 330);
  await expect(page.getByRole("heading", { name: "QA Athlete", exact: true })).toBeVisible();
  await swipe(70, 330);
  await expect(page.getByRole("heading", { name: "QA Athlete", exact: true })).toBeVisible();
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
  await page.getByRole("button", { name: /^Appearance/ }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Appearance", exact: true })).toBeVisible();
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
  await page.locator(".route-stage").evaluate(async (stage) => {
    await Promise.all(stage.getAnimations().map((animation) => animation.finished));
  });
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
