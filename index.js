import { AppRegistry } from 'react-native';
import notifee, { EventType } from '@notifee/react-native';
import App from './App';
import { runHeadlessBackupTask } from './src/services/backupBackgroundTask';
import { handleNotificationAction } from './src/services/notificationScheduler';

// Background notification action handler — runs in headless JS when app is killed/backgrounded.
notifee.onBackgroundEvent(async ({ type, detail }) => {
  if (type === EventType.ACTION_PRESS && detail?.pressAction?.id) {
    await handleNotificationAction(detail.pressAction.id, detail, { isForeground: false });
  }
});

AppRegistry.registerComponent('main', () => App);
AppRegistry.registerHeadlessTask('IronlogBackupTask', () => runHeadlessBackupTask);
