import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { readFile } from "node:fs/promises";
import { completeOnboarding } from "./onboarding";
async function onboard(page: Page) {
  await page.goto("app/");
  await completeOnboarding(page, "Backup QA");
}
test("browser backup restores photo bytes and profile after preview", async ({
  page,
}, info) => {
  await onboard(page);
  await page.goto("app/#/photos");
  const photo = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jD1sAAAAASUVORK5CYII=",
    "base64",
  );
  await expect(page.getByLabel("From device")).toBeEnabled();
  await page
    .getByLabel("From device")
    .setInputFiles({
      name: "synthetic.png",
      mimeType: "image/png",
      buffer: photo,
    });
  await expect(page.locator(".photo-entry img")).toHaveCount(1);
  await page.goto("app/#/home");
  await page.goto("app/#/settings");
  await page.getByRole("button", { name: /^Data & Privacy/ }).click();
  const downloaded = page.waitForEvent("download");
  await page
    .getByRole("button", {
      name: "Download complete browser backup",
      exact: true,
    })
    .click();
  const download = await downloaded;
  const bytes = await readFile((await download.path())!);
  await expect(
    page.getByText("Backup download started. Keep the file somewhere safe."),
  ).toBeVisible();
  await page.goto("app/#/home");
  await page.goto("app/#/settings");
  await page.getByRole("button", { name: /^Training/ }).click();
  await page.getByLabel("Name", { exact: true }).fill("Changed after backup");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByText("Profile saved", { exact: true })).toBeVisible();
  await page.goto("app/#/home");
  await page.goto("app/#/settings");
  await page.getByRole("button", { name: /^Data & Privacy/ }).click();
  await page
    .getByLabel("Restore a browser ZIP or Android JSON")
    .setInputFiles({
      name: "backup.zip",
      mimeType: "application/zip",
      buffer: bytes,
    });
  await expect(page.getByRole("dialog")).toContainText("1 photos");
  await page.screenshot({
    path: `output/playwright/${info.project.name}-restore-preview.png`,
  });
  await page
    .getByRole("button", { name: "Replace with this backup", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.reload();
  await page.getByRole("button", { name: /^Training/ }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue(
    "Backup QA",
  );
  await page.goto("app/#/photos");
  await expect(page.locator(".photo-entry img")).toHaveCount(1);
  await expect
    .poll(() =>
      page
        .locator(".photo-entry img")
        .evaluate(
          (img: HTMLImageElement) => img.complete && img.naturalWidth > 0,
        ),
    )
    .toBe(true);
  await page.goto("app/#/settings");
  await page.getByRole("button", { name: /^Data & Privacy/ }).click();
  await page
    .getByLabel("Restore a browser ZIP or Android JSON")
    .setInputFiles({
      name: "bad.json",
      mimeType: "application/json",
      buffer: Buffer.from('{"tables":{}}'),
    });
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.reload();
  await page.getByRole("button", { name: /^Training/ }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue(
    "Backup QA",
  );
});
test("all native themes retain readable landing controls and narrow layout", async ({
  page,
}, info) => {
  test.setTimeout(120000);
  await page.goto("./");
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
        name: theme === "Monet" ? /^Monet/ : theme,
        exact: true,
      })
      .click();
    const audit = await new AxeBuilder({ page })
      .withRules(["color-contrast", "button-name", "link-name"])
      .analyze();
    expect(
      audit.violations.map((v) => ({
        id: v.id,
        nodes: v.nodes.map((n) => n.target),
      })),
      theme,
    ).toEqual([]);
  }
  for (const [width, height, scale] of [
    [320, 568, 1],
    [360, 800, 1.3],
    [411, 891, 2],
    [844, 390, 1],
  ]) {
    await page.setViewportSize({ width, height });
    await page.evaluate(
      (s) => (document.documentElement.style.fontSize = `${16 * s}px`),
      scale,
    );
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
      `${width}px scale${scale}`,
    ).toBe(true);
    await page.screenshot({
      path: `output/playwright/${info.project.name}-landing-${width}-${scale}.png`,
    });
  }
});
