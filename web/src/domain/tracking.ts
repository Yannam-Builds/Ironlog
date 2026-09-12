import type { SessionExercise, LoggedSet } from './types';
export const trackingOptions = ['weight_reps','bodyweight_reps','bodyweight_plus_weight_reps','assisted_bodyweight','duration','duration_weight','duration_distance','cardio'];
type TrackingExercise = Pick<SessionExercise,'tracking'> & Partial<SessionExercise>;
export const isTimed = (e: TrackingExercise) => ['duration','duration_weight','duration_distance','cardio'].includes(trackingMode(e));
export const isCardio = (e: TrackingExercise) => e.tracking.trim().toLowerCase() === 'cardio' || ['cardio','conditioning'].includes((e.category ?? '').trim().toLowerCase());
export function trackingMode(e: TrackingExercise): string {
  let raw=e.tracking.trim().toLowerCase(); const name=(e.name??'').toLowerCase(),category=(e.category??'').toLowerCase(),equipment=(e.equipment??'').toLowerCase();
  if(raw==='weight_reps') {
    if(/\b(planks?|holds?|stretch(es)?|mobility|isometric|dead hang|wall sit)\b/.test(name))raw='duration';
    else if(/\b(crunch(es)?|sit[- ]?ups?|leg pull[- ]?in|mountain climbers?|curls?|raises?|extensions?|press(es)?|row|pulldowns?|pushdowns?|fly(es)?|squats?|deadlifts?|hip thrusts?|abductions?|adductions?|dips?|pull[- ]?ups?|push[- ]?ups?|lunges?|shrugs?|calf raises?|box jumps?)\b/.test(name))raw='weight_reps';
    else if(/\b(treadmill|walk(ing)?|run(ning)?|sprints?|bike|cycling|cycle|rower|rowing|erg|ski|swim(ming)?|stair|elliptical|prowler|sled|carry|farmer|jump rope|battle rope)\b/.test(name)||/cardio|conditioning/.test(category+' '+equipment))raw='duration_distance';
    else if(/mobility|stretch/.test(category))raw='duration';
  }
  const bodyweight=e.isBodyweight || equipment==='bodyweight' || ['bodyweight_reps','bodyweight_plus_weight_reps','weighted_bodyweight','assisted_bodyweight'].includes(raw) || /\b(pull[- ]?up|chin[- ]?up|push[- ]?up|dips?|planks?|crunch(es)?|sit[- ]?up|leg raise|mountain climber|muscle[- ]?up|handstand|pistol|nordic)\b/.test(name);
  const assisted=/\bassisted\b/.test(name)&&bodyweight;
  if(raw==='assisted_bodyweight')return raw;
  if(['reps','reps_only','bodyweight_reps'].includes(raw))return assisted?'assisted_bodyweight':'bodyweight_reps';
  if(['weighted_bodyweight','bodyweight_plus_weight_reps'].includes(raw))return assisted?'assisted_bodyweight':'bodyweight_plus_weight_reps';
  if(raw==='weight_reps'&&bodyweight)return assisted?'assisted_bodyweight':'bodyweight_plus_weight_reps';
  return raw === 'cardio' ? 'duration' : raw;
}
export function trackingDimensions(e: TrackingExercise) {
  const mode = trackingMode(e);
  return {
    mode,
    known: trackingOptions.includes(mode),
    load: ['weight_reps', 'bodyweight_plus_weight_reps', 'assisted_bodyweight', 'duration_weight'].includes(mode),
    reps: ['weight_reps', 'bodyweight_reps', 'bodyweight_plus_weight_reps', 'assisted_bodyweight'].includes(mode),
    duration: isTimed(e),
    distance: mode === 'duration_distance',
  };
}
export function validWorkingSet(e: SessionExercise,s: LoggedSet) {
  const mode=trackingMode(e), performed=isTimed(e)?s.durationSeconds:s.reps;
  if(s.kind==='warmup'||!Number.isFinite(s.weightKg)||s.weightKg<0||!Number.isFinite(performed)||performed<=0) return false;
  if(mode && !trackingOptions.includes(mode))return false;
  if(e.requiresExternalLoad && ['weight_reps','bodyweight_plus_weight_reps','duration_weight',''].includes(mode) && s.weightKg<=0)return false;
  return true;
}
export function estimatedOneRm(e:SessionExercise,s:LoggedSet):number|undefined {
  if(!validWorkingSet(e,s)||isCardio(e)||!['weight_reps',''].includes(trackingMode(e))||s.weightKg<=0||s.reps<1||s.reps>30)return undefined;
  return s.reps===1?s.weightKg:s.weightKg*(1+s.reps/30);
}
export function externalLoadVolume(e:SessionExercise,s:LoggedSet) {
  return validWorkingSet(e,s)&&!isCardio(e)&&['weight_reps','bodyweight_plus_weight_reps',''].includes(trackingMode(e))?s.weightKg*s.reps:0;
}
export function cardioSeconds(e: SessionExercise, s: LoggedSet): number {
  if (!isCardio(e) || !validWorkingSet(e, s)) return 0;
  return isTimed(e) ? s.durationSeconds : trackingMode(e) === '' ? s.reps : 0;
}
export function setDescription(e:SessionExercise,s:LoggedSet,unit:string,display:(kg:number,unit:string)=>number) {
 const load=`${display(s.weightKg,unit)} ${unit}`, mode=trackingMode(e);
 if(isTimed(e))return `${s.durationSeconds} seconds${mode==='duration_distance'?` · ${s.distanceKm} km`:mode==='duration_weight'?` · ${load}`:''}`;
 if (mode && !trackingOptions.includes(mode)) return `Unrecognized tracking (${e.tracking}) · recorded values ${s.weightKg} / ${s.reps}`;
 return `${mode==='bodyweight_reps'||(mode==='bodyweight_plus_weight_reps'&&s.weightKg===0)?'BW':mode==='bodyweight_plus_weight_reps'?`BW + ${load}`:mode==='assisted_bodyweight'?`${load} assistance`:load} × ${s.reps}`;
}
