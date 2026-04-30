import { Model } from '@nozbe/watermelondb';
import { text, field } from '@nozbe/watermelondb/decorators';

export default class AppSetting extends Model {
  static table = 'app_settings';

  @text('key') key;
  @text('value') value;
  @text('value_type') valueType;
  @field('updated_at') updatedAt;
}

