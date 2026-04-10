import React, { useState, useEffect, useCallback } from 'react';
import { StatusBar } from 'expo-status-bar';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { enableFreeze, enableScreens } from 'react-native-screens';
import { AppContextProvider } from './src/context/AppContext';
import { ThemeProvider } from './src/context/ThemeContext';
import { ActiveWorkoutBannerProvider } from './src/context/ActiveWorkoutBannerContext';
import AppNavigator from './src/navigation/AppNavigator';
import { runMigrations } from './src/services/migrations';
import { initExerciseLibrary } from './src/services/ExerciseLibraryService';
import MigrationScreen from './src/screens/MigrationScreen';
import LibrarySetupScreen from './src/screens/LibrarySetupScreen';
import { ensureTrainingDatabase } from './src/domain/storage/trainingDatabase';
import { migrateLegacyAsyncStorageToSQLite, seedExerciseIntelligence } from './src/domain/storage/trainingRepository';

enableScreens(true);
enableFreeze(true);

export default function App() {
  const [migrationStep, setMigrationStep] = useState(null);
  const [libraryStatus, setLibraryStatus] = useState(null);
  const [ready, setReady] = useState(false);
  const [startupError, setStartupError] = useState(null);
  const [bootAttempt, setBootAttempt] = useState(0);

  const bootstrap = useCallback(async () => {
    setReady(false);
    setStartupError(null);
    setMigrationStep(null);
    setLibraryStatus(null);

    try {
      await runMigrations((step, total) => setMigrationStep({ step, total }));
      setMigrationStep(null);
      const libraryIndex = await initExerciseLibrary((status) => setLibraryStatus(status));
      await ensureTrainingDatabase();
      await migrateLegacyAsyncStorageToSQLite();
      await seedExerciseIntelligence(libraryIndex || []);
    } catch (e) {
      console.warn('Startup error:', e);
      setStartupError(e);
    } finally {
      setReady(true);
    }
  }, []);

  useEffect(() => {
    (async () => {
      await bootstrap();
    })();
  }, [bootAttempt, bootstrap]);

  if (migrationStep) {
    return (
      <MigrationScreen
        step={migrationStep.step}
        total={migrationStep.total}
        message="Upgrading your data..."
      />
    );
  }

  if (!ready) {
    return <LibrarySetupScreen status={libraryStatus || 'setting_up'} />;
  }

  if (startupError) {
    return (
      <View style={s.errorScreen}>
        <Text style={s.errorTitle}>IRONLOG</Text>
        <Text style={s.errorHeading}>Startup failed</Text>
        <Text style={s.errorMessage}>
          {startupError?.message || 'Something went wrong while loading your data.'}
        </Text>
        <TouchableOpacity style={s.retryButton} onPress={() => setBootAttempt((attempt) => attempt + 1)}>
          <Text style={s.retryButtonText}>TRY AGAIN</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <AppContextProvider>
        <ThemeProvider>
          <ActiveWorkoutBannerProvider>
            <StatusBar style="light" backgroundColor="#080808" />
            <AppNavigator />
          </ActiveWorkoutBannerProvider>
        </ThemeProvider>
      </AppContextProvider>
    </GestureHandlerRootView>
  );
}

const s = StyleSheet.create({
  errorScreen: {
    flex: 1,
    backgroundColor: '#080808',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
  },
  errorTitle: {
    fontSize: 32,
    fontWeight: '900',
    color: '#f0f0f0',
    letterSpacing: -1,
    marginBottom: 8,
  },
  errorHeading: {
    fontSize: 16,
    fontWeight: '800',
    color: '#FF4500',
    letterSpacing: 2,
    marginBottom: 12,
  },
  errorMessage: {
    fontSize: 13,
    color: '#999',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 24,
  },
  retryButton: {
    paddingHorizontal: 24,
    paddingVertical: 14,
    backgroundColor: '#FF4500',
  },
  retryButtonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 2,
  },
});
