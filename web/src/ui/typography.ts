import { useSyncExternalStore } from "react";
import registry from "../generated/fonts.json";

export const fonts = registry.fonts;
export const typographyPresets = [
  {
    id: "original",
    name: "Original",
    description: "Keep each screen’s original weights",
  },
  {
    id: "light",
    name: "Light",
    description: "Lighter Lexend landing-page feel",
  },
  { id: "regular", name: "Regular", description: "Balanced, everyday reading" },
  { id: "bold", name: "Bold", description: "Stronger labels and headings" },
] as const;
export type TypographyChoice = {
  family: string;
  preset: (typeof typographyPresets)[number]["id"];
};
const key = "ironlog-typography";
const fallback: TypographyChoice = { family: "lexend", preset: "original" };
export function normalizeTypography(value: unknown): TypographyChoice {
  const data =
    value && typeof value === "object"
      ? (value as Record<string, unknown>)
      : {};
  return {
    family: fonts.some((f) => f.id === data.family)
      ? (data.family as string)
      : fallback.family,
    preset: typographyPresets.some((p) => p.id === data.preset)
      ? (data.preset as TypographyChoice["preset"])
      : fallback.preset,
  };
}
export function readTypography(): TypographyChoice {
  try {
    return normalizeTypography(JSON.parse(localStorage.getItem(key) || "null"));
  } catch {
    return { ...fallback };
  }
}
export function typographyWeight(original: number, choice: TypographyChoice) {
  const normalized = normalizeTypography(choice);
  const font = fonts.find((f) => f.id === normalized.family)!;
  const role = original <= 400 ? 0 : original < 600 ? 1 : 2;
  const weights = {
    light: [350, 450, 450],
    regular: [400, 500, 600],
    bold: [500, 650, 750],
  };
  const requested =
    normalized.preset === "original"
      ? original
      : weights[normalized.preset][role];
  return Math.min(font.maxWeight, Math.max(font.minWeight, requested));
}
export function applyTypography(value: TypographyChoice) {
  const choice = normalizeTypography(value);
  const root = document.documentElement;
  root.dataset.font = choice.family;
  root.dataset.fontPreset = choice.preset;
  root.style.setProperty(
    "--type-heading-weight",
    String(typographyWeight(700, choice)),
  );
  root.style.setProperty(
    "--font-family",
    choice.family === "lexend"
      ? "Lexend, system-ui, sans-serif"
      : `"IronLog ${choice.family}", Lexend, system-ui, sans-serif`,
  );
  root.style.setProperty(
    "--type-label-weight",
    String(typographyWeight(500, choice)),
  );
  for (const weight of [
    100, 200, 300, 350, 400, 450, 500, 600, 650, 700, 750, 800, 900,
  ])
    root.style.setProperty(
      `--type-w${weight}`,
      String(typographyWeight(weight, choice)),
    );
}
export function saveTypography(value: TypographyChoice) {
  const choice = normalizeTypography(value);
  try {
    localStorage.setItem(key, JSON.stringify(choice));
  } catch {
    throw Error(
      "Could not save the font preference. Allow browser storage and try again. Your current font has not changed.",
    );
  }
  applyTypography(choice);
  window.dispatchEvent(new Event(key));
}
export function initializeTypography() {
  applyTypography(readTypography());
  const storage = (event: StorageEvent) => {
    if (event.key !== key && event.key !== null) return;
    applyTypography(readTypography());
    window.dispatchEvent(new Event(key));
  };
  window.addEventListener("storage", storage);
  return () => window.removeEventListener("storage", storage);
}
const subscribe = (callback: () => void) => {
  window.addEventListener(key, callback);
  return () => window.removeEventListener(key, callback);
};
const snapshot = () => JSON.stringify(readTypography());
export const useTypography = (): TypographyChoice =>
  JSON.parse(
    useSyncExternalStore(subscribe, snapshot, () => JSON.stringify(fallback)),
  );
