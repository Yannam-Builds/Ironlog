import { expect, it } from "vitest";
import { queryExerciseSearch } from "../src/domain/exercise-search";
import type { Exercise } from "../src/domain/types";
const exercise = (
  id: string,
  name: string,
  extra: Partial<Exercise> = {},
): Exercise => ({
  id,
  name,
  muscle: "Chest",
  equipment: "Barbell",
  tracking: "weight_reps",
  ...extra,
});
it("does not treat a lone two-character word as a broad prefix match", () => {
  expect(
    queryExerciseSearch([exercise("one", "Bench Press")], "be zz"),
  ).toEqual([]);
  expect(
    queryExerciseSearch([exercise("one", "Bench Press")], "ben zz").map(
      (row) => row.id,
    ),
  ).toEqual(["one"]);
});
it("ranks exact, prefix, substring and aliases before broad metadata matches", () => {
  const rows = [
    exercise("broad", "Z Push", { secondaryMuscles: ["Bench"] }),
    exercise("alias", "A Press", { aliases: ["Bench"] }),
    exercise("substring", "Incline Bench Press"),
    exercise("prefix", "Bench Press"),
    exercise("exact", "Bench"),
  ];
  expect(queryExerciseSearch(rows, "bench").map((row) => row.id)).toEqual([
    "exact",
    "prefix",
    "alias",
    "substring",
    "broad",
  ]);
  expect(queryExerciseSearch(rows, "!!!")).toEqual(rows);
});
it("normalizes camel case and punctuation and searches metadata while excluding unrelated rows", () => {
  const rows = [
    exercise("one", "UpperBodyPress", {
      difficulty: "beginner",
      movementPattern: "push",
    }),
    exercise("two", "Cable Row", { muscle: "Back", equipment: "Cable" }),
  ];
  expect(queryExerciseSearch(rows, "upper-body").map((row) => row.id)).toEqual([
    "one",
  ]);
  expect(
    queryExerciseSearch(rows, "beginner push").map((row) => row.id),
  ).toEqual(["one"]);
  expect(queryExerciseSearch(rows, "xyz")).toEqual([]);
});
