import { Model } from '@nozbe/watermelondb';
import { field, text, children, relation, readonly, date } from '@nozbe/watermelondb/decorators';

export default class Workout extends Model {
  static table = 'workouts';

  static associations = {
    workout_exercises: { type: 'has_many', foreignKey: 'workout_id' },
    plans: { type: 'belongs_to', key: 'plan_id' },
    plan_days: { type: 'belongs_to', key: 'plan_day_id' },
  };

  @text('name') name;
  @field('started_at') startedAt;
  @field('completed_at') completedAt;
  @field('duration_seconds') durationSeconds;
  @field('rating') rating;
  @text('notes') notes;
  @text('status') status;
  @readonly @date('created_at') createdAt;
  @date('updated_at') updatedAt;

  @relation('plans', 'plan_id') plan;
  @relation('plan_days', 'plan_day_id') planDay;
  @children('workout_exercises') exercises;
}

