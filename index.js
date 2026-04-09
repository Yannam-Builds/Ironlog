import { registerRootComponent } from 'expo';
import { AppRegistry } from 'react-native';

import App from './App';
import { runHeadlessBackupTask } from './src/services/backupBackgroundTask';

// registerRootComponent calls AppRegistry.registerComponent('main', () => App);
// It also ensures that whether you load the app in Expo Go or in a native build,
// the environment is set up appropriately
registerRootComponent(App);
AppRegistry.registerHeadlessTask('IronlogBackupTask', () => runHeadlessBackupTask);
