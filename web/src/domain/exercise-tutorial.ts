import links from "../generated/exercise-tutorials.json";
/** Kotlin exerciseTutorialLink: exact name key first, then bundled compact-name keys. */
export function exerciseTutorial(name: string): string | undefined {
  const key = name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/_+$/, "");
  const lookup = Object.hasOwn(links, key) ? key : key.replace(/_/g, "");
  return Object.hasOwn(links, lookup)
    ? (links as Record<string, string>)[lookup]
    : undefined;
}
