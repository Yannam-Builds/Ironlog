import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import {
  parseThemes,
  parseTemplates,
  nativeLogoSvg,
} from "../scripts/extract-native.mjs";
describe("native extraction contracts", () => {
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
