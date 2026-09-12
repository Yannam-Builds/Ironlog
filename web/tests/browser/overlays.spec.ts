import { test, expect, type Page } from "@playwright/test";

async function onboard(page: Page) {
  await page.goto("app/");
  await page.getByLabel("Your name").fill("Overlay QA");
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
  await expect(page.getByRole("heading", { name: "Overlay QA" })).toBeVisible();
}

test("route focus does not steal a newly focused control on a delayed frame", async ({ page }) => {
  await onboard(page);
  await page.evaluate(() => {
    const callbacks: FrameRequestCallback[] = [];
    const original = window.requestAnimationFrame;
    window.requestAnimationFrame = (callback) => callbacks.push(callback);
    Object.assign(window, {
      flushRouteFrames: () => {
        window.requestAnimationFrame = original;
        callbacks.splice(0).forEach((callback) => callback(performance.now()));
      },
    });
    location.hash = "#/plans";
  });
  const opener = page.getByRole("button", { name: "Import plan", exact: true });
  await opener.focus();
  await page.evaluate(() => {
    (window as unknown as { flushRouteFrames: () => void }).flushRouteFrames();
  });
  await expect(opener).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("dialog", { name: "Import a plan" })).toBeVisible();
});

test("all themes keep sheets opaque, modal and keyboard dismissible", async ({
  page,
}, info) => {
  test.setTimeout(120000);
  await onboard(page);
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
    await page.goto("app/#/settings");
    await page
      .getByRole("button", {
        name: theme === "Monet" ? /^Monet/ : theme,
        exact: true,
      })
      .click();
    await page.goto("app/#/plans");
    const opener = page.getByRole("button", {
      name: "Import plan",
      exact: true,
    });
    await opener.focus();
    await opener.press("Enter");
    const dialog = page.getByRole("dialog", { name: "Import a plan" });
    await expect(dialog).toBeVisible();
    const surface = await dialog.evaluate((node) => ({
      color: getComputedStyle(node).backgroundColor,
      opacity: getComputedStyle(node).opacity,
      modal: node.matches(":modal"),
      header: getComputedStyle(node.querySelector("header")!).backgroundColor,
    }));
    expect(surface.color, theme).toMatch(/^rgb\(/);
    expect(surface.opacity, theme).toBe("1");
    expect(surface.header, theme).toBe(surface.color);
    expect(surface.modal, theme).toBe(true);
    await page.getByLabel("Or paste plan JSON").focus();
    for (let i = 0; i < 6; i++) {
      await page.keyboard.press("Tab");
      expect(
        await dialog.evaluate((node) => node.contains(document.activeElement)),
        theme,
      ).toBe(true);
    }
    await dialog.getByRole("button", { name: "Close Import a plan" }).focus();
    await page.keyboard.press("Shift+Tab");
    expect(
      await dialog.evaluate((node) => node.contains(document.activeElement)),
      theme,
    ).toBe(true);
    await page.keyboard.press("Tab");
    await expect(
      dialog.getByRole("button", { name: "Close Import a plan" }),
    ).toBeFocused();
    await page.keyboard.press("Escape");
    await expect(dialog).toHaveCount(0);
    await expect(opener).toBeFocused();
  }
  await page.setViewportSize({ width: 320, height: 568 });
  await page.evaluate(() => (document.documentElement.style.fontSize = "32px"));
  await page
    .getByRole("button", { name: "Browse programs", exact: true })
    .click();
  const library = page.getByRole("dialog", { name: "Program library" });
  await expect(library).toBeVisible();
  expect(
    await library.evaluate((node) => node.scrollWidth <= node.clientWidth + 1),
  ).toBe(true);
  await library.locator("button.list-row").last().scrollIntoViewIfNeeded();
  await expect(library.locator("button.list-row").last()).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Close Program library" }),
  ).toBeVisible();
  await page.screenshot({
    path: `output/playwright/${info.project.name}-library-large-text.png`,
  });
});

test("exercise options block background taps and set editing restores keyboard focus", async ({
  page,
}, info) => {
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
  await expect(page.getByRole("dialog")).toHaveCount(0);
  const card = page.locator(".exercise-card");
  const exerciseName = await card
    .getByRole("heading", { level: 2 })
    .innerText();
  await card.getByLabel("KG", { exact: true }).fill("65");
  await expect(card.getByLabel("KG", { exact: true })).toHaveValue("65");
  await card.getByLabel("Reps", { exact: true }).fill("8");
  await card.getByRole("button", { name: "Log", exact: true }).click();
  await expect(card.locator(".sets .set-row")).toHaveCount(1);
  const edit = card.getByRole("button", {
    name: "Edit set 1 of Barbell Bench Press",
  });
  await edit.focus();
  await edit.press("Enter");
  const editor = page.getByRole("dialog", { name: "Edit logged set" });
  await expect(editor).toBeVisible();
  await page.getByLabel("Reps", { exact: true }).last().fill("9");
  await page.keyboard.press("Escape");
  await expect(editor).toHaveCount(0);
  await expect(edit).toBeFocused();
  await expect(card.locator(".sets")).toContainText("65 kg × 8");
  await card
    .getByRole("button", { name: "Options for Barbell Bench Press" })
    .click();
  const options = page.getByRole("dialog", { name: exerciseName, exact: true });
  await expect(options).toBeVisible();
  // Tap where Minimize sits behind the backdrop: dismiss, never navigate away.
  const minimize = await page
    .getByRole("button", { name: "Minimize workout" })
    .boundingBox();
  expect(minimize).not.toBeNull();
  const point = {
    x: minimize!.x + minimize!.width / 2,
    y: minimize!.y + minimize!.height / 2,
  };
  expect(
    await page.evaluate(
      ({ x, y }) => !!document.elementFromPoint(x, y)?.closest("dialog"),
      point,
    ),
  ).toBe(true);
  const box = await options.boundingBox();
  expect(box!.y).toBeGreaterThan(point.y);
  await page.mouse.click(point.x, point.y);
  await expect(options).toHaveCount(0);
  await expect(
    page.getByRole("heading", { name: "Freestyle workout", exact: true }),
  ).toBeVisible();
  await expect(card.locator(".sets .set-row")).toHaveCount(1);
  await page.screenshot({
    path: `output/playwright/${info.project.name}-overlay-retained-set.png`,
  });
});
