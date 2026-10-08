import { expect, it } from "vitest";
import { exerciseTutorial } from "../src/domain/exercise-tutorial";
import links from "../src/generated/exercise-tutorials.json";
import { readFileSync } from "node:fs";
it("uses the native name key and preserves mapped URLs exactly", () => {
  expect(exerciseTutorial("  PLANK  ")).toBe(links.plank);
  expect(exerciseTutorial("Ab Crunch Machine")).toBe(links.abcrunchmachine);
  expect(exerciseTutorial("not a catalog exercise")).toBeUndefined();
  expect(exerciseTutorial("constructor")).toBeUndefined();
});
it("contains only HTTP or HTTPS destinations exported from the native map", () => {
  const native = JSON.parse(readFileSync("../app/src/main/assets/ironlog/exercise_youtube_by_normalized_name.json", "utf8")) as Record<string, {youtubeLink: string}>;
  expect(links).toEqual(Object.fromEntries(Object.entries(native).map(([key, row]) => [key, row.youtubeLink])));
  for (const link of Object.values(links))
    expect(["http:", "https:"]).toContain(new URL(link).protocol);
});
