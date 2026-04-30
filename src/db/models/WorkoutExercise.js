import { Model } from '@nozbe/watermelondb';
import { field, text, relation, children, readonly, date } from '@nozbe/watermelondb/decorators';

export default class WorkoutExercise extends Model {
  static table = 'workout_exercises';

  static associations = {
    workouts: { type: 'belongs_to', key: 'workout_id' },
    exercises: { type: 'belongs_to', key: 'exercise_id' },
    workout_sets: { type: 'has_many', foreignKey: 'workout_exercise_id' },
  };

  @field('order_index') orderIndex;
  @text('superset_group') supersetGroup;
  @text('notes') notes;
  @readonly @date('created_at') createdAt;
  @date('updated_at') updatedAt;

  @relation('workouts', 'workout_id') workout;
  @relation('exercises', 'exercise_id') exercise;
  @children('workout_sets') sets;
}

