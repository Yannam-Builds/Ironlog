import { expect, it } from "vitest";
import { activeGymEquipment, canImportAiPlans, unresolvedPlanRows } from "../src/domain/ai-plan-review";
import { decodePlans } from "../src/domain/codecs";
import { defaultProfile, type Exercise } from "../src/domain/types";
const bench: Exercise = { id: "bench", name: "Bench Press", muscle: "Chest", equipment: "Barbell", tracking: "weight_reps" };
it("blocks arbitrary supplied IDs and permits an explicitly reviewed library match", () => {
  const { plans } = decodePlans(JSON.stringify({ name: "Plan", days: [{ name: "Day", exercises: [{ exerciseId: "fake", name: "Mystery" }] }] }), [bench]);
  expect(unresolvedPlanRows(plans, [bench])).toHaveLength(1);
  expect(canImportAiPlans(plans, [bench])).toBe(false);
  plans[0].days[0].exercises[0].exerciseId = bench.id;
  expect(canImportAiPlans(plans, [bench])).toBe(true);
  plans[0].days[0].exercises = [];
  expect(canImportAiPlans(plans, [bench])).toBe(false);
});
it("starts from the active gym equipment restrictions", () => {
  expect(activeGymEquipment({ ...defaultProfile, activeGymId: "home" }, [{ id: "home", name: "Home", barKg: 20, platesKg: [], unavailableEquipment: ["BARBELL", "Cable"] }])).not.toContain("Barbell");
});
