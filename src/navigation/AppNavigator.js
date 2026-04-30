
import React, { useCallback, useMemo, useRef, useState } from 'react';
import { useSharedValue } from 'react-native-reanimated';
import {
  ActivityIndicator,
  Platform,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import PagerView from 'react-native-pager-view';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { DefaultTheme, NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import Ionicons from 'react-native-vector-icons/Ionicons';
import useWatermelonAppData from '../hooks/useWatermelonAppData';
import { useTheme } from '../context/ThemeContext';
import { GlassModeProvider } from '../context/GlassModeContext';
import FloatingWorkoutWidget from '../components/FloatingWorkoutWidget';
import FluidTabBar from '../components/FluidTabBar';
import AppScreenHeader from '../components/AppScreenHeader';
import { NAV_PILL_VISUAL_HEIGHT } from '../utils/bottomOverlaySpacing';
import HomeScreen from '../screens/HomeScreen';
import HistoryScreen from '../screens/HistoryScreen';
import StatsScreen from '../screens/StatsScreen';
import SettingsScreen from '../screens/SettingsScreen';
import PlansScreen from '../screens/PlansScreen';
import ActiveWorkoutScreen from '../screens/ActiveWorkoutScreen';
import PlanEditorScreen from '../screens/PlanEditorScreen';
import ExerciseLibraryScreen from '../screens/ExerciseLibraryScreen';
import BodyWeightScreen from '../screens/BodyWeightScreen';
import CreateExerciseScreen from '../screens/CreateExerciseScreen';
import ProgressPhotosScreen from '../screens/ProgressPhotosScreen';
import ProgramPickerScreen from '../screens/ProgramPickerScreen';
import ExerciseProgressScreen from '../screens/ExerciseProgressScreen';
import WorkoutCalendarScreen from '../screens/WorkoutCalendarScreen';
import VolumeAnalyticsScreen from '../screens/VolumeAnalyticsScreen';
import RecoveryMapScreen from '../screens/RecoveryMapScreen';
import ProgramInsightsScreen from '../screens/ProgramInsightsScreen';
import BodyMeasurementsScreen from '../screens/BodyMeasurementsScreen';
import GymProfilesScreen from '../screens/GymProfilesScreen';
import GymProfileEditorScreen from '../screens/GymProfileEditorScreen';
import OnboardingScreen from '../screens/OnboardingScreen';
import BackupCenterScreen from '../screens/BackupCenterScreen';
import PrivacyScreen from '../screens/PrivacyScreen';
import RestoreDataScreen from '../screens/RestoreDataScreen';
import ImportCenterScreen from '../screens/ImportCenterScreen';
import AIPlanScreen from '../screens/AIPlanScreen';
import TrainingIntelligenceScreen from '../screens/TrainingIntelligenceScreen';
import SplashScreen from '../screens/SplashScreen';
import DataPortabilityScreen from '../screens/DataPortabilityScreen';

// ── Tab screen registry ───────────────────────────────────────────────────────

const TAB_SCREENS = [
  { name: 'Home',     component: HomeScreen,     icon: 'barbell',          title: null       },
  { name: 'Plans',    component: PlansScreen,    icon: 'list',             title: 'PLANS'    },
  { name: 'Log',      component: HistoryScreen,  icon: 'time',             title: 'LOG'      },
  { name: 'Stats',    component: StatsScreen,    icon: 'stats-chart',      title: 'STATS'    },
  { name: 'Settings', component: SettingsScreen, icon: 'settings-outline', title: 'SETTINGS' },
];

const TAB_NAMES = new Set(TAB_SCREENS.map(s => s.name));
const TAB_COUNT = TAB_SCREENS.length;

// ── Stack + nav theme ─────────────────────────────────────────────────────────

const Stack      = createNativeStackNavigator();
const STACK_ANIM = 'slide_from_right';
const NAV_THEME  = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    primary:      '#f0f0f0',
    background:   '#0c0c0c',
    card:         '#0c0c0c',
    text:         '#f0f0f0',
    border:       '#1f1f1f',
    notification: '#ff6a00',
  },
};

