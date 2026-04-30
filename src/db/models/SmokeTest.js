import { Model } from '@nozbe/watermelondb';
import { field, readonly, date } from '@nozbe/watermelondb/decorators';

export default class SmokeTest extends Model {
  static table = 'db_smoke_tests';

  @field('label') label;
  @readonly @date('created_at') createdAt;
}

