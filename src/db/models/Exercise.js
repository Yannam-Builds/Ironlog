import { Model } from '@nozbe/watermelondb';
import { field, text, children, readonly, date } from '@nozbe/watermelondb/decorators';

export default class Exercise extends Model {
  static table = 'exercises';

  static associations = {
    exercise_muscles: { type: 'has_many', foreignKey: 'exercise_id' },
    plan_exercises: { type: 'has_many', foreignKey: 'exercise_id' },
    workout_exercises: { type: 'has_many', foreignKey: 'exercise_id' },
  };

  @text('name') name;
  @text('normalized_name') normalizedName;
  @text('primary_muscle') primaryMuscle;
  @text('equipment') equipment;
  @text('category') category;
  @field('is_custom') isCustom;
  @text('source') source;
  @text('notes') notes;
  @readonly @date('created_at') createdAt;
  @date('updated_at') updatedAt;

  @children('exercise_muscles') muscles;
}

