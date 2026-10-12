import type { Profile } from "./types";
/** Credentials are not portable preferences, including those in legacy native settings. */
export function portableNativeSettings(value: Record<string, unknown>): Record<string, unknown> {
  const clean = (entry: unknown): unknown => {
    if (Array.isArray(entry)) return entry.map(clean);
    if (entry && typeof entry === "object") return portableNativeSettings(entry as Record<string, unknown>);
    if (typeof entry === "string" && /^[\s]*[\[{]/.test(entry)) { try { return JSON.stringify(clean(JSON.parse(entry))); } catch { /* Ordinary preference text remains text. */ } }
    return entry;
  };
  return Object.fromEntries(Object.entries(value).filter(([key]) => !/api.?key|token|secret|password|authorization/i.test(key)).map(([key,entry]) => [key,
    clean(entry),
  ]));
}
export function portableProfile(profile: Profile): Profile {
  return { ...profile, nativeSettings: profile.nativeSettings ? portableNativeSettings(profile.nativeSettings) : undefined };
}
