import { Model } from '@nozbe/watermelondb';
import { field, text, relation, readonly, date } from '@nozbe/watermelondb/decorators';

export default class PlanExercise extends Model {
  static table = 'plan_exercises';

  static associations = {
    plan_days: { type: 'belongs_to', key: 'plan_day_id' },
    exercises: { type: 'belongs_to', key: 'exercise_id' },
  };

  @field('order_index') orderIndex;
  @field('sets') sets;
  @text('reps') reps;
  @field('rest_seconds') restSeconds;
  @text('superset_group') supersetGroup;
  @field('is_warmup') isWarmup;
  @text('notes') notes;
  @readonly @date('created_at') createdAt;
  @date('updated_at') updatedAt;

  @relation('plan_days', 'plan_day_id') planDay;
  @relation('exercises', 'exercise_id') exercise;
}

