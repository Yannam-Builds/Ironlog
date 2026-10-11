import { expect, it } from "vitest";
import { analyticsWindows, granularVolume } from "../src/domain/analytics-windows";
import type { Plan, Workout } from "../src/domain/types";
const workout = (id: string, date: Date): Workout => ({ id, name: id, startedAt: date.getTime(), completedAt: date.getTime(), status: "completed", exercises: [], notes: "", restUsed: false, revision: 0 });
it("separates the local ISO week from rolling seven days and excludes future sessions", () => {
  const now = new Date(2026,0,7,12).getTime();
  const rows = [workout("sunday", new Date(2026,0,4,10)), workout("monday", new Date(2026,0,5,10)), workout("future", new Date(2026,0,8,10))];
  expect(analyticsWindows(rows,"WEEK",undefined,now).current.map(w => w.id)).toEqual(["monday"]);
  expect(analyticsWindows(rows,"7D",undefined,now).current.map(w => w.id)).toEqual(["sunday","monday"]);
});
it("uses the active program length with the native 28-day minimum and 42-day fallback", () => {
  const now = new Date(2026,2,31).getTime();
  const plan = { days: Array(4).fill({}) } as Plan;
  expect(new Date(analyticsWindows([],"PROGRAM",plan,now).cutoff).getDate()).toBe(3);
  expect(new Date(analyticsWindows([],"PROGRAM",undefined,now).cutoff).getMonth()).toBe(1);
});
it("weights granular muscles and excludes warmups instead of counting full sets for every muscle", () => {
  const w = workout("w", new Date(2026,0,5));
  w.exercises = [{ id:"e", exerciseId:"e", name:"Custom", tracking:"weight_reps", muscle:"Chest", equipment:"Barbell", muscleContributions:{upperChest:.6,tricepsLong:.4}, sets:3,reps:"8",restSeconds:90,notes:"",supersetGroup:"",isWarmup:false,pendingWarmups:[],loggedSets:[{id:"s",weightKg:20,reps:8,durationSeconds:0,distanceKm:0,kind:"normal",notes:"",loggedAt:0},{id:"warm",weightKg:20,reps:8,durationSeconds:0,distanceKm:0,kind:"warmup",notes:"",loggedAt:0}] }];
  expect(granularVolume([w]).radar).toMatchObject({Chest:.6,Arms:.4});
});
