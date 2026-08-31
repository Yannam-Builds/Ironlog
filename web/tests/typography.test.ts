import { beforeEach, describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { URL as NodeURL } from "node:url";
import {
  fonts,
  normalizeTypography,
  readTypography,
  saveTypography,
  applyTypography,
  typographyWeight,
  initializeTypography,
} from "../src/ui/typography";

beforeEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  localStorage.clear();
  document.documentElement.removeAttribute("style");
});
describe("shared typography", () => {
  it("has exactly Lexend plus twenty unique licensed, bundled variable families", () => {
    expect(fonts).toHaveLength(21);
    expect(new Set(fonts.map((f) => f.id)).size).toBe(21);
    for (const font of fonts) {
      const bytes = readFileSync(
        new NodeURL(`../public/${font.file}`, import.meta.url),
      );
      expect(createHash("sha256").update(bytes).digest("hex")).toBe(
        font.sha256,
      );
      expect(
        readFileSync(
          new NodeURL(`../public/${font.license}`, import.meta.url),
          "utf8",
        ),
      ).toContain("SIL OPEN FONT LICENSE");
      if ("licenseSha256" in font) {
        expect(createHash("sha256").update(readFileSync(new NodeURL(`../public/${font.license}`, import.meta.url))).digest("hex")).toBe(font.licenseSha256);
      }
      expect(font.minWeight).toBeLessThanOrEqual(400);
      expect(font.maxWeight).toBeGreaterThanOrEqual(700);
    }
  });
  it("normalizes untrusted and legacy preferences without breaking startup", () => {
    expect(normalizeTypography(null)).toEqual({
      family: "lexend",
      preset: "original",
    });
    expect(
      normalizeTypography({ family: "__proto__", preset: "constructor" }),
    ).toEqual({ family: "lexend", preset: "original" });
    localStorage.setItem("ironlog-typography", "{broken");
    expect(readTypography()).toEqual({ family: "lexend", preset: "original" });
  });
  it("persists the font and preset together, applies all weight tokens, and signals updates", () => {
    const update = vi.fn();
    window.addEventListener("ironlog-typography", update);
    saveTypography({ family: "manrope", preset: "light" });
    expect(readTypography()).toEqual({ family: "manrope", preset: "light" });
    expect(document.documentElement.dataset.font).toBe("manrope");
    expect(document.documentElement.style.getPropertyValue("--type-w800")).toBe(
      "450",
    );
    expect(document.documentElement.style.getPropertyValue("--type-w400")).toBe(
      "350",
    );
    expect(update).toHaveBeenCalledOnce();
    window.removeEventListener("ironlog-typography", update);
  });
  it("clamps weights to the real font axis and preserves original styles by default", () => {
    expect(
      typographyWeight(900, { family: "quicksand", preset: "original" }),
    ).toBe(700);
    expect(
      typographyWeight(350, { family: "lexend", preset: "original" }),
    ).toBe(350);
    expect(typographyWeight(800, { family: "lexend", preset: "regular" })).toBe(
      600,
    );
    expect(typographyWeight(800, { family: "manrope", preset: "bold" })).toBe(
      750,
    );
    expect(typographyWeight(700, { family: "quicksand", preset: "bold" })).toBe(
      700,
    );
  });
  it("does not claim a save or change typography when persistence fails", () => {
    applyTypography({ family: "lexend", preset: "original" });
    vi.stubGlobal("localStorage", {
      getItem: () => null,
      setItem: () => {
        throw Error("denied");
      },
    });
    expect(() => saveTypography({ family: "inter", preset: "light" })).toThrow(
      /save/i,
    );
    expect(document.documentElement.dataset.font).toBe("lexend");
  });
  it("responds to another tab and clearing storage without writing a feedback loop", () => {
    const dispose = initializeTypography();
    localStorage.setItem(
      "ironlog-typography",
      JSON.stringify({ family: "outfit", preset: "bold" }),
    );
    const write = vi.spyOn(Storage.prototype, "setItem");
    window.dispatchEvent(
      new StorageEvent("storage", { key: "ironlog-typography" }),
    );
    expect(document.documentElement.dataset.font).toBe("outfit");
    expect(write).not.toHaveBeenCalled();
    localStorage.clear();
    window.dispatchEvent(new StorageEvent("storage", { key: null }));
    expect(document.documentElement.dataset.font).toBe("lexend");
    dispose();
  });
});
