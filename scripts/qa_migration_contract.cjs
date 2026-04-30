const fs = require('fs');
const path = require('path');
const vm = require('vm');

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function loadSQLiteExportModule(filePath) {
  const source = fs.readFileSync(filePath, 'utf8');
  const transformed = source
    .replace(/import \* as FileSystem from '\.\.\/platform\/filesystem';/g, 'const FileSystem = { cacheDirectory: "/tmp/", writeAsStringAsync: async () => undefined, readAsStringAsync: async () => "{}" };')
    .replace(/import \* as Sharing from '\.\.\/platform\/sharing';/g, 'const Sharing = { isAvailableAsync: async () => true, shareAsync: async () => undefined };')
    .replace(/import \* as DocumentPicker from '\.\.\/platform\/documentPicker';/g, 'const DocumentPicker = { getDocumentAsync: async () => ({ canceled: true, assets: [] }) };')
    .replace(/import \{ APP_VERSION \} from '\.\.\/platform\/appInfo';/g, "const APP_VERSION = 'test';")
    .replace(/import AsyncStorage from '@react-native-async-storage\/async-storage';/g, 'const AsyncStorage = {};')
    .replace(/import \{ loadTrainingSnapshot, replaceTrainingSnapshot \} from '\.\.\/domain\/storage\/trainingRepository';/g, 'const loadTrainingSnapshot = async () => ({}); const replaceTrainingSnapshot = async () => {};')
    .replace(/export const /g, 'const ')
    .replace(/export async function /g, 'async function ')
    .replace(/export function /g, 'function ')
    .concat('\nmodule.exports = { SQLITE_EXPORT_SCHEMA, validateSQLiteBundle };');

  const sandbox = { module: { exports: {} }, exports: {}, console };
  new vm.Script(transformed, { filename: filePath }).runInNewContext(sandbox);
  return sandbox.module.exports;
}

function run() {
  const repoRoot = path.join(__dirname, '..');
  const trainingRepositoryPath = path.join(repoRoot, 'src', 'domain', 'storage', 'trainingRepository.js');
  const exportImportPath = path.join(repoRoot, 'src', 'services', 'sqliteExportImport.js');
  const appContextPath = path.join(repoRoot, 'src', 'context', 'AppContext.js');

  const repositorySource = fs.readFileSync(trainingRepositoryPath, 'utf8');
  assert(repositorySource.includes('SQLITE_MIGRATION_MARKER_KEY'), 'Migration marker key usage missing');
  assert(repositorySource.includes('SQLite migration verification failed'), 'Migration verification guard missing');
  assert(repositorySource.includes('counts.plans !== plans.length'), 'Plan count migration check missing');
  assert(repositorySource.includes('counts.sessions !== history.length'), 'Session count migration check missing');
  assert(repositorySource.includes('counts.bodyWeight !== bodyWeight.length'), 'Bodyweight count migration check missing');

  const appContextSource = fs.readFileSync(appContextPath, 'utf8');
  assert(appContextSource.includes('migrateLegacyAsyncStorageToSQLite'), 'App boot migration call missing');

  const exportModule = loadSQLiteExportModule(exportImportPath);
  assert(exportModule.SQLITE_EXPORT_SCHEMA === 'IRONLOG_SQLITE_EXPORT_V1', 'SQLite export schema mismatch');

  const invalid = exportModule.validateSQLiteBundle({ schema: 'BAD', payload: {} });
  assert(invalid.valid === false, 'Invalid schema should fail validation');

  const valid = exportModule.validateSQLiteBundle({
    schema: 'IRONLOG_SQLITE_EXPORT_V1',
    payload: { plans: [], history: [], bodyWeight: [], bodyMeasurements: [], customExercises: [] },
    appState: { '@ironlog/notificationSettings': { enabled: true, notificationProfile: 'balanced' } },
    counts: { plans: 0, history: 0, bodyWeight: 0, bodyMeasurements: 0, customExercises: 0 },
  });
  assert(valid.valid === true, 'Valid bundle should pass validation');
  assert(valid.appStateKeys === 1, 'Expected appState key count in validation output');

  console.log('PASS: migration and export/import contract checks completed');
}

try {
  run();
} catch (error) {
  console.error(`FAIL: ${error.message}`);
  process.exit(1);
}
