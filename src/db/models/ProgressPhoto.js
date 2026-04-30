import { Model } from '@nozbe/watermelondb';
import { field, text, readonly, date } from '@nozbe/watermelondb/decorators';

export default class ProgressPhoto extends Model {
  static table = 'progress_photos';

  @text('file_uri') fileUri;
  @field('taken_at') takenAt;
  @field('bodyweight') bodyweight;
  @text('notes') notes;
  @readonly @date('created_at') createdAt;
  @date('updated_at') updatedAt;
}
