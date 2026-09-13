import palettes from "../generated/themes.json";
import { useSyncExternalStore } from "react";
export const themeNames: Record<string, string> = {
  obsidian_silver: "Obsidian Silver",
  deep_forest: "Deep Forest",
  titanium_blue: "Titanium Blue",
  monet: "Monet",
  royal_amethyst: "Royal Amethyst",
  midnight_teal: "Midnight Teal",
  crimson_steel: "Crimson Steel",
  burnt_terracotta: "Burnt Terracotta",
  electric_lemon: "Electric Lemon",
  dark: "Dark",
  amoled: "AMOLED",
  light: "Light",
};
export const themes = palettes as Record<string, Record<string, string>>;
function contrastInk(hex: string) {
  const channels = hex
    .slice(1, 7)
    .match(/../g)!
    .map((x) => parseInt(x, 16) / 255)
    .map((x) => (x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4));
  const l = channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
  return (l + 0.05) / 0.05 >= 1.05 / (l + 0.05) ? "#000000" : "#FFFFFF";
}
function subscribeTheme(listener: () => void) {
  const storage = (event: StorageEvent) => {
    if (event.key === "ironlog-theme") applyTheme(currentTheme());
  };
  window.addEventListener("ironlog-theme", listener);
  window.addEventListener("storage", storage);
  return () => {
    window.removeEventListener("ironlog-theme", listener);
    window.removeEventListener("storage", storage);
  };
}
export const useTheme = () =>
  useSyncExternalStore(subscribeTheme, currentTheme, () => "dark");
export function currentTheme() {
  try {
    const key = localStorage.getItem("ironlog-theme") || "dark";
    return themes[key] ? key : "dark";
  } catch {
    return "dark";
  }
}
export function applyTheme(id: string) {
  const key = themes[id] ? id : "dark";
  const colors = themes[key];
  for (const [role, value] of Object.entries(colors))
    document.documentElement.style.setProperty(`--${role}`, value);
  // Keep native tokens intact. Explicit contrast overrides only for web controls.
  document.documentElement.style.setProperty(
    "--button-ink",
    contrastInk(colors.accent),
  );
  document.documentElement.style.setProperty(
    "--danger-ink",
    contrastInk(colors.danger),
  );
  document.documentElement.style.setProperty(
    "--link",
    key === "dark" || key === "amoled"
      ? "#FF8C69"
      : key === "light"
        ? "#B71C0C"
        : colors.accent,
  );
  document.documentElement.dataset.theme = key;
  document.documentElement.style.colorScheme =
    key === "light" ? "light" : "dark";
  const meta = document.querySelector('meta[name="theme-color"]');
  meta?.setAttribute("content", colors.bg);
  try {
    localStorage.setItem("ironlog-theme", key);
  } catch {
    /* IndexedDB save errors are exposed by the application. */
  }
  window.dispatchEvent(new CustomEvent("ironlog-theme", { detail: key }));
}
