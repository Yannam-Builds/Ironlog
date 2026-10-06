// Consume the actual Kotlin regex lists so seed tracking rules have one source.
export function nativeTrackingNormalizer(source) {
  const rules = (name) => {
    const body = source.match(new RegExp(`val ${name} = listOf\\(([\\s\\S]*?)\\n    \\)`))?.[1];
    if (!body) throw Error(`Native tracking rule list missing: ${name}`);
    return [...body.matchAll(/Regex\(("(?:[^"\\]|\\.)*")\)/g)]
      .map(match => new RegExp(JSON.parse(match[1])));
  };
  const reps = rules("repBasedNameSignals");
  const duration = rules("durationOnlyNameSignals");
  const distance = rules("durationDistanceNameSignals");
  const valid = ["weight_reps", "duration", "duration_distance", "duration_weight"];
  return (entry, category, equipment) => {
    const name = entry.name.trim().toLowerCase();
    if (duration.some(rule => rule.test(name))) return "duration";
    if (reps.some(rule => rule.test(name))) return "weight_reps";
    if (distance.some(rule => rule.test(name))) return "duration_distance";
    if (/cardio|conditioning/.test(category)) return "duration_distance";
    if (/mobility|stretch/.test(category)) return "duration";
    if (/cardio|conditioning/.test(equipment.toLowerCase())) return "duration_distance";
    const explicit = (entry.trackingType ?? "").trim().toLowerCase();
    return valid.includes(explicit) ? explicit : "weight_reps";
  };
}
const title = value => (value ?? "").trim().split(/\s+/).filter(Boolean)
  .map(part => part[0].toUpperCase() + part.slice(1).toLowerCase()).join(" ");
export function nativeExerciseCatalog(entries, normalizerSource) {
  const normalize = nativeTrackingNormalizer(normalizerSource);
  const seen = new Set();
  return entries.filter(entry => {
    if (!entry.name?.trim()) return false;
    const key = entry.name.trim().toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/_+$/, "");
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  }).map(entry => {
    const equipment = title(entry.equipment) || "Other";
    const category = /cardio|conditioning/.test((entry.category ?? "").toLowerCase()) ? "cardio"
      : /stretch|mobility/.test((entry.category ?? "").toLowerCase()) ? "mobility" : "strength";
    const primary = (entry.primaryMuscles ?? [entry.primaryMuscle].filter(Boolean)).filter(value => value.trim()).map(title);
    const secondary = (entry.secondaryMuscles ?? []).filter(value => value.trim()).map(title);
    const contributions = {};
    for (const muscle of primary) contributions[muscle] = (contributions[muscle] ?? 0) + (secondary.length ? 0.7 : 1) / Math.max(primary.length, 1);
    for (const muscle of secondary) contributions[muscle] = (contributions[muscle] ?? 0) + 0.3 / Math.max(secondary.length, 1);
    return {
      id: entry.id, name: entry.name.trim(), aliases: entry.aliases ?? [],
      muscle: title(entry.primaryMuscle ?? entry.primaryMuscles?.[0] ?? "Other"),
      equipment, category, primaryMuscles: primary, secondaryMuscles: secondary,
      muscleContributions: contributions, tracking: normalize(entry, category, equipment),
      isBodyweight: entry.isBodyweight === true, requiresExternalLoad: entry.requiresExternalLoad === true,
      movementPattern: entry.movementPattern ?? undefined, difficulty: entry.difficulty ?? undefined,
    };
  });
}
