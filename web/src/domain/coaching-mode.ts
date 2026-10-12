import type { Profile } from "./types";
export function cloudCoachingEnabled(profile: Profile) {
  // Older browser profiles used the presence of provider settings as opt-in.
  return profile.intelligenceMode ? profile.intelligenceMode === "cloud_ai" : !!profile.cloudConfig;
}
export function canonicalGoalMode(value: string) {
  const mode = value.trim().toLowerCase();
  if (mode === "strength" || mode === "hypertrophy") return mode;
  return ["general_fitness", "general fitness", "performance", "endurance"].includes(mode) ? "general_fitness" : undefined;
}
