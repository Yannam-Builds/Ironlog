import type { Exercise } from "./types";

export type ExerciseResolutionStatus = "matched" | "needs_review" | "unresolved";
export type ExerciseCandidate = { exercise: Exercise; score: number };
export type ExerciseResolution = {
  status: ExerciseResolutionStatus;
  matched?: Exercise;
  confidence: number;
  reason: string;
  topCandidates: ExerciseCandidate[];
};

const strongMatchThreshold = 88;
const reviewMatchThreshold = 52;
const muscleHints: Record<string, string[]> = {
  chest: ["chest", "pec", "pecs", "pectoral"], back: ["back", "lat", "lats", "row"],
  shoulders: ["shoulder", "shoulders", "delt", "delts"], biceps: ["bicep", "biceps", "curl"],
  triceps: ["tricep", "triceps", "pushdown"], quads: ["quad", "quads", "thigh"],
  hamstrings: ["hamstring", "hamstrings"], glutes: ["glute", "glutes"], calves: ["calf", "calves"],
  core: ["core", "abs", "ab", "abdominal"], forearms: ["forearm", "forearms", "grip"],
  traps: ["trap", "traps", "shrug"], cardio: ["cardio", "run", "bike", "treadmill", "rower"],
};
const equipmentHints: Record<string, string[]> = {
  barbell: ["barbell", "bb"], dumbbell: ["dumbbell", "db"], cable: ["cable"],
  machine: ["machine", "smith"], bodyweight: ["bodyweight", "bw", "pullup", "pushup", "dip"],
  band: ["band"], kettlebell: ["kettlebell", "kb"],
};
const movementHints: Record<string, string[]> = {
  push: ["push", "press"], pull: ["pull", "row"], hinge: ["hinge", "deadlift", "rdl"],
  squat: ["squat", "legpress"], lunge: ["lunge", "split", "squat"],
  isolation: ["curl", "extension", "raise", "fly"], conditioning: ["conditioning", "metcon", "interval"],
};

export const normalizeExerciseName = (value: string) => value
  .trim().toLowerCase().replace(/[_-]+/g, " ").replace(/[^a-z0-9 ]+/g, "").replace(/\s+/g, " ");
const tokenize = (value: string) => normalizeExerciseName(value).split(" ").filter(Boolean);
const intersection = <T>(a: Set<T>, b: Set<T>) => [...a].filter((value) => b.has(value));

function tokenJaccardScore(a: string, b: string) {
  const left = new Set(tokenize(a));
  const right = new Set(tokenize(b));
  if (!left.size || !right.size) return 0;
  return Math.max(0, Math.min(100, Math.trunc(intersection(left, right).length / new Set([...left, ...right]).size * 100)));
}

function damerauLevenshteinDistance(a: string, b: string) {
  const rows = Array.from({ length: a.length + 1 }, () => Array(b.length + 1).fill(0));
  for (let i = 0; i <= a.length; i++) rows[i][0] = i;
  for (let j = 0; j <= b.length; j++) rows[0][j] = j;
  for (let i = 1; i <= a.length; i++) for (let j = 1; j <= b.length; j++) {
    const cost = a[i - 1] === b[j - 1] ? 0 : 1;
    rows[i][j] = Math.min(rows[i - 1][j] + 1, rows[i][j - 1] + 1, rows[i - 1][j - 1] + cost);
    if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1])
      rows[i][j] = Math.min(rows[i][j], rows[i - 2][j - 2] + 1);
  }
  return rows[a.length][b.length];
}

function editSimilarityScore(a: string, b: string) {
  if (!a || !b) return 0;
  return Math.max(0, Math.min(100, Math.trunc((1 - damerauLevenshteinDistance(a, b) / Math.max(a.length, b.length, 1)) * 100)));
}

