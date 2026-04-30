const fs = require('fs');
const path = require('path');
const vm = require('vm');

function loadScheduler(filePath) {
  const source = fs.readFileSync(filePath, 'utf8');
  const transformed = source
    .replace(
      /import notifee,\s*\{[^}]+\}\s*from '@notifee\/react-native';/,
      "const AndroidImportance = { DEFAULT: 3, LOW: 2 }; const AndroidCategory = { STOPWATCH: 'stopwatch' }; const TriggerType = { TIMESTAMP: 'timestamp' }; const AuthorizationStatus = { AUTHORIZED: 1, PROVISIONAL: 2 }; const notifee = { getNotificationSettings: async () => ({ authorizationStatus: AuthorizationStatus.AUTHORIZED }), requestPermission: async () => ({ authorizationStatus: AuthorizationStatus.AUTHORIZED }), createChannel: async () => 'mock_channel', createTriggerNotification: async () => 'mock_notification_id', displayNotification: async () => 'mock_display_id', cancelNotification: async () => undefined };"
    )
    .replace(/export async function /g, 'async function ')
    .replace(/export function /g, 'function ')
    .concat('\nmodule.exports = { buildDefaultNotificationCandidates, chooseNotificationCandidate, scheduleSmartNotification };');

  const sandbox = { module: { exports: {} }, exports: {}, console, Date, Math };
  const script = new vm.Script(transformed, { filename: filePath });
  script.runInNewContext(sandbox);
  return sandbox.module.exports;
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function isoOffsetDays(days) {
  return new Date(Date.now() + (days * 86400000)).toISOString();
}

async function run() {
  const filePath = path.join(__dirname, '..', 'src', 'services', 'notificationScheduler.js');
  const scheduler = loadScheduler(filePath);

  const baseSettings = {
    enabled: true,
    notificationProfile: 'balanced',
    weeklyCapMode: 'fixed_7',
    maxNotificationsPerWeekOverride: null,
    trainingReminders: true,
    milestoneNotifications: true,
    planAdherenceAlerts: true,
    recoverySuggestions: true,
    backupAlerts: true,
    quietHoursStart: 22,
    quietHoursEnd: 8,
    cooldownHours: 12,
    reminderLeadMinutes: 90,
    decisionLog: [],
  };

  // 1) Balanced cap: max 1/day
  {
    const settings = {
      ...baseSettings,
      decisionLog: [{ at: new Date().toISOString(), key: 'train_reminder_plan_aware', topic: 'training', outcome: 'sent' }],
    };
    const choice = scheduler.chooseNotificationCandidate({
      settings,
      candidates: [{ key: 'streak_preserve', topic: 'streak', score: 88 }],
      activePlan: { days: [{}, {}, {}] },
    });
    assert(choice.reason === 'daily_cap', 'Expected daily_cap suppression in balanced profile');
  }

  // 2) Balanced cap: max 3/week
  {
    const settings = {
      ...baseSettings,
      decisionLog: [
        { at: isoOffsetDays(-1), key: 'a', topic: 'training', outcome: 'sent' },
        { at: isoOffsetDays(-2), key: 'b', topic: 'streak', outcome: 'sent' },
        { at: isoOffsetDays(-3), key: 'c', topic: 'bodyweight', outcome: 'sent' },
      ],
    };
    const choice = scheduler.chooseNotificationCandidate({
      settings,
      candidates: [{ key: 'recovery_suggestion', topic: 'recovery', score: 70 }],
      activePlan: { days: [{}, {}, {}] },
    });
    assert(choice.reason === 'weekly_cap', 'Expected weekly_cap suppression in balanced profile');
  }

  // 3) Topic cooldown suppression
  {
    const settings = {
      ...baseSettings,
      maxNotificationsPerDayOverride: 2,
      decisionLog: [{ at: new Date(Date.now() - (2 * 3600000)).toISOString(), key: 'streak_preserve', topic: 'streak', outcome: 'sent' }],
    };
    const choice = scheduler.chooseNotificationCandidate({
      settings,
      candidates: [{ key: 'streak_preserve', topic: 'streak', score: 90 }],
      activePlan: { days: [{}, {}, {}] },
    });
    assert(choice.reason === 'cooldown_or_topic_gate', `Expected topic cooldown gate, got ${JSON.stringify(choice)}`);
  }

  // 4) Candidate arbitration picks highest score
  {
    const choice = scheduler.chooseNotificationCandidate({
      settings: { ...baseSettings, decisionLog: [] },
      candidates: [
        { key: 'low', topic: 'bodyweight', score: 30 },
        { key: 'high', topic: 'training', score: 90 },
      ],
      activePlan: { days: [{}, {}, {}, {}] },
    });
    assert(choice.candidate?.key === 'high', 'Expected highest-score candidate to win');
  }

  // 5) Bodyweight actioned suppression in candidate generation
  {
    const candidates = scheduler.buildDefaultNotificationCandidates({
      settings: baseSettings,
      workoutsLast7d: 2,
      bodyweightLoggingConsistency: 20,
      recoverySuggestions: ['Recover'],
      streaks: { training: { current: 1 } },
      newMilestones: [],
      history: [],
      bodyWeightEntries: [{ date: new Date().toISOString(), weight: 82 }],
      backupStatus: { enabled: true, dirty: false },
      activePlan: { days: [{}, {}, {}] },
    });
    const hasBodyWeightReminder = candidates.some((item) => item.key === 'bw_reminder');
    assert(!hasBodyWeightReminder, 'Expected bodyweight reminder suppression when already logged today');
  }

  // 6) Schedule emits sent decision and returns notification id
  {
    let updated = null;
    const scheduled = await scheduler.scheduleSmartNotification({
      settings: { ...baseSettings, decisionLog: [] },
      updateSettings: async (next) => {
        updated = next;
        return next;
      },
      candidates: [{ key: 'train_reminder_plan_aware', topic: 'training', score: 100, title: 'T', body: 'B', triggerDate: new Date(Date.now() + 3600000) }],
      activePlan: { days: [{}, {}, {}] },
    });
    assert(!!scheduled?.notificationId, 'Expected scheduled notification id');
    assert(Array.isArray(updated?.decisionLog) && updated.decisionLog[0]?.outcome === 'sent', 'Expected sent decision log');
  }

  // 7) Already-actioned suppression should win when topic already completed today
  {
    const choice = scheduler.chooseNotificationCandidate({
      settings: { ...baseSettings, decisionLog: [] },
      candidates: [{ key: 'train_reminder_plan_aware', topic: 'training', score: 100 }],
      activePlan: { days: [{}, {}, {}] },
      alreadyActionedTopics: ['training'],
    });
    assert(choice.reason === 'already_actioned', `Expected already_actioned, got ${JSON.stringify(choice)}`);
  }

  console.log('PASS: notification policy QA checks completed');
}

run().catch((error) => {
  console.error(`FAIL: ${error.message}`);
  process.exit(1);
});
