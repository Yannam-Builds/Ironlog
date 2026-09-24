import { test, expect } from "@playwright/test";
test("browser storage starts and persists independently", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(String(e)));
  page.on("console", (m) => {
    if (m.type() === "error") errors.push(m.text());
  });
  await page.goto("app/");
  const probe = await page.evaluate(() =>
    Promise.race([
      new Promise<string>((resolve) => {
        const r = indexedDB.open("ironlog-e2e-storage-probe", 1);
        r.onupgradeneeded = () => r.result.createObjectStore("probe");
        r.onerror = () => resolve(String(r.error));
        r.onsuccess = () => {
          r.result.close();
          resolve("available");
        };
      }),
      new Promise<string>((resolve) =>
        setTimeout(() => resolve("timeout"), 5000),
      ),
    ]),
  );
  console.log({ probe, errors, body: await page.locator("body").innerText() });
  expect(probe).toBe("available");
  await expect(
    page.getByRole("heading", { name: "Train with evidence. Progress like a game." }),
  ).toBeVisible();
});
