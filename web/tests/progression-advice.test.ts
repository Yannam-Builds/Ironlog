import { expect, it } from "vitest";
import { recommendProgression } from "../src/domain/progression-advice";
import { resolveProgressionPolicy } from "../src/domain/program-intelligence";
import { deloadDay, recommendedPlanDay, todayDirective } from "../src/domain/plan-coaching";
import type { SessionExercise, Plan, Workout } from "../src/domain/types";
import { buildTrainingIntelligence } from "../src/domain/program-intelligence";
const exercise = (): SessionExercise => ({ id: "slot", exerciseId: "bench", name: "Bench", sets: 3, reps: "8-12", tracking: "weight_reps", muscle: "Chest", equipment: "Barbell", restSeconds: 90, notes: "", isWarmup: false, supersetGroup: "A", pendingWarmups: [], loggedSets: Array.from({length: 3}, (_,i) => ({ id: String(i), weightKg: 50, reps: 12, rpe: 8, durationSeconds: 0, distanceKm: 0, kind: "normal", notes: "", loggedAt: 1 })) });
it("requires all targets, valid uniform normal sets and effort before progressing", () => {
  const e = exercise();
  expect(recommendProgression(e)).toMatchObject({ action: "add_load", weightKg: 52.5, reps: 12 });
  e.loggedSets[1].reps = 11; expect(recommendProgression(e)?.action).toBe("hold");
  e.loggedSets[1].reps = 12; delete e.loggedSets[1].rpe;
  expect(recommendProgression(e)).toMatchObject({ action: "hold", missingEvidence: ["effort"] });
  e.loggedSets[1].rpe = 8; e.loggedSets[2].weightKg = 55; expect(recommendProgression(e)?.action).toBe("hold");
  e.loggedSets[2].weightKg = 50; e.loggedSets[1].kind = "drop"; expect(recommendProgression(e)?.action).toBe("hold");
});
it("uses the stricter recorded effort and the resolved policy", () => {
  const e = exercise(); e.loggedSets[1].rir = 1;
  expect(recommendProgression(e)?.action).toBe("hold");
  expect(recommendProgression(e,e,{ policy: resolveProgressionPolicy(undefined, undefined, "aggressive") })?.action).toBe("add_load");
});
it("checks exact plate inventory and falls back to one rep when unachievable", () => {
  const e = exercise();
  expect(recommendProgression(e,e,{ setup: { barKg:20, platesKg:[15,1.25], plateInventory:[{weightKg:15,quantity:2},{weightKg:1.25,quantity:1}] } })).toMatchObject({action:"add_reps",weightKg:50,reps:13});
  expect(recommendProgression(e,e,{ setup: { barKg:20, platesKg:[15,1.25], plateInventory:[{weightKg:15,quantity:2},{weightKg:1.25,quantity:2}] } })).toMatchObject({action:"add_load",weightKg:52.5});
});
it("holds the load step when baseline is unachievable or smallest stock is unpaired", () => {
  const e=exercise();
  expect(recommendProgression(e,e,{setup:{barKg:20,platesKg:[15,1.25,.5],plateInventory:[{weightKg:15,quantity:2},{weightKg:1.25,quantity:2},{weightKg:.5,quantity:1}]}})?.action).toBe("add_reps");
  e.loggedSets.forEach(s=>{s.weightKg=49;});
  expect(recommendProgression(e,e,{setup:{barKg:20,platesKg:[15,1.25]}})?.action).toBe("add_reps");
});
it("holds percent 1RM targets and does not recommend unsupported tracking", () => {
  const e = exercise(); e.loggedSets.forEach(s => { s.reps=5; }); e.reps="5";
  const policy = resolveProgressionPolicy("percent_1rm"); expect(recommendProgression(e,e,{policy})?.action).toBe("hold");
  e.tracking="duration"; expect(recommendProgression(e)).toBeNull();
});
it("adds one bodyweight rep with sufficient evidence and never mutates source", () => {
  const e = exercise(); e.tracking="bodyweight_reps"; e.loggedSets.forEach(s => { s.weightKg=0; });
  const before = structuredClone(e); expect(recommendProgression(e)).toMatchObject({action:"add_reps",weightKg:0,reps:13}); expect(e).toEqual(before);
});
it("copies the native deload with stable originals, fresh IDs and minimum two sets", () => {
  let id=0; const day={id:"a",name:"A",color:"",exercises:[exercise(),{...exercise(),id:"b",sets:7}]};
  const result=deloadDay(day,() => String(++id)); expect(result.exercises.map(e => e.sets)).toEqual([2,3]);
  expect(result.exercises[0]).toMatchObject({notes:"Deload: use 60% of working weight",reps:"8-12"});
  expect(day.exercises[0].notes).toBe(""); expect(result.id).not.toBe(day.id);
});
it("cycles the native next day ignoring active/discarded/future sessions", () => {
  const plan={id:"p",days:[{id:"a"},{id:"b"}]} as Plan;
  const history=[{status:"completed",dayId:"a",startedAt:1},{status:"active",dayId:"b",startedAt:2},{status:"completed",dayId:"b",startedAt:100}] as Workout[];
  expect(recommendedPlanDay(plan,history,3)?.id).toBe("b");
  expect(recommendedPlanDay(plan,[],3)?.id).toBe("a");
});
it("prioritizes neural fatigue ahead of empty-week and slowing-PR guidance", () => {
  const snapshot=buildTrainingIntelligence([],{goalMode:"STRENGTH",weeklyGoalDays:3});
  expect(todayDirective(snapshot).title).toBe("Follow your next planned session");
  snapshot.neuralFatigue={isFlagged:true,consecutiveDays:3,lastHeavyExercises:[]};
  expect(todayDirective(snapshot).title).toBe("Rest or light cardio today");
});
