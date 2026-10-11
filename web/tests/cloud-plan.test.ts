import { expect, it } from "vitest";
import { validateGeneratedPlan } from "../src/domain/cloud-plan";
const valid = { type: "ironlog_plan", version: 1, plan: { name: "P", days: [{ name: "D", exercises: [{ exerciseName: "Unknown", sets: 3, reps: "8", restSeconds: 90 }] }] } };
it("accepts structurally valid plans but leaves unknown exercise review to the importer", () => expect(validateGeneratedPlan(JSON.stringify(valid), 1, [])).toContain("Unknown"));
it("rejects missing raw fields rather than accepting tolerant decoder defaults", () => {
  for (const raw of [{ name: "P", days: [{ exercises: [{ name: "Bench" }] }] }, { ...valid, version: 2 }, { ...valid, plan: { ...valid.plan, days: [{ exercises: valid.plan.days[0].exercises }] } }])
    expect(() => validateGeneratedPlan(JSON.stringify(raw), 1, [])).toThrow();
  expect(() => validateGeneratedPlan(JSON.stringify(valid), 3, [])).toThrow();
});
