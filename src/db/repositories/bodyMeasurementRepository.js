import { Q } from '@nozbe/watermelondb';
import { database } from '../database';
import { requireNumberMin } from '../../utils/validation';

const BODY = database.get('body_measurements');

export function getBodyMeasurementsObservable() {
  return BODY.query(Q.sortBy('measured_at', Q.desc)).observe();
}

export async function addBodyMeasurement(input = {}) {
  if (input.bodyweight != null) requireNumberMin(input.bodyweight, 0, 'bodyweight');
  const now = Date.now();
  let created = null;
  await database.write(async () => {
    created = await BODY.create((row) => {
      row.measuredAt = Number(input.measuredAt || now);
      row.bodyweight = input.bodyweight == null ? null : Number(input.bodyweight);
      row.waist = input.waist == null ? null : Number(input.waist);
      row.chest = input.chest == null ? null : Number(input.chest);
      row.arm = input.arm == null ? null : Number(input.arm);
      row.thigh = input.thigh == null ? null : Number(input.thigh);
      row.notes = input.notes ? String(input.notes) : '';
      row._raw.created_at = now;
      row.updatedAt = now;
    });
  });
  return created;
}

export async function updateBodyMeasurement(id, input = {}) {
  const row = await BODY.find(id);
  await database.write(async () => {
    await row.update((item) => {
      if (input.measuredAt !== undefined) item.measuredAt = Number(input.measuredAt);
      if (input.bodyweight !== undefined) item.bodyweight = input.bodyweight == null ? null : Number(input.bodyweight);
      if (input.waist !== undefined) item.waist = input.waist == null ? null : Number(input.waist);
      if (input.chest !== undefined) item.chest = input.chest == null ? null : Number(input.chest);
      if (input.arm !== undefined) item.arm = input.arm == null ? null : Number(input.arm);
      if (input.thigh !== undefined) item.thigh = input.thigh == null ? null : Number(input.thigh);
      if (input.notes !== undefined) item.notes = input.notes ? String(input.notes) : '';
      item.updatedAt = Date.now();
    });
  });
  return row;
}

export async function deleteBodyMeasurement(id) {
  const row = await BODY.find(id);
  await database.write(async () => {
    await row.markAsDeleted();
    await row.destroyPermanently();
  });
}
