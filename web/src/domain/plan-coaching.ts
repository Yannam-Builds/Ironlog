import type { Plan, PlanDay, Workout } from "./types";
import type { TrainingIntelligence } from "./program-intelligence";
export function deloadDay(day: PlanDay, id: () => string): PlanDay {
  return { ...day, id: id(), name: `${day.name} (Deload)`, color: day.color || "#555555", exercises: day.exercises.filter(e => e.exerciseId.trim()).map(e => ({ ...e, id: id(), sets: Math.max(2, Math.trunc(e.sets / 2)), notes: "Deload: use 60% of working weight" })) };
}
export function recommendedPlanDay(plan: Plan | undefined, history: Workout[], now = Date.now()): PlanDay | undefined {
  if (!plan?.days.length) return undefined;
  const last = history.filter(w => w.status === "completed" && w.startedAt <= now && plan.days.some(d => d.id === w.dayId)).sort((a,b) => b.startedAt-a.startedAt)[0];
  const index = plan.days.findIndex(d => d.id === last?.dayId);
  return plan.days[(index + 1) % plan.days.length];
}
export function todayDirective(snapshot: TrainingIntelligence) {
  if (snapshot.neuralFatigue.isFlagged) return { title: "Rest or light cardio today", reason: `${snapshot.neuralFatigue.consecutiveDays} consecutive heavy days detected — recovery first.` };
  if (Object.values(snapshot.setsByMuscle).every(n => n === 0)) return { title: "Follow your next planned session", reason: "No mapped working sets this week yet. Build a consistent log before adjusting your program." };
  if (snapshot.prTrend === "slowing") return { title: "Review progression inputs this week", reason: "PR frequency is lower than the prior 30 days; check technique, rep quality, recovery and load selection before adding stress." };
  return { title: "You’re on track — train as planned", reason: "Use your plan, technique and current check-in. Review a complete week before changing volume." };
}
