import { test, expect, type FrameLocator } from "@playwright/test";
import registry from "../../src/generated/fonts.json" with { type: "json" };
import { startTestOrigin } from "../helpers/test-origin";
import { completeOnboarding, openOnboardingProfile } from "./onboarding";

async function loadSelected(app: FrameLocator, family: string) {
  return app.locator("html").evaluate(async (_node, id) => {
    const css = id === "lexend" ? "Lexend" : `"IronLog ${id}"`;
    const faces = await document.fonts.load(
      `400 16px ${css}`,
      "Bench 65 kg × 8",
    );
    return faces.length > 0 && faces.every((face) => face.status === "loaded");
  }, family);
}

test("all 21 families and presets apply to landing, embedded app and inputs without narrow overflow", async ({
  page,
}, info) => {
  test.setTimeout(120_000);
  await page.addInitScript(() => {
    if (!localStorage.getItem("ironlog-typography"))
      localStorage.setItem("ironlog-typography", "invalid legacy JSON");
  });
  await page.goto("./");
  await expect(page.locator("html")).toHaveAttribute("data-font", "lexend");
  await page
    .getByRole("link", { name: "Try App in web instead", exact: true })
    .click();
  const app = page.frameLocator(
    'iframe[title="IronLog interactive app preview"]',
  );
  await openOnboardingProfile(app);
  await expect(app.getByLabel("Your name")).toBeVisible();
  for (const font of registry.fonts) {
    await page
      .getByRole("combobox", { name: "Font family", exact: true })
      .selectOption(font.id);
    await expect(app.locator("html")).toHaveAttribute("data-font", font.id);
    expect(
      await loadSelected(app, font.id),
      `${font.name} real face loaded`,
    ).toBe(true);
    for (const preset of ["light", "regular", "bold"]) {
      await page
        .getByRole("combobox", { name: "Font weight", exact: true })
        .selectOption(preset);
      await expect(app.locator("html")).toHaveAttribute(
        "data-font-preset",
        preset,
      );
      const expected = Math.min(
        font.maxWeight,
        preset === "light" ? 450 : preset === "regular" ? 600 : 750,
      );
      await expect(
        app.getByRole("heading", { name: "What should your ledger call you?" }),
      ).toHaveCSS("font-weight", String(expected));
      await expect(page.locator(".hero h1")).toHaveCSS(
        "font-weight",
        String(expected),
      );
      await expect(page.locator(".font-preview h3")).toHaveCSS(
        "font-weight",
        String(expected),
      );
    }
    await page.setViewportSize({ width: 320, height: 800 });
    await page.evaluate(() => {
      document.documentElement.style.fontSize = "200%";
    });
    await app.locator("html").evaluate((el) => {
      el.style.fontSize = "200%";
    });
    // Engines serialize optional CSS family quotes differently.
    await expect
      .poll(() =>
        app
          .getByLabel("Your name")
          .evaluate((el) =>
            getComputedStyle(el).fontFamily.replace(/["']/g, ""),
          ),
      )
      .toBe(
        font.id === "lexend"
          ? "Lexend, system-ui, sans-serif"
          : `IronLog ${font.id}, Lexend, system-ui, sans-serif`,
      );
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      `${font.name} landing`,
    ).toBe(true);
    expect(
      await app
        .locator("html")
        .evaluate((el) => el.scrollWidth <= el.clientWidth),
      `${font.name} app`,
    ).toBe(true);
  }
  await page
    .getByRole("combobox", { name: "Font family", exact: true })
    .selectOption("lexend");
  await page
    .getByRole("combobox", { name: "Font weight", exact: true })
    .selectOption("light");
  await page.locator(".font-showcase").screenshot({
    path: `output/playwright/${info.project.name}-font-picker.png`,
  });
  await page
    .getByRole("link", { name: "Open app full-screen", exact: true })
    .click();
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-font", "lexend");
  await expect(
    page.getByRole("heading", { name: "What should your ledger call you?" }),
  ).toHaveCSS("font-weight", "450");
});

test("font-loading failure stays usable and is explained", async ({ page }) => {
  await page.route("**/fonts/manrope_variable.ttf", (route) => route.abort());
  await page.goto("./");
  await page
    .getByRole("combobox", { name: "Font family", exact: true })
    .selectOption("manrope");
  await expect(page.getByText(/couldn.t load this font/i)).toBeVisible();
  await page
    .getByRole("button", { name: "Use Lexend instead", exact: true })
    .click();
  await expect(page.locator("html")).toHaveAttribute("data-font", "lexend");
});

test("denied preference writes keep the current font and explain the failure", async ({
  page,
}) => {
  await page.addInitScript(() => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function (key, value) {
      if (key === "ironlog-typography")
        throw new DOMException("Denied", "QuotaExceededError");
      return original.call(this, key, value);
    };
  });
  await page.goto("./");
  await page
    .getByRole("combobox", { name: "Font family", exact: true })
    .selectOption("inter");
  await expect(
    page.getByText(/Could not save the font preference/),
  ).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("data-font", "lexend");
});

