import { Model } from '@nozbe/watermelondb';
import { field, relation, readonly, date } from '@nozbe/watermelondb/decorators';

export default class WorkoutSet extends Model {
  static table = 'workout_sets';

  static associations = {
    workout_exercises: { type: 'belongs_to', key: 'workout_exercise_id' },
  };

  @field('set_index') setIndex;
  @field('weight') weight;
  @field('reps') reps;
  @field('rpe') rpe;
  @field('rir') rir;
  @field('rest_seconds') restSeconds;
  @field('is_warmup') isWarmup;
  @field('is_dropset') isDropset;
  @field('is_amrap') isAmrap;
  @field('to_failure') toFailure;
  @field('completed_at') completedAt;
  @readonly @date('created_at') createdAt;
  @date('updated_at') updatedAt;

  @relation('workout_exercises', 'workout_exercise_id') workoutExercise;
}