// ── Tab pager (native ViewPager2 via react-native-pager-view) ─────────────────
//
// Using the native pager rather than a JS/Reanimated solution because:
//  • DraggableFlatList (Plans screen) uses RNGH gestures; a JS pan gesture
//    wrapping the same touch surface fights it and loses.
//  • Native ViewPager2 handles nested-scroller conflicts at the Android level —
//    no JS gesture coordination needed.
//  • All animation runs off the JS thread → zero jitter.

function Tabs({ navigation }) {
  const colors       = useTheme();
  const insets       = useSafeAreaInsets();
  const tabBarHeight = NAV_PILL_VISUAL_HEIGHT + Math.max(insets.bottom, Platform.OS === 'ios' ? 12 : 10) + 4;

  const [tabIndex, setTabIndex] = useState(0);
  const pagerRef = useRef(null);
  const scrollX = useSharedValue(0);

  const goTo = useCallback((idx) => {
    const n = Math.max(0, Math.min(TAB_COUNT - 1, idx));
    pagerRef.current?.setPage(n);
    setTabIndex(n);
  }, []);

  // Intercept navigate('Plans') / navigate('Home') calls from within tab screens
  // so they slide the pager instead of pushing a new stack screen.
  const tabAwareNav = useMemo(() => new Proxy(navigation, {
    get(target, prop) {
      if (prop === 'navigate') {
        return (name, params) => {
          if (TAB_NAMES.has(name)) {
            goTo(TAB_SCREENS.findIndex(s => s.name === name));
          } else {
            target.navigate(name, params);
          }
        };
      }
      const val = target[prop];
      return typeof val === 'function' ? val.bind(target) : val;
    },
  }), [navigation, goTo]);

  // ── Synthetic tab-bar props ──────────────────────────────────────────────
  const routes          = useMemo(() => TAB_SCREENS.map(s => ({ key: s.name, name: s.name })), []);
  const fakeState       = useMemo(() => ({ routes, index: tabIndex }), [routes, tabIndex]);
  const fakeDescriptors = useMemo(() => {
    const d = {};
    TAB_SCREENS.forEach(s => {
      d[s.name] = {
        options: {
          title:                   s.title || s.name,
          tabBarIcon:              ({ color, size }) => <Ionicons name={s.icon} size={size} color={color} />,
          tabBarActiveTintColor:   colors.accent,
          tabBarInactiveTintColor: colors.muted,
        },
      };
    });
    return d;
  }, [colors.accent, colors.muted]);

  const fakeTabNav = useMemo(() => ({
    navigate: (name) => {
      const idx = TAB_SCREENS.findIndex(s => s.name === name);
      if (idx >= 0) goTo(idx);
    },
    emit: () => ({ defaultPrevented: false }),
  }), [goTo]);

  // ── Render ───────────────────────────────────────────────────────────────
  return (
    <View style={[s.tabsRoot, { backgroundColor: colors.bg }]}>
      <PagerView
        ref={pagerRef}
        style={s.pager}
        initialPage={0}
        overdrag={false}
        offscreenPageLimit={1}
        onPageScroll={(e) => {
          scrollX.value = e.nativeEvent.position + e.nativeEvent.offset;
        }}
        onPageSelected={(e) => setTabIndex(e.nativeEvent.position)}
      >
        {TAB_SCREENS.map((screen, idx) => (
          // PagerView requires plain View children with string keys
          <View key={String(idx)} style={s.page}>
            {screen.title ? (
              <AppScreenHeader
                navigation={tabAwareNav}
                options={{ title: screen.title }}
                route={{ name: screen.name }}
                back={null}
              />
            ) : null}
            <screen.component
              navigation={tabAwareNav}
              route={{ key: screen.name, name: screen.name, params: {} }}
            />
          </View>
        ))}
      </PagerView>

      {/* Active-workout floating pill */}
      <View style={[s.widgetAnchor, { bottom: tabBarHeight + 2 }]}>
        <FloatingWorkoutWidget navigation={navigation} />
      </View>

      {/* Custom frosted tab bar */}
      <FluidTabBar
        state={fakeState}
        descriptors={fakeDescriptors}
        navigation={fakeTabNav}
        scrollX={scrollX}
      />
    </View>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const s = StyleSheet.create({
  tabsRoot:     { flex: 1 },
  pager:        { flex: 1 },
  page:         { flex: 1 },
  widgetAnchor: { position: 'absolute', left: 0, right: 0, zIndex: 100 },
});

// ── Stack header helper ───────────────────────────────────────────────────────

const headerOpts = (colors) => ({
  headerShown:             true,
  header:                  (props) => <AppScreenHeader {...props} />,
  headerShadowVisible:     false,
  headerBackTitleVisible:  false,
  statusBarColor:          colors.bg,
  animation:               STACK_ANIM,
});

// ── Themed status bar ─────────────────────────────────────────────────────────

function ThemedStatusBar() {
  const colors  = useTheme();
  const isLight = colors.bg === '#f2f2f7';
  const bgColor = typeof colors.bg === 'string' ? colors.bg : '#000000';
  return (
    <StatusBar
      barStyle={isLight ? 'dark-content' : 'light-content'}
      backgroundColor={bgColor}
      translucent={false}
    />
  );
}

// ── Error boundary ────────────────────────────────────────────────────────────

class AppErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }
  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }
  componentDidCatch(error, info) {
    console.error('[AppErrorBoundary]', error, info);
  }
  render() {
    if (this.state.hasError) {
      return (
        <View style={{ flex: 1, backgroundColor: '#0a0a0a', justifyContent: 'center', alignItems: 'center', padding: 32 }}>
          <Text style={{ color: '#f0f0f0', fontSize: 20, fontWeight: '900', marginBottom: 12 }}>Something went wrong</Text>
          <ScrollView style={{ maxHeight: 200, width: '100%' }}>
            <Text style={{ color: '#888', fontSize: 11, lineHeight: 16 }}>{String(this.state.error)}</Text>
          </ScrollView>
          <TouchableOpacity
            style={{ marginTop: 28, borderWidth: 1, borderColor: '#ff6a00', borderRadius: 10, paddingHorizontal: 24, paddingVertical: 12 }}
            onPress={() => this.setState({ hasError: false, error: null })}
          >
            <Text style={{ color: '#ff6a00', fontWeight: '800' }}>Try Again</Text>
          </TouchableOpacity>
        </View>
      );
    }
    return this.props.children;
  }
}