function hintBonus(input: string, candidate: Exercise) {
  const inputTokens = new Set(tokenize(input));
  let bonus = 0;
  const muscle = candidate.muscle.trim().toLowerCase();
  if (muscleHints[muscle]?.some((hint) => inputTokens.has(hint))) bonus += 8;
  const equipment = candidate.equipment.trim().toLowerCase();
  if (equipmentHints[equipment]?.some((hint) => inputTokens.has(hint))) bonus += 7;
  const movement = candidate.movementPattern?.trim().toLowerCase() ?? "";
  if (movementHints[movement]?.some((hint) => inputTokens.has(hint))) bonus += 6;
  return bonus;
}

function scoreSimilarity(input: string, candidate: Exercise) {
  const candidateName = normalizeExerciseName(candidate.name);
  const inputTokens = new Set(tokenize(input));
  const candidateTokens = new Set(tokenize(candidateName));
  const nameTokenScore = tokenJaccardScore(input, candidateName);
  const editScore = editSimilarityScore(input, candidateName);
  const containsBonus = input === candidateName ? 15 :
    ((input.includes(candidateName) || candidateName.includes(input)) && Math.abs(input.length - candidateName.length) <= 6 ? 10 : 0);
  const inputList = tokenize(input), candidateList = tokenize(candidateName);
  const prefixTokenBonus = inputList.length && candidateList.length >= inputList.length &&
    inputList.every((token, index) => candidateList[index] === token) ? 8 : 0;
  const extraInputPenalty = Math.min(9, [...inputTokens].filter((token) => !candidateTokens.has(token)).length * 3);
  const extraCandidatePenalty = Math.min(6, [...candidateTokens].filter((token) => !inputTokens.has(token)).length * 2);
  const typoTokenBonus = Math.min(14, [...inputTokens].reduce((sum, token) => {
    const best = Math.max(0, ...[...candidateTokens].map((other) => editSimilarityScore(token, other)));
    return sum + (best >= 82 ? 5 : best >= 70 ? 3 : 0);
  }, 0));
  const aliases = candidate.aliases ?? [];
  const aliasTokenScore = Math.max(0, ...aliases.map((alias) => Math.max(
    tokenJaccardScore(input, normalizeExerciseName(alias)), editSimilarityScore(input, normalizeExerciseName(alias)),
  )));
  const hints = hintBonus(input, candidate);
  const base = Math.trunc(nameTokenScore * 0.52 + editScore * 0.48);
  return Math.max(0, Math.min(100, Math.max(base + containsBonus + prefixTokenBonus + hints + typoTokenBonus - extraInputPenalty - extraCandidatePenalty, aliasTokenScore + hints)));
}

export function resolveExerciseCandidate(name: string, library: Exercise[]): ExerciseResolution {
  const normalized = normalizeExerciseName(name);
  if (!normalized) return { status: "unresolved", confidence: 0, reason: "Blank exercise name", topCandidates: [] };
  const exact = library.find((exercise) => normalizeExerciseName(exercise.name) === normalized);
  if (exact) return { status: "matched", matched: exact, confidence: 100, reason: "Exact normalized name", topCandidates: [] };
  const alias = library.find((exercise) => exercise.aliases?.some((value) => normalizeExerciseName(value) === normalized));
  if (alias) return { status: "matched", matched: alias, confidence: 98, reason: "Alias exact match", topCandidates: [] };
  const topCandidates = library.map((exercise) => ({ exercise, score: scoreSimilarity(normalized, exercise) }))
    .sort((a, b) => b.score - a.score || a.exercise.name.localeCompare(b.exercise.name)).slice(0, 6);
  const best = topCandidates[0];
  if (!best) return { status: "unresolved", confidence: 0, reason: "No library candidates", topCandidates: [] };
  if (best.score >= strongMatchThreshold) return { status: "matched", matched: best.exercise, confidence: best.score, reason: "High-confidence fuzzy match", topCandidates };
  if (best.score >= reviewMatchThreshold) return { status: "needs_review", matched: best.exercise, confidence: best.score, reason: "Ambiguous match candidate", topCandidates };
  return { status: "unresolved", matched: best.exercise, confidence: best.score, reason: "Low similarity", topCandidates };
}
