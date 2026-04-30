import { useEffect, useState } from 'react';
import { Q } from '@nozbe/watermelondb';
import { database } from '../db/database';
import {
  addBodyMeasurement,
  updateBodyMeasurement,
  deleteBodyMeasurement,
} from '../db/repositories/bodyMeasurementRepository';

const BODY = database.get('body_measurements');

export default function useWatermelonBodyMeasurements() {
  const [measurements, setMeasurements] = useState([]);

  useEffect(() => {
    const sub = BODY.query(Q.sortBy('measured_at', Q.desc)).observe().subscribe(rows => {
      setMeasurements(rows.map(r => ({
        id: r.id,
        measuredAt: r.measuredAt,
        date: new Date(r.measuredAt).toISOString(),
        bodyweight: r.bodyweight,
        waist: r.waist,
        chest: r.chest,
        arm: r.arm,
        thigh: r.thigh,
        notes: r.notes,
      })));
    });
    return () => sub.unsubscribe();
  }, []);

  const add = (input) => addBodyMeasurement(input);
  const update = (id, input) => updateBodyMeasurement(id, input);
  const remove = (id) => deleteBodyMeasurement(id);

  // Legacy shape for BodyWeightScreen / BodyMeasurementsScreen: [{ id, weight, date }]
  const bodyWeight = measurements
    .filter(m => m.bodyweight != null)
    .map(m => ({ id: m.id, weight: m.bodyweight, date: m.date }));

  return { measurements, bodyWeight, add, update, remove };
}
