import { useState } from 'react';
import type { SessionExercise } from '../domain/types';
import { recentPerformances, recentSetLabel } from '../domain/recent-performance';
import { useApp, displayWeight } from '../ui/context';
import { Button, Sheet } from '../ui/components';

export function RecentPerformanceControl({exercise,dayId}:{exercise:SessionExercise;dayId?:string}) {
  const {data}=useApp();
  const [open,setOpen]=useState(false);
  const [sameDay,setSameDay]=useState(false);
  // The shared snapshot owns database observation; compute this bounded view only while open.
  const rows=open ? recentPerformances(data.workouts,exercise,sameDay ? dayId : undefined) : [];
  return <>
    <button className="text-button" onClick={()=>{setSameDay(false);setOpen(true);}}>Recent performance</button>
    {open && <Sheet title="Recent performance" onClose={()=>setOpen(false)}>
      <h3>{exercise.name}</h3>
      <p>Retained working sets from completed workouts. A different setup is context, not a like-for-like target.</p>
      {dayId && <div className="two-col" role="group" aria-label="Performance history scope">
        <Button variant={sameDay?'secondary':'primary'} aria-pressed={!sameDay} onClick={()=>setSameDay(false)}>All workouts</Button>
        <Button variant={sameDay?'primary':'secondary'} aria-pressed={sameDay} onClick={()=>setSameDay(true)}>This plan day</Button>
      </div>}
      {!rows.length && <p>No previous working sets in this view. Your current workout will appear after it is completed.</p>}
      {rows.map((row,i)=><section className="card" key={`${row.workoutId}:${row.exercise.id}:${i}`}>
        <small className="muted">{new Date(row.occurredAt).toLocaleString()}</small>
        <h3>{row.workoutName}</h3>
        <p>{row.comparable?'Same tracking and equipment':'Different or unrecorded setup'}</p>
        <p className="muted">{[row.exercise.name,row.exercise.equipment,row.exercise.tracking.replaceAll('_',' ')].filter(Boolean).join(' · ')}</p>
        {row.exercise.loggedSets.slice(0,12).map((set,index)=><p key={set.id}>{index+1} · {recentSetLabel(row.exercise,set,data.profile.unit,displayWeight)}</p>)}
        {row.exercise.loggedSets.length>12 && <small>{row.exercise.loggedSets.length-12} more sets in History</small>}
        {row.exercise.notes && <p style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{row.exercise.notes}</p>}
      </section>)}
      <Button variant="ghost" onClick={()=>setOpen(false)}>Back to workout</Button>
    </Sheet>}
  </>;
}
