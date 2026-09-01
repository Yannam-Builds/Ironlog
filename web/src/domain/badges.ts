export type BadgeTier = "bronze" | "silver" | "gold" | "blue";

export type BadgeDefinition = {
  id: string;
  title: string;
  description: string;
  tier: BadgeTier;
  icon: string;
};

export const badgeDefinitions: readonly BadgeDefinition[] = [
  {
    id: "first_workout",
    title: "Iron Initiate",
    description: "Complete your first workout",
    tier: "bronze",
    icon: "ic_badge_dumbbell.png",
  },
  {
    id: "streak_3",
    title: "Spark",
    description: "Achieve a 3-day workout streak",
    tier: "bronze",
    icon: "ic_badge_flame.png",
  },
  {
    id: "first_rest_timer",
    title: "Patience",
    description: "Use the rest timer for the first time",
    tier: "bronze",
    icon: "ic_badge_hourglass.png",
  },
  {
    id: "first_plan",
    title: "Architect",
    description: "Create your first training plan",
    tier: "bronze",
    icon: "ic_badge_twin_dumbbells.png",
  },
  {
    id: "workouts_10",
    title: "Charged",
    description: "Complete 10 workouts",
    tier: "silver",
    icon: "ic_badge_lightning.png",
  },
  {
    id: "consistency_4w",
    title: "Clockwork",
    description: "Train consistently for 4 weeks",
    tier: "silver",
    icon: "ic_badge_calendar.png",
  },
  {
    id: "first_pr",
    title: "Muscle Memory",
    description: "Log your first personal record",
    tier: "silver",
    icon: "ic_badge_flexed_arm.png",
  },
  {
    id: "ai_activated",
    title: "Augmented",
    description: "Activate Cloud AI coaching",
    tier: "silver",
    icon: "ic_badge_atom.png",
  },
  {
    id: "progressive_streak",
    title: "Growth Curve",
    description: "Progress in 4 consecutive workouts",
    tier: "silver",
    icon: "ic_badge_chart.png",
  },
  {
    id: "workouts_50",
    title: "Champion",
    description: "Complete 50 workouts",
    tier: "gold",
    icon: "ic_badge_trophy.png",
  },
  {
    id: "streak_30",
    title: "Ironclad",
    description: "Achieve a 30-day workout streak",
    tier: "gold",
    icon: "ic_badge_shield.png",
  },
  {
    id: "workouts_100",
    title: "Sovereign",
    description: "Complete 100 workouts",
    tier: "gold",
    icon: "ic_badge_crown.png",
  },
  {
    id: "volume_milestone",
    title: "Summit",
    description: "Lift 100,000 kg total volume",
    tier: "gold",
    icon: "ic_badge_mountain.png",
  },
  {
    id: "member_365",
    title: "Eternal",
    description: "365 days since your first workout",
    tier: "blue",
    icon: "ic_badge_infinity.png",
  },
  {
    id: "all_goal_modes",
    title: "Multiclass",
    description: "Train with all 3 available goal modes",
    tier: "blue",
    icon: "ic_badge_3stars.png",
  },
];

const definitionsById = new Map(
  badgeDefinitions.map((definition) => [definition.id, definition]),
);

export function badgeDefinition(id: string): BadgeDefinition {
  return (
    definitionsById.get(id) ?? {
      id,
      title: id.replaceAll("_", " "),
      description: "Earned achievement",
      tier: "bronze",
      icon: "ic_badge_dumbbell.png",
    }
  );
}
