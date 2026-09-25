import type {
  OnboardingLedgerBaseline,
  Profile,
  TrainingSignals,
} from "./types";

export const ONBOARDING_BASELINE_PROVENANCE = "onboarding_self_report" as const;
export const ONBOARDING_BASELINE_FORMULA =
  "trainingAgeMonths*4.345*historicalTrainingDaysPerWeek" as const;

const clamp = (value: number, min: number, max: number) =>
  Math.max(min, Math.min(max, value));

const toStat = (value: number, scale: number) =>
  clamp(Math.round(1 + Math.log(1 + value / scale) * 180), 1, 999);

export function calculateOnboardingBaseline(
  profile: Pick<
    Profile,
    | "trainingAgeMonths"
    | "historicalTrainingDaysPerWeek"
    | "weeklyGoal"
    | "weightKg"
    | "onboardingBodyweightKg"
    | "hasPastTraining"
    | "hasGymAccess"
    | "baselinePushups"
    | "baselinePullups"
    | "baselineBenchKg"
    | "baselineLatPulldownKg"
    | "baselineMileRunSeconds"
  >,
  seededAt = Date.now(),
): OnboardingLedgerBaseline {
  const trainingAgeMonths = Math.max(0, Math.round(profile.trainingAgeMonths));
  const historicalTrainingDaysPerWeek = clamp(
    Math.round(profile.historicalTrainingDaysPerWeek),
    1,
    7,
  );
  const weeklyGoal = clamp(Math.round(profile.weeklyGoal), 1, 7);
  const bodyweight = profile.onboardingBodyweightKg ??
    (profile.weightKg > 0 ? profile.weightKg : 0);
  // Older web profiles did not store this answer. A non-zero training age was
  // their only equivalent signal, so retain that migration behavior.
  const hasPastTraining = profile.hasPastTraining || trainingAgeMonths > 0;
  const pushups = Math.max(0, profile.baselinePushups);
  const pullups = Math.max(0, profile.baselinePullups);
  const benchKg = Math.max(0, profile.baselineBenchKg);
  const latPulldownKg = Math.max(0, profile.baselineLatPulldownKg);
  const mileSeconds = Math.max(0, profile.baselineMileRunSeconds);
  const paceFactor = mileSeconds > 0
    ? clamp(900 / mileSeconds, 0, 2)
    : 0;
  const estimatedLifetimeSessions = Math.max(
    0,
    Math.round(trainingAgeMonths * 4.345 * historicalTrainingDaysPerWeek),
  );
  const exposure = estimatedLifetimeSessions;
  // The browser currently has no lift, calisthenics, run-test, or gym-access
  // questions. Those inputs intentionally stay at the native model's neutral
  // values instead of being guessed from an experience label.
  const strengthRaw = benchKg * 2.4 + latPulldownKg * 1.5 + pullups * 6 +
    pushups * 1.2 + bodyweight * 0.45 + exposure * 0.3;
  const powerRaw = benchKg * 1.6 + pullups * 3.5 + pushups * 0.8 +
    exposure * 0.12;
  const hypertrophyRaw = benchKg * 1.4 + latPulldownKg * 1.1 +
    pushups * 1.6 + pullups * 2.2 + exposure * 0.22;
  const enduranceRaw = pushups * 0.9 + exposure * 0.08 +
    paceFactor * 30 + weeklyGoal * 2.5;
  const agilityRaw = pullups + paceFactor * 55 + exposure * 0.05;
  const disciplineRaw =
    exposure * 0.16 + weeklyGoal * 8 + (hasPastTraining ? 18 : 0);
  const recoveryRaw = exposure * 0.1 + weeklyGoal * 6 +
    (profile.hasGymAccess ? 8 : 4);
  const meaningful = trainingAgeMonths > 0 || bodyweight > 0 ||
    hasPastTraining || pushups > 0 || pullups > 0 || benchKg > 0 ||
    latPulldownKg > 0 || mileSeconds > 0;
  const stats: TrainingSignals = meaningful
    ? {
        strength: toStat(strengthRaw, 45),
        power: toStat(powerRaw, 28),
        hypertrophy: toStat(hypertrophyRaw, 55),
        endurance: toStat(enduranceRaw, 18),
        agility: toStat(agilityRaw, 16),
        discipline: toStat(disciplineRaw, 22),
        recovery: toStat(recoveryRaw, 24),
      }
    : {
        strength: 0,
        power: 0,
        hypertrophy: 0,
        endurance: 0,
        agility: 0,
        discipline: 0,
        recovery: 0,
      };
  const score = Object.values(stats).reduce((sum, value) => sum + value, 0) / 7;
  const grade = !meaningful
    ? "Uncalibrated"
    : score >= 150 &&
        estimatedLifetimeSessions >= 180 &&
        trainingAgeMonths >= 16
      ? "Titanium"
      : score >= 110 &&
          estimatedLifetimeSessions >= 90 &&
          trainingAgeMonths >= 10
        ? "Steel"
        : score >= 80 &&
            estimatedLifetimeSessions >= 28 &&
            trainingAgeMonths >= 6
          ? "Iron"
          : score >= 45 &&
              estimatedLifetimeSessions >= 8 &&
              trainingAgeMonths >= 2
            ? "Graphite"
            : "Uncalibrated";
  const supportedBadgeIds: string[] = [];
  if (estimatedLifetimeSessions >= 1) supportedBadgeIds.push("first_workout");
  if (estimatedLifetimeSessions >= 10) supportedBadgeIds.push("workouts_10");
  if (estimatedLifetimeSessions >= 50) supportedBadgeIds.push("workouts_50");
  if (estimatedLifetimeSessions >= 100) supportedBadgeIds.push("workouts_100");
  if (trainingAgeMonths * 4.345 >= 4 && estimatedLifetimeSessions >= 4)
    supportedBadgeIds.push("consistency_4w");
  if (trainingAgeMonths * 30.4375 >= 365 && estimatedLifetimeSessions >= 1)
    supportedBadgeIds.push("member_365");

  return {
    version: 1,
    provenance: ONBOARDING_BASELINE_PROVENANCE,
    formula: ONBOARDING_BASELINE_FORMULA,
    trustScore: 0.5,
    estimatedLifetimeSessions,
    xp: estimatedLifetimeSessions * 20,
    grade,
    stats,
    supportedBadgeIds,
    seededAt,
  };
}

export function recomputeOnboardingBaseline(
  profile: Profile,
  now = Date.now(),
): Profile {
  const previousBaseline = profile.ledgerBaseline;
  const badgeUnlocks = Object.fromEntries(
    Object.entries(profile.badgeUnlocks).filter(([id, unlockedAt]) => {
      if (id === "s_rank") return false;
      // Builds before baseline badges became derived-only wrote provisional
      // unlocks at exactly the baseline seed instant. Remove only those legacy
      // rows; a badge earned independently at another instant remains durable.
      return !(
        previousBaseline?.supportedBadgeIds.includes(id) &&
        unlockedAt === previousBaseline.seededAt
      );
    }),
  );
  if (!profile.onboarded) return { ...profile, badgeUnlocks };
  const ledgerBaseline = calculateOnboardingBaseline(
    profile,
    previousBaseline?.seededAt ?? now,
  );
  return { ...profile, ledgerBaseline, badgeUnlocks };
}
