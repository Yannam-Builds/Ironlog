import { Model } from '@nozbe/watermelondb';
import { field, text, readonly, date } from '@nozbe/watermelondb/decorators';

export default class BodyMeasurement extends Model {
  static table = 'body_measurements';

  @field('measured_at') measuredAt;
  @field('bodyweight') bodyweight;
  @field('waist') waist;
  @field('chest') chest;
  @field('arm') arm;
  @field('thigh') thigh;
  @text('notes') notes;
  @readonly @date('created_at') createdAt;
  @date('updated_at') updatedAt;
}

