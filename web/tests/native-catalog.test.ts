import { readFileSync } from "node:fs";
import { expect, it } from "vitest";
import type { Exercise } from "../src/domain/types";
import { nativeExerciseCatalog } from "../scripts/native-exercise-catalog.mjs";
import { bootstrap, resetData, savePlan, startWorkout, readSnapshot } from "../src/data/store";
import { encodeAndroidBackup, decodeAndroidBackup } from "../src/domain/codecs";
import { defaultProfile } from "../src/domain/types";
const catalog: Exercise[] = JSON.parse(readFileSync("public/data/exercises.json", "utf8"));
it("transfers search taxonomy and aliases through canonical native backup fields",()=>{
 const exercise={...catalog[0],aliases:["Custom alias"],movementPattern:"rotation",difficulty:"beginner"};
 const payload=JSON.parse(encodeAndroidBackup({profile:defaultProfile,exercises:[exercise],plans:[],workouts:[],measurements:[],photos:[],checkins:[],gyms:[]}));
 delete payload.webExtension;
 const restored=decodeAndroidBackup(JSON.stringify(payload)).snapshot.exercises[0];
 expect(restored).toMatchObject({aliases:exercise.aliases,movementPattern:exercise.movementPattern,difficulty:exercise.difficulty});
});
it("exports Kotlin's normalized rep tracking and taxonomy for machine crunches", () => {
  expect(catalog.find(e => e.id === "ab_crunch_machine")).toMatchObject({
    tracking: "weight_reps", category: "strength", isBodyweight: false,
    requiresExternalLoad: true, primaryMuscles: ["Abdominals"],
    movementPattern: "rotation", difficulty: "beginner", muscleContributions: {Abdominals: 1},
  });
});
it("uses actual Kotlin regex precedence for reps, duration, distance and explicit weighted duration",()=>{
 const source=readFileSync("../app/src/main/java/com/ironlog/app/util/ExerciseTrackingTypeNormalizer.kt","utf8");
 const rows=nativeExerciseCatalog([
  {id:"press",name:"Bench Press",category:"cardio",trackingType:"duration_distance"},
  {id:"plank",name:"Plank Hold",trackingType:"weight_reps"},
  {id:"run",name:"Treadmill Running",trackingType:"weight_reps"},
  {id:"weighted",name:"Loaded Stabilizer",trackingType:"duration_weight",primaryMuscles:["Chest"],secondaryMuscles:["Triceps"]},
 ],source);
 expect(rows.map((row:Exercise)=>row.tracking)).toEqual(["weight_reps","duration","duration_distance","duration_weight"]);
 expect(rows[3].muscleContributions).toEqual({Chest:0.7,Triceps:0.3});
});
it("updates the bundled library without reinterpreting a previously started session",async()=>{
 await resetData();
 const corrected=catalog.find(e=>e.id==="ab_crunch_machine")!;
 await bootstrap([{...corrected,tracking:"duration_distance"}]);
 await savePlan({id:"catalog-plan",name:"Original",order:0,description:"",goal:"",days:[{id:"day",name:"Day",color:"",exercises:[{id:"slot",exerciseId:corrected.id,name:corrected.name,sets:3,reps:"60",notes:"Keep",restSeconds:0,supersetGroup:"",isWarmup:false}]}]});
 const original=await startWorkout("catalog-plan","day");
 await bootstrap(catalog);
 const state=await readSnapshot();
 expect(state.workouts[0]).toEqual(original);
 expect(state.exercises.find(e=>e.id===corrected.id)?.tracking).toBe("weight_reps");
});
it("retains native bodyweight and external-load flags rather than flattening them into a guessed raw mode", () => {
  const raw = JSON.parse(readFileSync("../app/src/main/assets/exerciseLibrary.json", "utf8"));
  for (const entry of raw.exercises) {
    const exported = catalog.find(e => e.id === entry.id)!;
    expect(exported.isBodyweight, entry.name).toBe(entry.isBodyweight === true);
    expect(exported.requiresExternalLoad, entry.name).toBe(entry.requiresExternalLoad === true);
    expect(exported.movementPattern, entry.name).toBe(entry.movementPattern ?? undefined);
    expect(exported.difficulty, entry.name).toBe(entry.difficulty ?? undefined);
  }
});
