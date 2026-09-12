import { expect, it } from 'vitest';
import { recentPerformances, recentSetLabel, latestPerformedSet } from '../src/domain/recent-performance';
import type { SessionExercise, Workout } from '../src/domain/types';
const current: SessionExercise={id:'slot',exerciseId:'stable',name:'Cable hold',tracking:'duration_weight',equipment:'Cable',muscle:'chest',sets:3,reps:'30',restSeconds:90,notes:'',supersetGroup:'',isWarmup:false,pendingWarmups:[],loggedSets:[{id:'set',weightKg:20,reps:0,durationSeconds:30,distanceKm:0,kind:'normal',notes:'',loggedAt:100,rir:2}]};
const workout=(id:string,patch:Partial<Workout>={}):Workout=>({id,name:id,startedAt:100,status:'completed',exercises:[structuredClone(current)],revision:0,restUsed:false,notes:'',dayId:'a',...patch});
it('uses stable identity, completed nonfuture history and optional plan-day filtering',()=>{
 const rows=[workout('b'),workout('a'),workout('newer',{startedAt:150,dayId:'b'}),workout('future',{startedAt:201}),workout('active',{status:'active'}),workout('discarded',{status:'discarded'}),workout('name-only',{exercises:[{...current,exerciseId:'other'}]})];
 expect(recentPerformances(rows,current,undefined,200).map(r=>r.workoutId)).toEqual(['newer','a','b']);
 expect(recentPerformances(rows,current,'a',200).map(r=>r.workoutId)).toEqual(['a','b']);
 expect(recentPerformances(rows,{...current,exerciseId:''},undefined,200)).toEqual([]);
});
it('filters invalid and warmup sets, marks metadata comparability and keeps source immutable',()=>{
 const w=workout('w');w.exercises[0].loggedSets.push({...current.loggedSets[0],id:'warmup',kind:'warmup'},{...current.loggedSets[0],id:'empty',durationSeconds:0});
 const before=structuredClone(w);
 expect(recentPerformances([w],current,undefined,200)[0]).toMatchObject({comparable:true,exercise:{loggedSets:[current.loggedSets[0]]}});
 expect(w).toEqual(before);
 expect(recentPerformances([w],{...current,equipment:'Machine'},undefined,200)[0].comparable).toBe(false);
 expect(recentPerformances([w],{...current,tracking:'duration'},undefined,200)[0].comparable).toBe(false);
 expect(recentPerformances([w],{...current,equipment:''},undefined,200)[0].comparable).toBe(false);
});
it('keeps timed, added and assistance summaries distinct and picks latest timestamp for rest',()=>{
 const display=(kg:number)=>kg;
 expect(recentSetLabel(current,current.loggedSets[0],'kg',display)).toBe('30 seconds · 20 kg · RIR 2');
 expect(recentSetLabel({...current,tracking:'assisted_bodyweight'},{...current.loggedSets[0],reps:8},'kg',display)).toBe('20 kg assistance × 8 · RIR 2');
 expect(recentSetLabel({...current,tracking:'bodyweight_plus_weight_reps'},{...current.loggedSets[0],reps:8},'kg',display)).toBe('BW + 20 kg × 8 · RIR 2');
 const w=workout('w');w.exercises[0].loggedSets.unshift({...current.loggedSets[0],id:'later-warmup',kind:'warmup',loggedAt:150});
 w.exercises.push({...current,id:'other',loggedSets:[{...current.loggedSets[0],id:'future',loggedAt:300}]});
 expect(latestPerformedSet(w,200)?.set.id).toBe('later-warmup');
});
