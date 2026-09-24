import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react';
import { beforeEach, afterEach, expect, it } from 'vitest';
import { App } from '../src/App';
import { bootstrap, db, saveProfile, startWorkout, mutateWorkout, readSnapshot } from '../src/data/store';
beforeEach(async()=>{await db.delete();await db.open();await bootstrap([]);await saveProfile({onboarded:true});});afterEach(cleanup);
it('logs and reloads weighted-duration with load and seconds in separate dimensions',async()=>{
 const w=await startWorkout();await mutateWorkout(w.id,w.revision,x=>x.exercises.push({id:'hold',exerciseId:'hold',name:'Cable hold',sets:3,reps:'60',restSeconds:0,notes:'',supersetGroup:'',isWarmup:false,tracking:'duration_weight',muscle:'chest',equipment:'cable',pendingWarmups:[],loggedSets:[]}));
 window.location.hash='#/workout';render(<App/>);
 fireEvent.change(await screen.findByLabelText('KG'),{target:{value:'20'}});fireEvent.change(screen.getByLabelText('Seconds'),{target:{value:'60'}});fireEvent.click(screen.getByRole('button',{name:/^Log$/}));
 await waitFor(()=>expect(screen.getByText('60 seconds · 20 kg')).toBeVisible());
 expect((await readSnapshot()).workouts[0].exercises[0].loggedSets[0]).toMatchObject({weightKg:20,durationSeconds:60,reps:0});
 cleanup();render(<App/>);expect(await screen.findByText('60 seconds · 20 kg')).toBeVisible();
});
it('captures library interpretation into a plan session before later library edits',async()=>{
 const e={id:'hold',name:'Cable hold',muscle:'chest',equipment:'cable',tracking:'duration_weight',category:'strength',primaryMuscles:['chest'],muscleContributions:{pec_major:1},requiresExternalLoad:true};
 await bootstrap([e]);await db.plans.put({id:'p',name:'Routine',description:'',goal:'',order:0,days:[{id:'d',name:'Day',color:'',exercises:[{id:'slot',exerciseId:'hold',name:e.name,sets:3,reps:'60',restSeconds:0,notes:'',supersetGroup:'',isWarmup:false}]}]});
 const w=await startWorkout('p','d');expect(w.exercises[0]).toMatchObject({primaryMuscles:['chest'],muscleContributions:{pec_major:1},requiresExternalLoad:true,category:'strength'});
});
it('assigns and removes a session-only superset group',async()=>{
 let w=await startWorkout();w=await mutateWorkout(w.id,w.revision,x=>x.exercises.push({id:'bench-slot',exerciseId:'bench',name:'Bench Press',sets:3,reps:'8',restSeconds:90,notes:'',supersetGroup:'',isWarmup:false,tracking:'weight_reps',muscle:'chest',equipment:'barbell',pendingWarmups:[],loggedSets:[]}));const name=w.exercises[0].name;window.location.hash='#/workout';render(<App/>);
 fireEvent.click(await screen.findByRole('button',{name:`Options for ${name}`}));fireEvent.click(screen.getByRole('button',{name:'Superset group'}));fireEvent.click(screen.getByRole('button',{name:'Group A'}));
 await waitFor(async()=>expect((await readSnapshot()).workouts[0].exercises[0].supersetGroup).toBe('A'));
 fireEvent.click(screen.getByRole('button',{name:`Options for ${name}`}));fireEvent.click(screen.getByRole('button',{name:'Superset group'}));fireEvent.click(screen.getByRole('button',{name:'No superset'}));
 await waitFor(async()=>expect((await readSnapshot()).workouts[0].exercises[0].supersetGroup).toBe(''));
});
it('hides or deletes active exercise notes with native scope and exposes the tutorial action',async()=>{
 await bootstrap([{id:'bench',name:'Bench Press',muscle:'chest',equipment:'barbell',tracking:'weight_reps'}]);
 let w=await startWorkout();w=await mutateWorkout(w.id,w.revision,x=>x.exercises.push({id:'bench-slot',exerciseId:'bench',name:'Bench Press',sets:3,reps:'8',restSeconds:90,notes:'Keep shoulder blades pinned',supersetGroup:'',isWarmup:false,tracking:'weight_reps',muscle:'chest',equipment:'barbell',pendingWarmups:[],loggedSets:[]}));
 window.location.hash='#/workout';render(<App/>);
 expect(await screen.findByText('Keep shoulder blades pinned')).toBeVisible();
 fireEvent.click(screen.getByRole('button',{name:'Workout options'}));
 fireEvent.click(screen.getByRole('button',{name:'Exercise notes'}));
 fireEvent.click(screen.getByRole('switch',{name:'Show exercise notes'}));
 await waitFor(()=>expect(screen.queryByText('Keep shoulder blades pinned')).not.toBeInTheDocument());
 expect((await readSnapshot()).workouts[0].exercises[0].notes).toBe('Keep shoulder blades pinned');
 expect((await readSnapshot()).profile.planExerciseNotesVisible).toBe(false);
 fireEvent.click(screen.getByRole('button',{name:'Delete notes from this session'}));
 expect(screen.getByText('This removes every exercise-level note from the active workout. The source plan is unchanged.')).toBeVisible();
 fireEvent.click(screen.getByRole('button',{name:'Confirm delete notes'}));
 await waitFor(async()=>expect((await readSnapshot()).workouts[0].exercises[0].notes).toBe(''));
 fireEvent.click(screen.getByRole('button',{name:'Options for Bench Press'}));
 const tutorial=screen.getByRole('link',{name:'Watch on YouTube'});
 expect(tutorial).toHaveAttribute('href','https://www.youtube.com/results?search_query=Bench%20Press+exercise+tutorial');
 expect(tutorial).toHaveAttribute('target','_blank');
 expect(tutorial).toHaveAttribute('rel','noopener noreferrer');
});
it('persists native rest add, pause, resume and skip controls across reload',async()=>{
 let w=await startWorkout();w=await mutateWorkout(w.id,w.revision,x=>{x.restEndsAt=Date.now()+90_000;x.restUsed=true;});
 window.location.hash='#/workout';render(<App/>);
 fireEvent.click(await screen.findByRole('button',{name:'Pause rest'}));
 await waitFor(async()=>expect((await readSnapshot()).workouts[0].restPausedRemainingMs).toBeGreaterThan(80_000));
 cleanup();render(<App/>);
 expect(await screen.findByRole('button',{name:'Resume rest'})).toBeVisible();
 const before=(await readSnapshot()).workouts[0].restPausedRemainingMs!;
 fireEvent.click(screen.getByRole('button',{name:'Add 30 seconds'}));
 await waitFor(async()=>expect((await readSnapshot()).workouts[0].restPausedRemainingMs).toBe(before+30_000));
 await waitFor(()=>expect(screen.getByRole('button',{name:'Resume rest'})).toBeEnabled());
 fireEvent.click(screen.getByRole('button',{name:'Resume rest'}));
 await waitFor(async()=>expect((await readSnapshot()).workouts[0].restEndsAt).toBeGreaterThan(Date.now()+100_000));
 await waitFor(()=>expect(screen.getByRole('button',{name:'Pause rest'})).toBeEnabled());
 fireEvent.click(screen.getByRole('button',{name:'Skip rest'}));
 await waitFor(async()=>expect((await readSnapshot()).workouts[0].restEndsAt).toBeUndefined());
 await waitFor(()=>expect(screen.queryByRole('button',{name:'Skip rest'})).not.toBeInTheDocument());
});

