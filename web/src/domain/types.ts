export type Tracking =
  "weight_reps" | "bodyweight_reps" | "duration" | "duration_distance";
export type SetKind = "normal" | "warmup" | "failure" | "drop" | "amrap";
export interface Exercise {
  id: string;
  name: string;
  muscle: string;
  equipment: string;
  tracking: Tracking;
  secondaryMuscles?: string[];
  aliases?: string[];
  custom?: boolean;
}
export interface PlannedExercise {
  id: string;
  exerciseId: string;
  name: string;
  sets: number;
  reps: string;
  restSeconds: number;
  notes: string;
  supersetGroup: string;
  isWarmup: boolean;
}
export interface PlanDay {
  id: string;
  name: string;
  color: string;
  exercises: PlannedExercise[];
}
export interface Plan {
  id: string;
  name: string;
  description: string;
  goal: string;
  days: PlanDay[];
  order: number;
  templateId?: string;
}
export interface LoggedSet {
  id: string;
  weightKg: number;
  reps: number;
  durationSeconds: number;
  distanceKm: number;
  kind: SetKind;
  rpe?: number;
  rir?: number;
  notes: string;
  loggedAt: number;
}
export interface WarmupTarget {
  id: string;
  weightKg: number;
  reps: number;
}
export interface SessionExercise extends PlannedExercise {
  tracking: Tracking;
  muscle: string;
  equipment: string;
  secondaryMuscles?: string[];
  loggedSets: LoggedSet[];
  pendingWarmups: WarmupTarget[];
}
export interface Workout {
  id: string;
  planId?: string;
  dayId?: string;
  name: string;
  startedAt: number;
  completedAt?: number;
  durationSeconds?: number;
  status: "active" | "completed" | "discarded";
  exercises: SessionExercise[];
  notes: string;
  rating?: number;
  restEndsAt?: number;
  restUsed: boolean;
  revision: number;
  imported?: boolean;
}
export interface Profile {
  name: string;
  age: number;
  heightCm: number;
  weightKg: number;
  experience: string;
  goal: string;
  weeklyGoal: number;
  sessionMinutes: number;
  coaching: string;
  onboardingStep: number;
  onboarded: boolean;
  activePlanId?: string;
  theme: string;
  unit: "kg" | "lb";
  effort: "rpe" | "rir";
  restSeconds: number;
  barKg: number;
  platesKg: number[];
  keepAwake: boolean;
  badgeUnlocks: Record<string, number>;
  recoveryWeeks: string[];
  lastBackupAt?: number;
}
export interface Measurement {
  id: string;
  date: string;
  type: string;
  value: number;
  unit: string;
}
export interface Photo {
  id: string;
  date: string;
  notes: string;
  blob: Blob;
}
export interface RecoveryCheckin {
  id: string;
  at: number;
  soreness: number;
  sleep: number;
  energy: number;
  painRegions: string[];
}
export interface Gym {
  id: string;
  name: string;
  barKg: number;
  platesKg: number[];
}
export interface AppSnapshot {
  profile: Profile;
  plans: Plan[];
  workouts: Workout[];
  exercises: Exercise[];
  measurements: Measurement[];
  photos: Photo[];
  checkins: RecoveryCheckin[];
  gyms: Gym[];
}
export interface ImportResult {
  imported: number;
  unresolved: number;
  skipped: number;
  warnings: string[];
}
export const defaultProfile: Profile = {
  name: "",
  age: 25,
  heightCm: 170,
  weightKg: 70,
  experience: "beginner",
  goal: "General Fitness",
  weeklyGoal: 3,
  sessionMinutes: 60,
  coaching: "balanced",
  onboardingStep: 0,
  onboarded: false,
  theme: "dark",
  unit: "kg",
  effort: "rpe",
  restSeconds: 90,
  barKg: 20,
  platesKg: [20, 15, 10, 5, 2.5, 1.25],
  keepAwake: true,
  badgeUnlocks: {},
  recoveryWeeks: [],
};
