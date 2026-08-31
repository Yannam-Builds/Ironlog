import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: ".",
  testMatch: "app-server-stopped.spec.ts",
  outputDir: "../output/playwright/origin-stopped",
  workers: 1,
  reporter: "list",
  expect: { timeout: 10_000 },
  use: { trace: "retain-on-failure", screenshot: "only-on-failure" },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["iPhone 13"],
        defaultBrowserType: "chromium",
        browserName: "chromium",
      },
    },
    { name: "webkit", use: { ...devices["iPhone 13"], browserName: "webkit" } },
  ],
});
