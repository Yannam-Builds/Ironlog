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

