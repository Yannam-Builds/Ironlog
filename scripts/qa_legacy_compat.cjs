const fs = require('fs');
const path = require('path');

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function read(file) {
  return fs.readFileSync(file, 'utf8');
}

function run() {
  const root = path.join(__dirname, '..');
  const exerciseLibraryService = path.join(root, 'src', 'services', 'ExerciseLibraryService.js');
  const googleDriveService = path.join(root, 'src', 'services', 'googleDriveService.js');

  const exerciseSource = read(exerciseLibraryService);
  const driveSource = read(googleDriveService);

  assert(
    exerciseSource.includes('LEGACY_COMPAT: historical ids kept for upgrade/import continuity.'),
    'Missing LEGACY_COMPAT marker in ExerciseLibraryService aliases block'
  );
  assert(
    exerciseSource.includes('LEGACY_EXERCISE_ID_ALIASES'),
    'Legacy exercise id aliases block missing'
  );

  assert(
    driveSource.includes('LEGACY_COMPAT: keep EXPO_PUBLIC_* env fallbacks'),
    'Missing LEGACY_COMPAT marker in googleDriveService env fallback block'
  );
  assert(
    driveSource.includes('IRONLOG_GOOGLE_DRIVE_ANDROID_CLIENT_ID'),
    'Primary bare RN env identifier missing in googleDriveService'
  );

  console.log('PASS: legacy compatibility markers and boundaries are in place');
}

try {
  run();
} catch (error) {
  console.error(`FAIL: ${error.message}`);
  process.exit(1);
}
