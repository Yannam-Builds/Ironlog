import { expect, it } from 'vitest';
import { creditedProof, readinessByRegion } from '../src/domain/engine';
import { decodeAndroidBackup, encodeAndroidBackup } from '../src/domain/codecs';
import { defaultProfile, type SessionExercise, type Workout } from '../src/domain/types';

const now = 1788610000000;
const exercise = (patch: Partial<SessionExercise> = {}): SessionExercise => ({
  id:'slot', exerciseId:'stable', name:'Mystery', muscle:'', equipment:'cable', tracking:'weight_reps',
  sets:3, reps:'8', restSeconds:90, notes:'', supersetGroup:'', isWarmup:false, pendingWarmups:[],
  loggedSets:[{id:'s', weightKg:20, reps:8, durationSeconds:0, distanceKm:0, kind:'normal', notes:'', loggedAt:now-86400000}], ...patch,
});
const workout = (e:SessionExercise):Workout => ({id:'w', name:'Training', startedAt:now-86400000, status:'completed', durationSeconds:600, notes:'', revision:0, restUsed:false, exercises:[e]});
it('only valid timed cardio sets earn the ten-minute cardio proof',()=>{
  const e=exercise({tracking:'cardio', category:'cardio'});
  e.loggedSets[0]={...e.loggedSets[0],reps:0,durationSeconds:600,kind:'warmup'};
  expect(creditedProof(workout(e),now)).toBe(false);
  e.loggedSets[0].kind='normal'; expect(creditedProof(workout(e),now)).toBe(true);
  e.tracking='future_metric'; e.loggedSets[0].reps=600;
  expect(creditedProof(workout(e),now)).toBe(false);
  e.tracking='bodyweight_reps'; expect(creditedProof(workout(e),now)).toBe(false);
});
it('expands primary arrays, broad contribution keys and case-insensitive fine keys',()=>{
  const baseline=readinessByRegion([workout(exercise({muscle:'chest'}))],[],now);
  expect(readinessByRegion([workout(exercise({primaryMuscles:['chest']}))],[],now)).toEqual(baseline);
  expect(readinessByRegion([workout(exercise({muscleContributions:{' Chest ':1}}))],[],now)).toEqual(baseline);
  expect(readinessByRegion([workout(exercise({muscleContributions:{' PEC_MAJOR ':1}}))],[],now))
    .toEqual(readinessByRegion([workout(exercise({muscleContributions:{pec_major:1}}))],[],now));
  expect(readinessByRegion([workout(exercise({primaryMuscles:['chest'],muscleContributions:{unrecognized:1}}))],[],now)).toEqual(baseline);
});
it('uses category in exercise recovery factor like native',()=>{
  const category=exercise({muscle:'quads',category:'squat'});
  expect(readinessByRegion([workout(category)],[],now)).toEqual(readinessByRegion([workout({...category,category:'',equipment:'squat'})],[],now));
});
it('exports an unnamed active finite setup as an active native gym',()=>{
  const inventory=[{weightKg:10,quantity:4}];
  const raw=JSON.parse(encodeAndroidBackup({profile:{...defaultProfile,barKg:15,platesKg:[10],plateInventory:inventory},plans:[],workouts:[],exercises:[],measurements:[],photos:[],checkins:[],gyms:[]}));
  delete raw.webExtension;
  const decoded=decodeAndroidBackup(JSON.stringify(raw)).snapshot;
  expect(decoded.profile).toMatchObject({barKg:15,plateInventory:inventory});
  expect(decoded.gyms).toHaveLength(1);
});
