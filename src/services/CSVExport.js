
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';

function esc(val) {
  if (val === null || val === undefined) return '';
  const s = String(val);
  if (s.includes(',') || s.includes('"') || s.includes('\n'))
    return '"' + s.replace(/"/g, '""') + '"';
  return s;
}

export async function exportCSV(history) {
  const headers = ['Date', 'Workout Name', 'Exercise Name', 'Set Order', 'Weight', 'Reps', 'RPE', 'Distance', 'Seconds', 'Notes', 'Workout Notes'];
  const rows = [headers.join(',')];

  for (const session of [...history].reverse()) {
    const date = session.date ? session.date.split('T')[0] : '';
    const workoutName = session.dayName || session.planName || '';
    for (const ex of (session.exercises || [])) {
      (ex.sets || []).forEach((set, idx) => {
        rows.push([
          esc(date),
          esc(workoutName),
          esc(ex.name || ''),
          idx + 1,
          set.weight ?? 0,
          set.reps ?? 0,
          set.rpe ?? '',
          '',
          '',
          esc(set.note || ''),
          '',
        ].join(','));
      });
    }
  }

  const csv = rows.join('\n');
  const today = new Date().toISOString().split('T')[0];
  const filename = `IRONLOG_export_${today}.csv`;
  const path = FileSystem.cacheDirectory + filename;
  await FileSystem.writeAsStringAsync(path, csv, { encoding: FileSystem.EncodingType.UTF8 });

  if (!(await Sharing.isAvailableAsync())) throw new Error('Sharing not available on this device');
  await Sharing.shareAsync(path, { mimeType: 'text/csv', dialogTitle: 'Export IRONLOG Data' });
}
