import { z } from "zod";
const id = z.string().min(1).max(200);
const number = z.number().finite().nonnegative();
const tracking = z.enum([
  "weight_reps",
  "bodyweight_reps",
  "duration",
  "duration_distance",
]);
export const exerciseSchema = z.object({
  id,
  name: z.string().min(1),
  muscle: z.string(),
  equipment: z.string(),
  tracking,
  secondaryMuscles: z.array(z.string()).optional(),
  aliases: z.array(z.string()).optional(),
  custom: z.boolean().optional(),
});
const planned = z.object({
  id,
  exerciseId: z.string(),
  name: z.string().min(1),
  sets: z.number().int().min(1).max(1000),
  reps: z.string(),
  restSeconds: number,
  notes: z.string(),
  supersetGroup: z.string(),
  isWarmup: z.boolean(),
});
export const planSchema = z.object({
  id,
  name: z.string().min(1),
  description: z.string(),
  goal: z.string(),
  days: z.array(
    z.object({
      id,
      name: z.string(),
      color: z.string(),
      exercises: z.array(planned),
    }),
  ),
  order: z.number().finite(),
  templateId: z.string().optional(),
});
export const workoutSchema = z.object({
  id,
  planId: z.string().optional(),
  dayId: z.string().optional(),
  name: z.string(),
  startedAt: number,
  completedAt: number.optional(),
  durationSeconds: number.optional(),
  status: z.enum(["active", "completed", "discarded"]),
  notes: z.string(),
  rating: number.optional(),
  restEndsAt: number.optional(),
  restUsed: z.boolean(),
  revision: z.number().int().nonnegative(),
  imported: z.boolean().optional(),
  exercises: z.array(
    planned.extend({
      tracking,
      muscle: z.string(),
      equipment: z.string(),
      secondaryMuscles: z.array(z.string()).optional(),
      pendingWarmups: z.array(z.object({ id, weightKg: number, reps: number })),
      loggedSets: z.array(
        z.object({
          id,
          weightKg: number,
          reps: number,
          durationSeconds: number,
          distanceKm: number,
          kind: z.enum(["normal", "warmup", "failure", "drop", "amrap"]),
          rpe: z.number().min(0).max(10).optional(),
          rir: number.optional(),
          notes: z.string(),
          loggedAt: number,
        }),
      ),
    }),
  ),
});
export const profileSchema = z.object({
  name: z.string(),
  age: number,
  heightCm: number,
  weightKg: number,
  experience: z.string(),
  goal: z.string(),
  weeklyGoal: z.number().int().min(1).max(7),
  sessionMinutes: number,
  coaching: z.string(),
  onboardingStep: z.number().int().nonnegative(),
  onboarded: z.boolean(),
  activePlanId: z.string().optional(),
  theme: z.string(),
  unit: z.enum(["kg", "lb"]),
  effort: z.enum(["rpe", "rir"]),
  restSeconds: number,
  barKg: number,
  platesKg: z.array(z.number().positive()),
  keepAwake: z.boolean(),
  badgeUnlocks: z.record(z.string(), number),
  recoveryWeeks: z.array(z.string()),
  lastBackupAt: number.optional(),
});
export const snapshotSchema = z.object({
  profile: profileSchema,
  plans: z.array(planSchema),
  workouts: z.array(workoutSchema),
  exercises: z.array(exerciseSchema),
  measurements: z.array(
    z.object({
      id,
      date: z.string(),
      type: z.string(),
      value: number,
      unit: z.string(),
    }),
  ),
  photos: z.array(
    z.object({
      id,
      date: z.string(),
      notes: z.string(),
      blob: z.instanceof(Blob),
    }),
  ),
  checkins: z.array(
    z.object({
      id,
      at: number,
      soreness: z.number().int().min(0).max(5),
      sleep: z.number().int().min(0).max(5),
      energy: z.number().int().min(0).max(5),
      painRegions: z.array(z.string()),
    }),
  ),
  gyms: z.array(
    z.object({
      id,
      name: z.string(),
      barKg: number,
      platesKg: z.array(z.number().positive()),
    }),
  ),
});
