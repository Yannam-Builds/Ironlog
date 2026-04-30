import { Model } from '@nozbe/watermelondb';
import { field, text, relation, children, readonly, date } from '@nozbe/watermelondb/decorators';

export default class PlanDay extends Model {
  static table = 'plan_days';

  static associations = {
    plans: { type: 'belongs_to', key: 'plan_id' },
    plan_exercises: { type: 'has_many', foreignKey: 'plan_day_id' },
    workouts: { type: 'has_many', foreignKey: 'plan_day_id' },
  };

  @text('name') name;
  @text('color') color;
  @field('order_index') orderIndex;
  @readonly @date('created_at') createdAt;
  @date('updated_at') updatedAt;

  @relation('plans', 'plan_id') plan;
  @children('plan_exercises') exercises;
}

