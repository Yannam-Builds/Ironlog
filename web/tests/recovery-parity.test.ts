import { expect, it } from 'vitest';
import { readinessByRegion, deriveSnapshot } from '../src/domain/engine';
import { defaultProfile, type SessionExercise, type Workout } from '../src/domain/types';
const now = 1788610000000;
const ex = (name: string, muscle: string, count = 3): SessionExercise => ({ id:name,exerciseId:name,name,muscle,equipment:'',tracking:'weight_reps',sets:count,reps:'8',restSeconds:90,notes:'',supersetGroup:'',isWarmup:false,pendingWarmups:[],loggedSets:Array.from({length:count},(_,i)=>({id:String(i),weightKg:20,reps:8,durationSeconds:0,distanceKm:0,kind:'normal',notes:'',loggedAt:now-86400000})) });
const workout = (exercises:SessionExercise[]):Workout => ({id:'w',name:'Training',startedAt:now-86400000,status:'completed',notes:'',restUsed:false,revision:0,exercises});
it('unrelated leg dose and failure do not change chest recovery',()=>{
 const chest=ex('Chest Press','chest'); const legs=ex('Squat','quads',18);legs.loggedSets.forEach(s=>s.kind='failure');
 expect(readinessByRegion([workout([chest,legs])],[],now).Push).toBe(readinessByRegion([workout([chest])],[],now).Push);
});
it('failure type and RIR zero describe the same effort',()=>{
 const a=ex('Chest Press','chest'),b=structuredClone(a);a.loggedSets.forEach(s=>s.kind='failure');b.loggedSets.forEach(s=>s.rir=0);
 expect(readinessByRegion([workout([a])],[],now)).toEqual(readinessByRegion([workout([b])],[],now));
});
it('timed duration does not receive a high rep multiplier',()=>{
 const a=ex('Weighted hold','chest'),b=structuredClone(a);a.tracking=b.tracking='duration';a.loggedSets.forEach(s=>s.durationSeconds=10);b.loggedSets.forEach(s=>s.durationSeconds=60);
 expect(readinessByRegion([workout([a])],[],now)).toEqual(readinessByRegion([workout([b])],[],now));
});
it('unknown muscles, empty sets, untouched regions and future work remain unknown',()=>{
 expect(readinessByRegion([workout([ex('Mystery movement','')])],[],now)).toEqual({});
 const empty=ex('Chest Press','chest');empty.loggedSets.forEach(s=>{s.reps=0;s.weightKg=0});expect(readinessByRegion([workout([empty])],[],now)).toEqual({});
 const future=workout([ex('Chest Press','chest')]);future.startedAt=now+1;expect(readinessByRegion([future],[],now)).toEqual({});
 expect(readinessByRegion([],['Legs','invalid'],now)).toEqual({Legs:0});
 const derived=deriveSnapshot({profile:defaultProfile,workouts:[],plans:[],exercises:[],measurements:[],photos:[],gyms:[],checkins:[]},now);
 expect(derived.state).toBe('unknown'); expect(derived.readiness).toBe(0);
});
