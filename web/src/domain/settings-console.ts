export type SettingsDestinationId =
  | "training"
  | "intelligence"
  | "appearance"
  | "notifications"
  | "data"
  | "about";

export type SettingsDestinationSpec = {
  id: SettingsDestinationId;
  title: string;
  description: string;
  keywords: string[];
};

export const settingsDestinations: SettingsDestinationSpec[] = [
  {
    id: "training",
    title: "Training",
    description: "Profile, workout behavior, equipment and tracking tools",
    keywords: ["athlete", "goal", "days", "weight", "unit", "rest", "barbell", "haptics", "exercise", "gym", "body weight", "plan"],
  },
  {
    id: "intelligence",
    title: "Intelligence",
    description: "Choose and configure the coaching engine",
    keywords: ["built in", "apex", "nano", "cloud", "api", "provider", "model", "coaching", "ai"],
  },
  {
    id: "appearance",
    title: "Appearance",
    description: "Theme, typography, spacing and optional effects",
    keywords: ["theme", "font", "type", "spacing", "shine", "glass", "motion", "visual"],
  },
  {
    id: "notifications",
    title: "Notifications",
    description: "Workout reminders, milestones, quiet hours and channels",
    keywords: ["reminder", "alert", "milestone", "quiet hours", "time", "channel", "test"],
  },
  {
    id: "data",
    title: "Data & Privacy",
    description: "Import, backup, export, privacy and destructive data actions",
    keywords: ["backup", "restore", "import", "export", "csv", "privacy", "delete history", "clear history", "reset pr"],
  },
  {
    id: "about",
    title: "About",
    description: "Version information and the app tutorial",
    keywords: ["version", "build", "tutorial", "onboarding", "help"],
  },
];

export function filterSettingsDestinations(query: string) {
  const terms = query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
  if (!terms.length) return settingsDestinations;
  return settingsDestinations.filter((destination) => {
    const searchable = [destination.title, destination.description, ...destination.keywords]
      .join(" ")
      .toLocaleLowerCase();
    return terms.every((term) => searchable.includes(term));
  });
}
