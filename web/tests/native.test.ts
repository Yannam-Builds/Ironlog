import { readFileSync } from "node:fs";
import { readdirSync } from "node:fs";
import { resolve } from "node:path";
import { createHash } from "node:crypto";
import { describe, expect, it } from "vitest";
import {
  parseThemes,
  parseTemplates,
  nativeLogoSvg,
} from "../scripts/extract-native.mjs";
describe("native extraction contracts", () => {
  const hash = (path: string) =>
    createHash("sha256").update(readFileSync(path)).digest("hex");
  it("exports every density-independent native artwork file and font", () => {
    const nativeArt = readdirSync(resolve("../app/src/main/res/drawable-nodpi"))
      .filter((name) => name.endsWith(".png"))
      .sort();
    const expectedArt = [...nativeArt, "logo_iron.png", "logo_log.png"].sort();
    const webArt = [
      ...readdirSync(resolve("public/assets")).filter((name) =>
        name.endsWith(".png") && !name.startsWith("icon-"),
      ),
      ...readdirSync(resolve("public/assets/badges")),
    ].sort();
    expect(webArt).toEqual(expectedArt);
    for (const name of nativeArt) {
      const webPath = name.startsWith("ic_badge_")
        ? resolve("public/assets/badges", name)
        : resolve("public/assets", name);
      expect(hash(webPath)).toBe(
        hash(resolve("../app/src/main/res/drawable-nodpi", name)),
      );
    }
    for (const name of ["logo_iron.png", "logo_log.png"]) {
      expect(hash(resolve("public/assets", name))).toBe(
        hash(resolve("../app/src/main/res/drawable", name)),
      );
    }
    const nativeFonts = readdirSync(resolve("../app/src/main/res/font"))
      .filter((name) => name.endsWith(".ttf"))
      .sort();
    const webFonts = [
      "lexend_variable.ttf",
      ...readdirSync(resolve("public/fonts")),
    ].sort();
    expect(webFonts).toEqual(nativeFonts);
    for (const name of nativeFonts) {
      const webPath = name === "lexend_variable.ttf"
        ? resolve("public/assets", name)
        : resolve("public/fonts", name);
      expect(hash(webPath)).toBe(hash(resolve("../app/src/main/res/font", name)));
    }
  });
  it("uses the current monochrome vector, not the retired orange launcher raster", () => {
    const xml =
      '<path android:fillColor="#FFFFFF" android:pathData="M10 10L20 20Z" />';
    const svg = nativeLogoSvg(xml);
    expect(svg).toContain('d="M10 10L20 20Z"');
    expect(svg).toContain('fill="#FFFFFF"');
    expect(svg).not.toContain("<rect");
    expect(svg).not.toContain('fill="#000000"');
    expect(svg).not.toContain("#FF4500");
  });
  it("converts ARGB without turning opaque menu colors transparent", () => {
    const themes = parseThemes(
      'val Dark = IronLogThemeTokens(name = "DARK", bg = Color(0xFF121212), accentSoft = Color(0x22FF4500), text = Color.White)',
    );
    expect(themes.dark.bg).toBe("#121212");
    expect(themes.dark.accentSoft).toBe("#FF450022");
    expect(themes.dark.text).toBe("#FFFFFF");
  });
  it("preserves notes, warmups and nested exercises from native templates", () => {
    const plans = parseTemplates(
      'ProgramTemplate(id = "a", name = "Plan", category = "BEGINNER", description = "One", days = listOf(FullPlanDay("A", "#FF4500", listOf(PlanExerciseInput(name = "Bench", sets = 3, reps = "8–10", restSeconds = 90, notes = "Slow (controlled)", isWarmup = true)))))',
    );
    expect(plans).toHaveLength(1);
    expect(plans[0].days[0].exercises[0]).toMatchObject({
      name: "Bench",
      notes: "Slow (controlled)",
      sets: 3,
      isWarmup: true,
    });
  });
});
