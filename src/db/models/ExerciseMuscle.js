import { Model } from '@nozbe/watermelondb';
import { field, text, relation, readonly, date } from '@nozbe/watermelondb/decorators';

export default class ExerciseMuscle extends Model {
  static table = 'exercise_muscles';

  static associations = {
    exercises: { type: 'belongs_to', key: 'exercise_id' },
  };

  @text('muscle') muscle;
  @text('role') role;
  @field('contribution_fraction') contributionFraction;
  @readonly @date('created_at') createdAt;
  @date('updated_at') updatedAt;

  @relation('exercises', 'exercise_id') exercise;
}

