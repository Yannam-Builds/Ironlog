import type { SessionExercise, PlateSetup } from "./types";
import { resolveProgressionPolicy, type ResolvedPolicy } from "./program-intelligence";
import { plateCalculation } from "./engine";
import { trackingMode } from "./tracking";
export type ProgressionAdvice = { action: "hold" | "add_reps" | "add_load"; weightKg: number; reps: number; reason: string; policy: ResolvedPolicy; workingSets: number; effortRecordedSets: number; missingEvidence: string[]; loadStepKg?: number; sourceDate?: number };
/** Port of ProgressionRecommendationEngine; advice never edits a plan or logs a set. */
export function recommendProgression(previous: SessionExercise, target: SessionExercise = previous, options: { policy?: ResolvedPolicy; setup?: PlateSetup; unit?: "kg" | "lb"; sourceDate?: number } = {}): ProgressionAdvice | null {
  const tracking = trackingMode(target);
  if (!["weight_reps", "bodyweight_reps", "bodyweight_plus_weight_reps"].includes(tracking)) return null;
  const sets = previous.loggedSets.filter(s => s.kind !== "warmup");
  if (!sets.length || sets.some(s => !Number.isFinite(s.weightKg) || s.weightKg < 0 || !Number.isFinite(s.reps) || s.reps < 1 || s.reps > 1000)) return null;
  const baseline = sets[0].weightKg;
  // The upper end of a rep range must be repeatable before increasing difficulty.
  const parsed = /^\s*(\d+)(?:\s*[–—-]\s*(\d+))?\s*$/.exec(target.reps);
  const targetReps = parsed ? Number(parsed[2] ?? parsed[1]) : 0;
  const margins = sets.map(s => {
    const values = [Number.isFinite(s.rir) && s.rir! >= 0 && s.rir! <= 10 ? s.rir : undefined, Number.isFinite(s.rpe) && s.rpe! >= 1 && s.rpe! <= 10 ? 10 - s.rpe! : undefined].filter((n): n is number => n !== undefined);
    return values.length ? Math.min(...values) : undefined;
  });
  const policy = options.policy ?? resolveProgressionPolicy();
  let step = options.unit === "lb" ? 5 / 2.20462262185 : 2.5;
  if (/barbell|ez.?bar/i.test(target.equipment) && options.setup) {
    const { barKg, platesKg, plateInventory } = options.setup;
    const smallest = Math.min(...(plateInventory ? plateInventory.filter(p => p.quantity >= 1 && p.quantity <= 100).map(p => p.weightKg) : platesKg).filter(n => Number.isFinite(n) && n > 0));
    step = 0;
    // Native ProgressionLoadability checks both the baseline and its smallest step.
    if (Number.isFinite(barKg) && barKg > 0 && baseline >= barKg && Number.isFinite(smallest) &&
        plateCalculation(baseline, barKg, platesKg, plateInventory).isValid &&
        plateCalculation(baseline + smallest * 2, barKg, platesKg, plateInventory).isValid) step = smallest * 2;
  }
  const evidence = { policy, workingSets: sets.length, effortRecordedSets: margins.filter(m => m !== undefined).length, missingEvidence: [...(margins.some(m => m === undefined) ? ["effort"] : []), ...(target.sets <= 0 || !targetReps ? ["targets"] : [])], sourceDate: options.sourceDate, loadStepKg: Number.isFinite(step) ? step : undefined };
  const advice = (action: ProgressionAdvice["action"], weightKg: number, reps: number, reason: string): ProgressionAdvice => ({ ...evidence, action, weightKg, reps, reason });
  const hold = (reason: string) => advice("hold", baseline, targetReps || Math.trunc(sets[0].reps), reason);
  if (target.sets <= 0 || !targetReps) return hold("Repeat and assess: set a rep target before increasing difficulty.");
  if (sets.length < target.sets || sets.some(s => s.reps < targetReps)) return hold("Repeat the load and build toward all planned reps first.");
  if (sets.some(s => s.kind !== "normal" || s.toFailure)) return hold("Repeat and assess: special set techniques need an individual progression decision.");
  if (margins.some(m => m === undefined)) return hold("Repeat and assess: record RPE or reps in reserve before increasing difficulty.");
  if (margins.some(m => m! < policy.minimumEffortMargin)) return hold("Repeat the load; the last session was already closer to your limit than this policy allows.");
  if (sets.some(s => Math.abs(s.weightKg - baseline) > .01)) return hold("Repeat your planned loading pattern; mixed loads need a set-by-set review.");
  const loadAllowed = tracking !== "bodyweight_reps" && baseline > 0 && step > 0 && Number.isFinite(step) && step / baseline <= policy.maximumLoadIncreaseRatio;
  const repsAdvice = (reason: string) => advice("add_reps", baseline, Math.trunc(Math.min(...sets.map(s => s.reps))) + 1, reason);
  if (policy.strategy === "percent_1rm" && tracking !== "bodyweight_reps" && baseline > 0) {
    const policyTarget = Math.max(...sets.map(s => s.weightKg * (1 + s.reps / 30))) * policy.percent1RM / 100;
    if (policyTarget <= baseline + .01) return hold(`Repeat and assess: the current load already meets this policy's ${policy.percent1RM}% estimated 1RM target.`);
    if (!loadAllowed || baseline + step > policyTarget * 1.02) return repsAdvice(`The next available load exceeds this policy's ${policy.percent1RM}% estimated 1RM target. Add one controlled rep instead.`);
  }
  if (!loadAllowed) return repsAdvice("Targets met with effort in reserve. Try one more rep with the same technique; a suitable load increase is unavailable.");
  return advice("add_load", baseline + step, targetReps, `Targets met within the ${policy.label.toLowerCase()} guardrails. Try the smallest available increase only if technique stays solid.`);
}
