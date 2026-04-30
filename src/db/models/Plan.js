import { Model } from '@nozbe/watermelondb';
import { field, text, children, readonly, date } from '@nozbe/watermelondb/decorators';

export default class Plan extends Model {
  static table = 'plans';

  static associations = {
    plan_days: { type: 'has_many', foreignKey: 'plan_id' },
    workouts: { type: 'has_many', foreignKey: 'plan_id' },
  };

  @text('name') name;
  @text('goal') goal;
  @text('description') description;
  @field('is_active') isActive;
  @readonly @date('created_at') createdAt;
  @date('updated_at') updatedAt;

  @children('plan_days') days;
}

