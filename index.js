import { AppRegistry } from 'react-native';
import App from './App';
import { runHeadlessBackupTask } from './src/services/backupBackgroundTask';

AppRegistry.registerComponent('main', () => App);
AppRegistry.registerHeadlessTask('IronlogBackupTask', () => runHeadlessBackupTask);
