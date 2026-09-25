import { expect, type FrameLocator, type Page } from "@playwright/test";

type AppSurface = Page | FrameLocator;

export async function openOnboardingProfile(app: AppSurface) {
  await expect(
    app.getByRole("heading", {
      name: "Train with evidence. Progress like a game.",
    }),
  ).toBeVisible();
  await app
    .getByRole("button", { name: "Build my training system", exact: true })
    .click();
  await expect(
    app.getByRole("heading", { name: "What should your ledger call you?" }),
  ).toBeVisible();
}

export async function completeOnboarding(app: AppSurface, name: string) {
  await openOnboardingProfile(app);
  await app.getByLabel("Your name").fill(name);
  await app
    .getByRole("button", { name: `Continue as ${name}`, exact: true })
    .click();
  await expect(
    app.getByRole("heading", { name: "Tell us where training begins." }),
  ).toBeVisible();
  await app
    .getByRole("button", { name: "Use this baseline", exact: true })
    .click();
  await app
    .getByRole("button", { name: "Use this progression", exact: true })
    .click();
  await app
    .getByRole("button", { name: "Save weekly rhythm", exact: true })
    .click();
  await app.getByRole("button", { name: "Use this goal", exact: true }).click();
  await app
    .getByRole("button", { name: "Continue with local coaching", exact: true })
    .click();
  await app
    .getByRole("button", { name: "Continue without integrations", exact: true })
    .click();
  await app
    .getByRole("button", { name: "Save my baseline", exact: true })
    .click();
  await expect(
    app.getByRole("heading", { name: /Start with structure/i }),
  ).toBeVisible();
  await app
    .getByRole("button", { name: "Start training", exact: true })
    .click();
  await expect(app.getByRole("heading", { name, exact: true })).toBeVisible();
}
