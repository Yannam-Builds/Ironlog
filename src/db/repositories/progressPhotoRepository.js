import { Q } from '@nozbe/watermelondb';
import { database } from '../database';
import { requireNonEmpty, requireNumberMin } from '../../utils/validation';

const PHOTOS = database.get('progress_photos');

export function getProgressPhotosObservable() {
  return PHOTOS.query(Q.sortBy('taken_at', Q.desc)).observe();
}

export async function getProgressPhotos() {
  return PHOTOS.query(Q.sortBy('taken_at', Q.desc)).fetch();
}

export async function addProgressPhoto(input = {}) {
  requireNonEmpty(input.fileUri, 'fileUri');
  if (input.bodyweight != null) requireNumberMin(input.bodyweight, 0, 'bodyweight');
  const now = Date.now();
  let created = null;
  await database.write(async () => {
    created = await PHOTOS.create((row) => {
      row.fileUri = String(input.fileUri);
      row.takenAt = Number(input.takenAt || now);
      row.bodyweight = input.bodyweight == null ? null : Number(input.bodyweight);
      row.notes = input.notes ? String(input.notes) : '';
      row._raw.created_at = now;
      row.updatedAt = now;
    });
  });
  return created;
}

export async function upsertProgressPhotoByDate(input = {}) {
  requireNonEmpty(input.fileUri, 'fileUri');
  const takenAt = Number(input.takenAt || Date.now());
  const dateKey = new Date(takenAt).toISOString().slice(0, 10);
  const existing = await PHOTOS.query().fetch();
  const match = existing.find((row) => new Date(Number(row.takenAt || 0)).toISOString().slice(0, 10) === dateKey);

  if (match) {
    await database.write(async () => {
      await match.update((row) => {
        row.fileUri = String(input.fileUri);
        row.takenAt = takenAt;
        row.bodyweight = input.bodyweight == null ? null : Number(input.bodyweight);
        row.notes = input.notes ? String(input.notes) : '';
        row.updatedAt = Date.now();
      });
    });
    return match;
  }
  return addProgressPhoto(input);
}

export async function deleteProgressPhoto(id) {
  const row = await PHOTOS.find(id);
  await database.write(async () => {
    await row.markAsDeleted();
    await row.destroyPermanently();
  });
}

export async function clearProgressPhotos() {
  const rows = await PHOTOS.query().fetch();
  if (!rows.length) return;
  await database.write(async () => {
    await database.batch(...rows.map((row) => row.prepareDestroyPermanently()));
  });
}
