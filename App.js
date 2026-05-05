import React, { useState, useEffect, useCallback } from 'react';
import { AppState, View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { enableFreeze, enableScreens } from 'react-native-screens';
import ThemedToast from './src/components/ui/ThemedToast';
import { KeyboardProvider } from 'react-native-keyboard-controller';
import { BottomSheetModalProvider } from '@gorhom/bottom-sheet';
import BootSplash from 'react-native-bootsplash';
import { ThemeProvider } from './src/context/ThemeContext';
import { ActiveWorkoutBannerProvider } from './src/context/ActiveWorkoutBannerContext';
import AppNavigator from './src/navigation/AppNavigator';
import LibrarySetupScreen from './src/screens/LibrarySetupScreen';
import { registerForegroundNotifHandler, handleNotificationAction } from './src/services/notificationScheduler';
import { consumePendingAction } from './src/services/workoutNotificationBridge';
import { navigationRef } from './src/navigation/navigationRef';
// WatermelonDB bootstrap — database singleton is created on import
import './src/db/database';
import { seedExercisesIfNeeded, backfillExerciseMusclesIfNeeded } from './src/db/repositories/exerciseRepository';

enableScreens(true);
enableFreeze(true);

export default function App() {
  const [ready, setReady] = useState(false);
  const [startupError, setStartupError] = useState(null);
  const [bootAttempt, setBootAttempt] = useState(0);

  const bootstrap = useCallback(async () => {
    setReady(false);
    setStartupError(null);

    try {
      // Seed built-in exercise library into WatermelonDB on first launch.
      // This is idempotent — subsequent launches skip seeding via the
      // 'exercise_seed_v1_complete' marker in app_settings.
      await seedExercisesIfNeeded();
      // Backfill exercise_muscles rows for users who installed before
      // muscle rows were written during seeding. No-op if already populated.
      await backfillExerciseMusclesIfNeeded();
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

  // Register foreground notification action handler once on mount.
  useEffect(() => {
    const unsubscribe = registerForegroundNotifHandler();
    return () => { if (typeof unsubscribe === 'function') unsubscribe(); };
  }, []);

  // Handle pending notification actions that were stored while app was backgrounded.
  // This fires when the user taps a background notification and the app resumes.
  useEffect(() => {
    const sub = AppState.addEventListener('change', async (nextState) => {
      if (nextState === 'active') {
        const pending = await consumePendingAction();
        if (pending?.actionId) {
          handleNotificationAction(pending.actionId, pending, { isForeground: true });
        }
      }
    });
    return () => sub.remove();
  }, []);

  useEffect(() => {
    if (ready) {
      BootSplash.hide({ fade: true }).catch(() => {});
    }
  }, [ready]);

  let content = null;

  if (!ready) {
    content = <LibrarySetupScreen status="setting_up" />;
  } else if (startupError) {
    content = (
      <View style={s.errorScreen}>
        <Text style={s.errorTitle}>Ironlog</Text>
        <Text style={s.errorHeading}>Startup failed</Text>
        <Text style={s.errorMessage}>
          {startupError?.message || 'Something went wrong while loading your data.'}
        </Text>
        <TouchableOpacity style={s.retryButton} onPress={() => setBootAttempt((attempt) => attempt + 1)}>
          <Text style={s.retryButtonText}>TRY AGAIN</Text>
        </TouchableOpacity>
      </View>
    );
  } else {
    content = (
      <KeyboardProvider>
        <BottomSheetModalProvider>
          <ThemeProvider>
            <ActiveWorkoutBannerProvider>
              <AppNavigator />
              <ThemedToast />
            </ActiveWorkoutBannerProvider>
          </ThemeProvider>
        </BottomSheetModalProvider>
      </KeyboardProvider>
    );
  }

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        {content}
      </SafeAreaProvider>
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