test("Settings changes sync back to the website and survive workout navigation", async ({
  page,
}) => {
  await page.goto("./");
  await page
    .getByRole("link", { name: "Try App in web instead", exact: true })
    .click();
  const app = page.frameLocator(
    'iframe[title="IronLog interactive app preview"]',
  );
  await completeOnboarding(app, "Typography QA");
  await app.getByRole("link", { name: "Settings", exact: true }).click();
  await app.getByRole("button", { name: /^Appearance/ }).click();
  await app
    .getByRole("combobox", { name: "Font family", exact: true })
    .selectOption("manrope");
  await app
    .getByRole("combobox", { name: "Font weight", exact: true })
    .selectOption("light");
  await expect(
    page.getByRole("combobox", { name: "Font family", exact: true }),
  ).toHaveValue("manrope");
  await expect(page.locator("html")).toHaveAttribute(
    "data-font-preset",
    "light",
  );
  await app.getByRole("link", { name: "Home", exact: true }).click();
  await app
    .getByRole("button", { name: "Start freestyle", exact: true })
    .click();
  await app.getByRole("button", { name: "Add exercise", exact: true }).click();
  await expect(app.getByRole("dialog")).toHaveCSS(
    "font-family",
    /IronLog manrope/,
  );
  await expect(app.getByLabel("Exercise name")).toHaveCSS("font-weight", "350");
  await expect(app.getByRole("button", { name: /Close/ })).toHaveCSS(
    "font-weight",
    "450",
  );
  await app.getByLabel("Exercise name").fill("Barbell Bench Press");
  await app
    .getByRole("button", { name: /^Barbell Bench Press/ })
    .first()
    .click();
  await expect(app.getByRole("dialog")).toHaveCount(0);
  const card = app.locator(".exercise-card").first();
  await card.getByLabel("KG", { exact: true }).fill("65");
  await card.getByLabel("Reps", { exact: true }).fill("8");
  await card.getByRole("button", { name: "Log", exact: true }).click();
  await expect(card.locator(".sets .set-row")).toHaveCount(1);
  await app
    .getByRole("button", { name: "Finish workout", exact: true })
    .click();
  await app
    .getByRole("button", { name: "Save completed workout", exact: true })
    .click();
  await expect(
    app.getByRole("heading", { name: "History", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Open app full-screen", exact: true })
    .click();
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-font", "manrope");
  await expect(page.locator("html")).toHaveAttribute(
    "data-font-preset",
    "light",
  );
});

test("spacing slider changes layout, persists, resets and retains touch targets at large text", async ({
  page,
}, info) => {
  await page.setViewportSize({ width: 320, height: 800 });
  await page.goto("app/");
  await completeOnboarding(page, "Spacing QA");
  await page.getByRole("link", { name: "Settings", exact: true }).click();
  await page.getByRole("button", { name: /^Appearance/ }).click();
  const slider = page.getByRole("slider", { name: "UI spacing", exact: true });
  for (const value of [85, 125]) {
    await slider.fill(String(value));
    await expect(page.locator("html")).toHaveAttribute(
      "data-spacing",
      String(value),
    );
    const padding = await page
      .locator(".app-shell")
      .evaluate((el) => parseFloat(getComputedStyle(el).paddingLeft));
    expect(padding).toBeCloseTo((14 * value) / 100, 1);
    await expect(page.locator("body")).toHaveCSS("font-size", "16px");
    expect(
      (await page.getByRole("combobox", { name: "Font family", exact: true }).boundingBox())!.height,
    ).toBeGreaterThanOrEqual(48);
    expect(
      (await page
        .getByRole("button", { name: "Reset spacing", exact: true })
        .boundingBox())!.height,
    ).toBeGreaterThanOrEqual(48);
    const nav = page.getByRole("link", { name: "Home", exact: true });
    expect((await nav.boundingBox())!.height).toBeGreaterThanOrEqual(48);
    await expect(nav.locator("svg")).toHaveCSS("width", "21px");
    await page.setViewportSize({ width: 320, height: 800 });
    await page.addStyleTag({ content: "html { font-size: 200%; }" });
    const overflow = await page.evaluate(() => Array.from(document.querySelectorAll("*"))
      .map((element) => {
        const rect = element.getBoundingClientRect();
        return {
          tag: element.tagName.toLowerCase(),
          className: typeof element.className === "string" ? element.className : "",
          text: element.textContent?.trim().slice(0, 80) ?? "",
          left: Math.round(rect.left),
          right: Math.round(rect.right),
        };
      })
      .filter((item) => item.right > innerWidth + 1 || item.left < -1));
    expect(overflow).toEqual([]);
    expect((await slider.boundingBox())!.height).toBeGreaterThanOrEqual(48);
    await slider.screenshot({
      path: `output/playwright/${info.project.name}-spacing-${value}.png`,
    });
    await page.reload();
    await page.getByRole("button", { name: /^Appearance/ }).click();
    await expect(slider).toHaveValue(String(value));
  }
  await page
    .getByRole("button", { name: "Reset spacing", exact: true })
    .click();
  await expect(slider).toHaveValue("100");
});

test("font choice and unused bundled fonts work with the origin stopped", async ({
  page,
}) => {
  test.setTimeout(120_000);
  const origin = await startTestOrigin();
  try {
    await page.goto(origin.appUrl);
    await expect(
      page.getByRole("heading", { name: "Train with evidence. Progress like a game." }),
    ).toBeVisible();
    await page.evaluate(async () => {
      await navigator.serviceWorker.ready;
    });
    await page.reload();
    await expect
      .poll(() => page.evaluate(() => !!navigator.serviceWorker.controller))
      .toBe(true);
    await page.goto(origin.appUrl.replace(/app\/$/, ""));
    await origin.stop();
    await page
      .getByRole("combobox", { name: "Font family", exact: true })
      .selectOption("quicksand");
    await page
      .getByRole("combobox", { name: "Font weight", exact: true })
      .selectOption("bold");
    const face = await page.evaluate(async () =>
      (await document.fonts.load('700 16px "IronLog quicksand"')).some(
        (f) => f.status === "loaded",
      ),
    );
    expect(face).toBe(true);
    await page.reload();
    await expect(page.locator("html")).toHaveAttribute(
      "data-font",
      "quicksand",
    );
    const failed = await page.evaluate(() =>
      fetch("/Ironlog/not-cached-" + crypto.randomUUID()).then(
        () => false,
        () => true,
      ),
    );
    expect(failed).toBe(true);
  } finally {
    await origin.stop();
  }
});
