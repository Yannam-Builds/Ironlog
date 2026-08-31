import { useSyncExternalStore } from "react";
const key = "ironlog-spacing";
export function normalizeSpacing(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value)
    ? Math.max(85, Math.min(125, Math.round(value / 5) * 5))
    : 100;
}
export function readSpacing() {
  try {
    return normalizeSpacing(JSON.parse(localStorage.getItem(key) || "null"));
  } catch {
    return 100;
  }
}
function applySpacing(value: number) {
  document.documentElement.dataset.spacing = String(value);
  document.documentElement.style.setProperty(
    "--ui-spacing",
    String(value / 100),
  );
}
export function saveSpacing(value: number) {
  const spacing = normalizeSpacing(value);
  try {
    localStorage.setItem(key, JSON.stringify(spacing));
  } catch {
    throw Error(
      "Could not save UI spacing. Allow browser storage and try again. Your current spacing has not changed.",
    );
  }
  applySpacing(spacing);
  window.dispatchEvent(new Event(key));
}
export function initializeSpacing() {
  applySpacing(readSpacing());
  const changed = (event: StorageEvent) => {
    if (event.key !== key && event.key !== null) return;
    applySpacing(readSpacing());
    window.dispatchEvent(new Event(key));
  };
  window.addEventListener("storage", changed);
  return () => window.removeEventListener("storage", changed);
}
const subscribe = (callback: () => void) => {
  window.addEventListener(key, callback);
  return () => window.removeEventListener(key, callback);
};
export const useSpacing = () =>
  useSyncExternalStore(subscribe, readSpacing, () => 100);
