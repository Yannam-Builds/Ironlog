/** Unknown imported values remain explicit and cannot be credited as known dimensions. */
export type Tracking = string;
export interface ExerciseMetadata {
  primaryMuscles?: string[];
  muscleContributions?: Record<string, number>;
  category?: string;
  isBodyweight?: boolean;
  requiresExternalLoad?: boolean;
  movementPattern?: string;
  difficulty?: string;
}
export type SetKind = "normal" | "warmup" | "failure" | "drop" | "amrap";
export interface Exercise extends ExerciseMetadata {
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
  progressionRules?: ProgramRules;
  exerciseProgressionOverrides?: Record<string, string>;
}
export interface ProgramRules {
  progressionModel: string;
  blockLengthWeeks: number;
  currentWeek: number;
  deloadEveryWeeks: number;
  percent1RM: number;
  rpeTarget: number;
  rirTarget: number;
}
export interface LoggedSet {
  id: string;
  weightKg: number;
  reps: number;
  durationSeconds: number;
  distanceKm: number;
  kind: SetKind;
  toFailure?: boolean;
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
export interface SessionExercise extends PlannedExercise, ExerciseMetadata {
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
export interface TrainingSignals {
  strength: number;
  power: number;
  hypertrophy: number;
  endurance: number;
  agility: number;
  discipline: number;
  recovery: number;
}
export interface OnboardingLedgerBaseline {
  version: 1;
  provenance: "onboarding_self_report";
  formula: "trainingAgeMonths*4.345*historicalTrainingDaysPerWeek";
  trustScore: 0.5;
  estimatedLifetimeSessions: number;
  xp: number;
  grade: string;
  stats: TrainingSignals;
  supportedBadgeIds: string[];
  seededAt: number;
}
export interface Profile {
  name: string;
  age: number;
  yearOfBirth: number;
  heightCm: number;
  weightKg: number;
  onboardingBodyweightKg?: number;
  experience: string;
  goal: string;
  progressionStyle: string;
  goalMode: string;
  selectedTrainingDays: number[];
  trainingAgeMonths: number;
  historicalTrainingDaysPerWeek: number;
  hasPastTraining: boolean;
  hasGymAccess: boolean;
  baselinePushups: number;
  baselinePullups: number;
  baselineBenchKg: number;
  baselineLatPulldownKg: number;
  baselineMileRunSeconds: number;
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
  /** Total physical plates, shared between both sides. Missing means unlimited. */
  plateInventory?: { weightKg: number; quantity: number }[];
  keepAwake: boolean;
  planExerciseNotesVisible: boolean;
  exerciseNextNotes?: Record<string, string>;
  badgeUnlocks: Record<string, number>;
  ledgerBaseline?: OnboardingLedgerBaseline;
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
  plateInventory?: { weightKg: number; quantity: number }[];
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
  yearOfBirth: 2000,
  heightCm: 170,
  weightKg: 70,
  experience: "beginner",
  goal: "General Fitness",
  progressionStyle: "LINEAR",
  goalMode: "STRENGTH",
  selectedTrainingDays: [0, 2, 4],
  trainingAgeMonths: 0,
  historicalTrainingDaysPerWeek: 3,
  hasPastTraining: false,
  hasGymAccess: true,
  baselinePushups: 0,
  baselinePullups: 0,
  baselineBenchKg: 0,
  baselineLatPulldownKg: 0,
  baselineMileRunSeconds: 0,
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
  planExerciseNotesVisible: true,
  exerciseNextNotes: {},
  badgeUnlocks: {},
  recoveryWeeks: [],
};
