import { expect, it } from "vitest";
import { manualPlanPrompt } from "../src/domain/manual-prompt";
import { defaultProfile } from "../src/domain/types";
it("does not assume every onboarded athlete is an adult and keeps the schema valid", () => {
  const prompt = manualPlanPrompt(
    { ...defaultProfile, age: 16, goal: 'Strength "practice"' },
    "Dumbbells",
    "No barbell",
  );
  expect(prompt).toContain("person aged 16");
  expect(prompt).not.toContain("for an adult");
  expect(prompt).toContain("Dumbbells");
  expect(prompt).toContain("No barbell");
  const json = prompt.split("\n").find((x) => x.startsWith("{"))!;
  expect(JSON.parse(json).plan.goal).toBe('Strength "practice"');
});