// ── Root navigator ────────────────────────────────────────────────────────────

export default function AppNavigator() {
  const { initialized, onboardingComplete } = useWatermelonAppData();
  const colors = useTheme();
  const [splashDone, setSplashDone] = React.useState(false);

  if (!splashDone) {
    return <SplashScreen onFinish={() => setSplashDone(true)} />;
  }

  if (!initialized) {
    return (
      <View style={{ flex: 1, backgroundColor: '#0a0a0a', justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator color={colors.accent} size="large" />
      </View>
    );
  }

  const navTheme = {
    ...NAV_THEME,
    colors: {
      ...NAV_THEME.colors,
      primary:      colors.text,
      background:   colors.bg,
      card:         colors.bg,
      text:         colors.text,
      border:       colors.faint,
      notification: colors.accent,
    },
  };

  return (
    <AppErrorBoundary>
    <GlassModeProvider>
      <NavigationContainer theme={navTheme}>
        <ThemedStatusBar />
        <Stack.Navigator
          screenOptions={{
            headerShown:              false,
            animation:                STACK_ANIM,
            gestureEnabled:           true,
            fullScreenGestureEnabled: true,
            freezeOnBlur:             true,
            contentStyle:             { backgroundColor: colors.bg },
          }}
          initialRouteName={onboardingComplete ? 'Tabs' : 'Onboarding'}
        >
          <Stack.Screen name="Onboarding"  component={OnboardingScreen} />
          <Stack.Screen name="Tabs"        component={Tabs} />
          <Stack.Screen
            name="ActiveWorkout"
            component={ActiveWorkoutScreen}
            options={{ gestureEnabled: false, animation: 'fade' }}
          />
          <Stack.Screen name="PlanEditor"           component={PlanEditorScreen}          options={{ ...headerOpts(colors), title: 'EDIT PLAN'         }} />
          <Stack.Screen name="ExerciseLibrary"      component={ExerciseLibraryScreen}     options={{ ...headerOpts(colors), title: 'EXERCISE LIBRARY'  }} />
          <Stack.Screen name="BodyWeight"           component={BodyWeightScreen}          options={{ ...headerOpts(colors), title: 'BODY WEIGHT'        }} />
          <Stack.Screen name="CreateExercise"       component={CreateExerciseScreen}      options={{ ...headerOpts(colors), title: 'CREATE EXERCISE'    }} />
          <Stack.Screen name="ProgressPhotos"       component={ProgressPhotosScreen}      options={{ ...headerOpts(colors), title: 'PROGRESS PHOTOS'    }} />
          <Stack.Screen name="ProgramPicker"        component={ProgramPickerScreen}       options={{ ...headerOpts(colors), title: 'PROGRAMS'           }} />
          <Stack.Screen name="ExerciseProgress"     component={ExerciseProgressScreen}    options={{ ...headerOpts(colors), title: 'PROGRESS'           }} />
          <Stack.Screen name="WorkoutCalendar"      component={WorkoutCalendarScreen}     options={{ ...headerOpts(colors), title: 'CALENDAR'           }} />
          <Stack.Screen name="VolumeAnalytics"      component={VolumeAnalyticsScreen}     options={{ ...headerOpts(colors), title: 'VOLUME ANALYTICS'   }} />
          <Stack.Screen name="RecoveryMap"          component={RecoveryMapScreen}         options={{ ...headerOpts(colors), title: 'MUSCLE RECOVERY'    }} />
          <Stack.Screen name="ProgramInsights"      component={ProgramInsightsScreen}     options={{ ...headerOpts(colors), title: 'PROGRAM INSIGHTS'   }} />
          <Stack.Screen name="BodyMeasurements"     component={BodyMeasurementsScreen}    options={{ ...headerOpts(colors), title: 'BODY TRACKER'       }} />
          <Stack.Screen name="GymProfiles"          component={GymProfilesScreen}         options={{ ...headerOpts(colors), title: 'GYM PROFILES'       }} />
          <Stack.Screen name="GymProfileEditor"     component={GymProfileEditorScreen}    options={{ ...headerOpts(colors), title: 'EDIT PROFILE'       }} />
          <Stack.Screen name="BackupCenter"         component={BackupCenterScreen}        options={{ ...headerOpts(colors), title: 'BACKUP CENTER'      }} />
          <Stack.Screen name="Privacy"              component={PrivacyScreen}             options={{ ...headerOpts(colors), title: 'PRIVACY'            }} />
          <Stack.Screen name="RestoreData"          component={RestoreDataScreen}         options={{ ...headerOpts(colors), title: 'RESTORE DATA'       }} />
          <Stack.Screen name="ImportCenter"         component={ImportCenterScreen}        options={{ ...headerOpts(colors), title: 'IMPORT CENTER'      }} />
          <Stack.Screen name="AIPlan"               component={AIPlanScreen}              options={{ ...headerOpts(colors), title: 'AI PLAN CREATOR'    }} />
          <Stack.Screen name="TrainingIntelligence" component={TrainingIntelligenceScreen} options={{ ...headerOpts(colors), title: 'ATHLETE PROFILE'  }} />
          <Stack.Screen name="DataPortability"       component={DataPortabilityScreen}       options={{ ...headerOpts(colors), title: 'BACKUP & EXPORT'  }} />
        </Stack.Navigator>
      </NavigationContainer>
    </GlassModeProvider>
    </AppErrorBoundary>
  );
}
