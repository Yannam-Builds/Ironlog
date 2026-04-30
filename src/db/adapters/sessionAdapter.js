function sortByNumberAsc(a, b, key) {
  return Number(a?.[key] || 0) - Number(b?.[key] || 0);
}

function safeIsoFromMs(value) {
  const ms = Number(value || 0);
  return new Date(ms > 0 ? ms : Date.now()).toISOString();
}

function buildExerciseLookup(exerciseRows = []) {
  return new Map((exerciseRows || []).map((row) => [row.id, row]));
}

function buildWorkoutExerciseLookup(workoutExerciseRows = []) {
  const byWorkout = new Map();
  (workoutExerciseRows || []).forEach((row) => {
    const workoutId = row?._raw?.workout_id;
    if (!workoutId) return;
    if (!byWorkout.has(workoutId)) byWorkout.set(workoutId, []);
    byWorkout.get(workoutId).push(row);
  });
  return byWorkout;
}

function buildSetLookup(setRows = []) {
  const byWorkoutExercise = new Map();
  (setRows || []).forEach((row) => {
    const workoutExerciseId = row?._raw?.workout_exercise_id;
    if (!workoutExerciseId) return;
    if (!byWorkoutExercise.has(workoutExerciseId)) byWorkoutExercise.set(workoutExerciseId, []);
    byWorkoutExercise.get(workoutExerciseId).push(row);
  });
  return byWorkoutExercise;
}

function mapSetRow(row) {
  const isWarmup = !!row?.isWarmup;
  const isDropset = !!row?.isDropset;
  const isAmrap = !!row?.isAmrap;
  const toFailure = !!row?.toFailure;
  return {
    id: row.id,
    setIndex: Number(row.setIndex || 0),
    weight: Number(row.weight) || 0,
    reps: Number(row.reps) || 0,
    rpe: row.rpe ?? null,
    rir: row.rir ?? null,
    restSeconds: Number(row.restSeconds) || 0,
    isWarmup,
    isDropset,
    isAmrap,
    toFailure,
    completedAt: row.completedAt ?? null,
    type: isWarmup
      ? 'warmup'
      : isDropset
        ? 'dropset'
        : isAmrap
          ? 'amrap'
          : toFailure
            ? 'failure'
            : 'normal',
  };
}

function inferTrackingTypeFromSets(sets = []) {
  const hasOnlyDuration = sets.length > 0 && sets.every((setRow) => {
    const reps = Number(setRow?.reps || 0);
    const weight = Number(setRow?.weight || 0);
    return reps > 0 && weight === 0;
  });
  return hasOnlyDuration ? 'duration' : 'weight_reps';
}

export function mapHistoryFromWatermelonRows(workoutRows = [], workoutExerciseRows = [], setRows = [], exerciseRows = []) {
  const exerciseById = buildExerciseLookup(exerciseRows);
  const workoutExercisesByWorkout = buildWorkoutExerciseLookup(workoutExerciseRows);
  const setsByWorkoutExercise = buildSetLookup(setRows);

  return (workoutRows || [])
    .slice()
    .sort((a, b) => Number(b?.startedAt || 0) - Number(a?.startedAt || 0))
    .map((workoutRow) => {
      const workoutExercises = (workoutExercisesByWorkout.get(workoutRow.id) || [])
        .slice()
        .sort((a, b) => sortByNumberAsc(a, b, 'orderIndex'));
      let totalSets = 0;
      let totalVolume = 0;

      const exercises = workoutExercises.map((workoutExerciseRow) => {
        const exerciseRow = exerciseById.get(workoutExerciseRow?._raw?.exercise_id);
        const mappedSets = (setsByWorkoutExercise.get(workoutExerciseRow.id) || [])
          .slice()
          .sort((a, b) => sortByNumberAsc(a, b, 'setIndex'))
          .map(mapSetRow);

        totalSets += mappedSets.length;
        mappedSets.forEach((setItem) => {
          if (!setItem.isWarmup) totalVolume += (setItem.weight || 0) * (setItem.reps || 0);
        });

        const primaryMuscle = exerciseRow?.primaryMuscle || 'Other';
        return {
          id: workoutExerciseRow.id,
          exerciseId: workoutExerciseRow?._raw?.exercise_id,
          name: exerciseRow?.name || 'Unknown',
          primaryMuscle,
          primaryMuscles: primaryMuscle ? [primaryMuscle] : [],
          equipment: exerciseRow?.equipment || 'Other',
          category: exerciseRow?.category || 'strength',
          isBodyweight: (exerciseRow?.equipment || '').toLowerCase() === 'bodyweight',
          trackingType: inferTrackingTypeFromSets(mappedSets),
          note: workoutExerciseRow?.notes || '',
          notes: workoutExerciseRow?.notes || '',
          sets: mappedSets,
        };
      });

      return {
        id: workoutRow.id,
        dayName: workoutRow?.name || 'Workout',
        date: safeIsoFromMs(workoutRow?.startedAt),
        duration: Number(workoutRow?.durationSeconds) || 0,
        sets: totalSets,
        totalVolume,
        rating: workoutRow?.rating ?? null,
        summaryText: workoutRow?.notes || '',
        isDeload: false,
        exercises,
      };
    });
}
