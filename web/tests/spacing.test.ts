import { beforeEach, expect, it, vi } from "vitest";
import {
  normalizeSpacing,
  readSpacing,
  saveSpacing,
  initializeSpacing,
} from "../src/ui/spacing";
beforeEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});
it("bounds and snaps spacing while rejecting malformed legacy values", () => {
  expect(normalizeSpacing(null)).toBe(100);
  expect(normalizeSpacing("125")).toBe(100);
  expect(normalizeSpacing(Infinity)).toBe(100);
  expect(normalizeSpacing(5)).toBe(85);
  expect(normalizeSpacing(999)).toBe(125);
  expect(normalizeSpacing(108)).toBe(110);
});
it("persists layout only, applies immediately and reloads the chosen scale", () => {
  initializeSpacing()();
  saveSpacing(120);
  expect(readSpacing()).toBe(120);
  expect(document.documentElement.style.getPropertyValue("--ui-spacing")).toBe(
    "1.2",
  );
  expect(document.documentElement.style.fontSize).toBe("");
  expect(document.documentElement.style.zoom).toBe("");
  const stop = initializeSpacing();
  expect(document.documentElement.dataset.spacing).toBe("120");
  localStorage.setItem("ironlog-spacing", "85");
  window.dispatchEvent(new StorageEvent("storage", { key: "ironlog-spacing" }));
  expect(document.documentElement.dataset.spacing).toBe("85");
  stop();
});
it("does not apply a preference when persistence fails", () => {
  saveSpacing(100);
  vi.stubGlobal("localStorage", {
    getItem: () => "100",
    setItem: () => {
      throw Error("full");
    },
  });
  expect(() => saveSpacing(125)).toThrow(/Could not save/);
  expect(document.documentElement.dataset.spacing).toBe("100");
});
