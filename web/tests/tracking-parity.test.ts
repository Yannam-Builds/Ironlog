import { expect, it } from 'vitest';
import { decodeAndroidBackup, encodeAndroidBackup } from '../src/domain/codecs';
import { deriveSnapshot } from '../src/domain/engine';
import { defaultProfile, type AppSnapshot } from '../src/domain/types';
const native = (type: string, overrides: Record<string,unknown> = {}) => JSON.stringify({type:'ironlog_watermelon_export',version:1,data:{exercises:[{id:'e',name:'Edited library',primary_muscle:'legs',tracking_type:type}],plans:[],plan_days:[],plan_exercises:[],workouts:[{id:'w',name:'Training',started_at:1000,status:'completed',duration_seconds:1800}],workout_exercises:[{id:'slot',workout_id:'w',exercise_id:'e',exercise_snapshot_json:JSON.stringify({version:1,name:'Original hold',primaryMuscle:'chest',primaryMuscles:['chest'],secondaryMuscles:['triceps'],muscleContributions:{pec_major:1},trackingType:type,equipment:'cable',category:'strength',isBodyweight:false,requiresExternalLoad:true,...overrides})}],workout_sets:Array.from({length:8},(_,i)=>({id:String(i),workout_exercise_id:'slot',weight:20,reps:60}))}});
it('preserves all native tracking dimensions and immutable metadata without web extension',()=>{
 for (const type of ['bodyweight_plus_weight_reps','assisted_bodyweight','duration_weight','duration_distance','cardio']) {
 const a=decodeAndroidBackup(native(type)).snapshot;
 expect(a.workouts[0].exercises[0]).toMatchObject({name:'Original hold',muscle:'chest',tracking:type,primaryMuscles:['chest'],secondaryMuscles:['triceps'],muscleContributions:{pec_major:1},requiresExternalLoad:true});
 const raw=JSON.parse(encodeAndroidBackup(a));delete raw.webExtension;
 const b=decodeAndroidBackup(JSON.stringify(raw)).snapshot;
 expect(b.workouts[0].exercises[0]).toMatchObject({name:'Original hold',tracking:type,muscle:'chest'});
 expect(b.workouts[0].exercises[0].loggedSets[0]).toMatchObject(a.workouts[0].exercises[0].loggedSets[0]);
 }
});
it('preserves an unsupported native tracking value and excludes it from proof',()=>{
 const s=decodeAndroidBackup(native('future_metric')).snapshot;
 expect(s.workouts[0].exercises[0].tracking).toBe('future_metric');
 expect(deriveSnapshot(s,100000).creditedCount).toBe(0);
});
it('weighted seconds never enter strength estimates or rep-volume',()=>{
 const s=decodeAndroidBackup(native('duration_weight')).snapshot;
 expect(s.workouts[0].exercises[0].loggedSets[0]).toMatchObject({weightKg:20,reps:0,durationSeconds:60});
 const d=deriveSnapshot(s,100000);expect(d.volumeKg).toBe(0);expect(d.prs).toEqual([]);expect(d.trainingSignals.strength).toBe(0);
});
it('added load contributes volume but assisted loads never become load PRs',()=>{
 for(const type of ['bodyweight_plus_weight_reps','assisted_bodyweight']) {
 const s=decodeAndroidBackup(native(type,{isBodyweight:true})).snapshot;
 s.workouts[0].exercises[0].loggedSets.forEach(x=>x.reps=8);
 const d=deriveSnapshot(s,100000);expect(d.prs).toEqual([]);expect(d.volumeKg).toBe(type==='assisted_bodyweight'?0:1280);
 }
});
import { trackingMode, estimatedOneRm, isTimed } from '../src/domain/tracking';
it('uses native legacy inference and assisted aliases without fabricated load PRs',()=>{
 const s=decodeAndroidBackup(native('weight_reps',{name:'Pull-up',isBodyweight:false,requiresExternalLoad:false})).snapshot;
 const e=s.workouts[0].exercises[0];expect(trackingMode(e)).toBe('bodyweight_plus_weight_reps');expect(estimatedOneRm(e,e.loggedSets[0])).toBeUndefined();
 expect(trackingMode({...e,name:'Assisted pull-up',tracking:'bodyweight_reps'})).toBe('assisted_bodyweight');
 expect(isTimed({...e,name:'Plank',tracking:'weight_reps'})).toBe(true);
 expect(isTimed({...e,name:'Running',tracking:'weight_reps'})).toBe(true);
});
it('canonical native snapshot beats stale web extension metadata',()=>{
 const a=decodeAndroidBackup(native('duration_weight')).snapshot;
 const raw=JSON.parse(encodeAndroidBackup(a));
 const stale=raw.webExtension.workouts[0];
 stale.name='Stale workout';stale.startedAt=999999;stale.status='active';
 stale.exercises[0].name='Stale';stale.exercises[0].exerciseId='wrong-library-exercise';
 stale.exercises[0].notes='stale notes';stale.exercises[0].supersetGroup='stale group';
 Object.assign(stale.exercises[0].loggedSets[0],{weightKg:999,reps:999,durationSeconds:999,
  distanceKm:999,kind:'warmup',toFailure:false,loggedAt:999999});
 const restored=decodeAndroidBackup(JSON.stringify(raw)).snapshot.workouts[0];
 expect(restored).toMatchObject({name:a.workouts[0].name,startedAt:a.workouts[0].startedAt,status:'completed'});
 expect(restored.exercises[0].name).toBe('Original hold');
 expect(restored.exercises[0].exerciseId).toBe(a.workouts[0].exercises[0].exerciseId);
 expect(restored.exercises[0].notes).toBe(a.workouts[0].exercises[0].notes);
 expect(restored.exercises[0].supersetGroup).toBe(a.workouts[0].exercises[0].supersetGroup);
 expect(restored.exercises[0].loggedSets[0]).toEqual(a.workouts[0].exercises[0].loggedSets[0]);
});
it('rejects moving a canonical set to another extension exercise',()=>{
 const a=decodeAndroidBackup(native('weight_reps')).snapshot;
 const raw=JSON.parse(encodeAndroidBackup(a));
 const original=raw.webExtension.workouts[0].exercises[0];
 raw.webExtension.workouts[0].exercises.push({...structuredClone(original),id:'other'});
 original.loggedSets=[];
 expect(()=>decodeAndroidBackup(JSON.stringify(raw))).toThrow(/missing canonical workout sets/i);
});
