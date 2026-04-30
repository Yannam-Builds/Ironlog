
import React, { useRef, useState, useEffect } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet, Dimensions,
  FlatList, StatusBar,
} from 'react-native';
import Reanimated, { useSharedValue, withSpring, useAnimatedStyle } from 'react-native-reanimated';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { getSetting, setSetting } from '../db/repositories/settingsRepository';
import { useTheme } from '../context/ThemeContext';
import { ONBOARDING_TEMPLATE_ID } from '../data/programTemplates';
import { APP_VERSION_LABEL } from '../platform/appInfo';

const { width, height } = Dimensions.get('window');

const SLIDES = [
  {
    id: 'welcome',
    icon: 'barbell-outline',
    title: 'WELCOME TO\nIronlogDB',
    body: 'The no-nonsense workout tracker built for lifters who mean business.',
    accent: true,
  },
  {
    id: 'program',
    icon: 'list-outline',
    title: 'PICK A\nPROGRAM',
    body: 'Start with the featured Full Body Beginner plan, browse by category, or build your own from scratch.',
  },
  {
    id: 'workout',
    icon: 'flash-outline',
    title: 'START\nWORKING OUT',
    body: 'Log sets with one tap, track PRs automatically, and get rest timer reminders between every set.',
  },
  {
    id: 'recovery',
    icon: 'body-outline',
    title: 'TRACK\nRECOVERY',
    body: 'See which muscles you\'ve trained this week on an interactive body map. Know when to push and when to rest.',
  },
  {
    id: 'setup',
    icon: 'shield-checkmark-outline',
    title: 'SET UP\nSMART FEATURES',
    body: `IronlogDB ${APP_VERSION_LABEL} adds smart notifications, encrypted local backups, and optional Google Drive backup. We'll guide you through the setup after onboarding.`,
  },
  {
    id: 'ready',
    icon: 'trophy-outline',
    title: "YOU'RE\nREADY",
    body: 'Every rep counts. Every session builds. Start your first workout and watch the progress unfold.',
    isLast: true,
  },
];

export default function OnboardingScreen({ navigation }) {
  const completeOnboarding = async () => { await setSetting('onboarding_complete', true, 'boolean'); };
  const colors = useTheme();
  const flatRef = useRef(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [goalDays, setGoalDays] = useState(4);

  const dot0 = useSharedValue(20);
  const dot1 = useSharedValue(6);
  const dot2 = useSharedValue(6);
  const dot3 = useSharedValue(6);
  const dot4 = useSharedValue(6);
  const dot5 = useSharedValue(6);
  const dotWidths = [dot0, dot1, dot2, dot3, dot4, dot5];

  const dotStyle0 = useAnimatedStyle(() => ({ width: dot0.value }));
  const dotStyle1 = useAnimatedStyle(() => ({ width: dot1.value }));
  const dotStyle2 = useAnimatedStyle(() => ({ width: dot2.value }));
  const dotStyle3 = useAnimatedStyle(() => ({ width: dot3.value }));
  const dotStyle4 = useAnimatedStyle(() => ({ width: dot4.value }));
  const dotStyle5 = useAnimatedStyle(() => ({ width: dot5.value }));
  const dotStyles = [dotStyle0, dotStyle1, dotStyle2, dotStyle3, dotStyle4, dotStyle5];

  useEffect(() => {
    dotWidths.forEach((sv, i) => {
      sv.value = withSpring(i === currentIndex ? 20 : 6, { stiffness: 300, damping: 24 });
    });
  }, [currentIndex]);

  const finish = async ({ openStarterProgram = false } = {}) => {
    await completeOnboarding();
    const current = (await getSetting('ironlog_settings')) || {};
    await setSetting('ironlog_settings', { ...current, weeklyGoalDays: goalDays }, 'json');
    if (openStarterProgram) {
      navigation.reset({
        index: 1,
        routes: [
          { name: 'Tabs' },
          {
            name: 'ProgramPicker',
            params: {
              fromOnboarding: true,
              recommendedTemplateId: ONBOARDING_TEMPLATE_ID,
              autoOpenRecommended: true,
            },
          },
        ],
      });
      return;
    }
    navigation.replace('Tabs');
  };

  const next = () => {
    if (currentIndex < SLIDES.length - 1) {
      flatRef.current?.scrollToIndex({ index: currentIndex + 1, animated: true });
    } else {
      finish();
    }
  };

  const renderSlide = ({ item, index }) => (
    <View style={[s.slide, { width }]}>
      <View style={[s.iconCircle, { backgroundColor: colors.accentSoft, borderColor: colors.accentBorder }]}>
        <Ionicons name={item.icon} size={56} color={colors.accent} />
      </View>
      <Text style={[s.title, { color: colors.text }]}>{item.title}</Text>
      <Text style={[s.body, { color: colors.subtext }]}>{item.body}</Text>
    </View>
  );

  return (
    <View style={[s.container, { backgroundColor: colors.bg }]}>
      <StatusBar barStyle="light-content" />

      {/* Skip button */}
      <TouchableOpacity style={s.skipBtn} onPress={finish}>
        <Text style={[s.skipText, { color: colors.muted }]}>SKIP</Text>
      </TouchableOpacity>

      {/* Slides */}
      <FlatList
        ref={flatRef}
        data={SLIDES}
        keyExtractor={item => item.id}
        renderItem={renderSlide}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        scrollEventThrottle={16}
        onMomentumScrollEnd={e => {
          const idx = Math.round(e.nativeEvent.contentOffset.x / width);
          setCurrentIndex(idx);
        }}
        style={{ flex: 1 }}
      />

      {/* Dots */}
      <View style={s.dots}>
        {SLIDES.map((_, i) => (
          <Reanimated.View
            key={i}
            style={[s.dot, dotStyles[i], { backgroundColor: i === currentIndex ? colors.accent : colors.faint }]}
          />
        ))}
      </View>

      {/* CTA Button */}
      <View style={s.goalWrap}>
        <Text style={[s.goalLabel, { color: colors.muted }]}>WEEKLY GOAL WORKOUT DAYS</Text>
        <View style={s.goalRow}>
          {[3, 4, 5, 6].map((days) => {
            const active = goalDays === days;
            return (
              <TouchableOpacity
                key={days}
                style={[
                  s.goalPill,
                  {
                    borderColor: active ? colors.accent : colors.faint,
                    backgroundColor: active ? colors.accentSoft : 'transparent',
                  },
                ]}
                onPress={() => setGoalDays(days)}
                activeOpacity={0.85}
              >
                <Text style={[s.goalText, { color: active ? colors.accent : colors.muted }]}>{days}</Text>
              </TouchableOpacity>
            );
          })}
        </View>
      </View>

      <TouchableOpacity
        style={[s.btn, { backgroundColor: colors.accent }]}
        onPress={next}
        activeOpacity={0.85}>
        <Text style={s.btnText}>
          {currentIndex === SLIDES.length - 1 ? "LET'S GO" : 'NEXT'}
        </Text>
        <Ionicons
          name={currentIndex === SLIDES.length - 1 ? 'flash' : 'arrow-forward'}
          size={18}
          color="#fff"
        />
      </TouchableOpacity>

      {currentIndex === SLIDES.length - 1 && (
        <TouchableOpacity
          style={[s.secondaryBtn, { borderColor: colors.accentBorder, backgroundColor: colors.accentSoft }]}
          onPress={() => finish({ openStarterProgram: true })}
          activeOpacity={0.85}>
          <Text style={[s.secondaryBtnText, { color: colors.accent }]}>PICK STARTER PROGRAM</Text>
        </TouchableOpacity>
      )}

      <TouchableOpacity
        style={[s.restoreBtn, { borderColor: colors.faint }]}
        onPress={() => navigation.navigate('RestoreData')}
        activeOpacity={0.85}>
        <Text style={[s.restoreBtnText, { color: colors.muted }]}>RESTORE PREVIOUS DATA</Text>
      </TouchableOpacity>

      <View style={{ height: 32 }} />
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  skipBtn: { position: 'absolute', top: 52, right: 24, zIndex: 10, padding: 8 },
  skipText: { fontSize: 11, fontWeight: '700', letterSpacing: 2 },
  slide: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 36,
    paddingTop: 54,
  },
  iconCircle: {
    width: 112,
    height: 112,
    borderRadius: 56,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    marginBottom: 34,
  },
  title: {
    fontSize: 34,
    fontWeight: '900',
    letterSpacing: 1.5,
    textAlign: 'center',
    lineHeight: 40,
    marginBottom: 16,
  },
  body: {
    fontSize: 15,
    lineHeight: 23,
    textAlign: 'center',
    fontWeight: '500',
  },
  dots: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 6,
    marginBottom: 24,
  },
  dot: { height: 6, borderRadius: 3 },
  goalWrap: { marginHorizontal: 24, marginBottom: 14, gap: 8 },
  goalLabel: { fontSize: 10, fontWeight: '700', letterSpacing: 1.8, textAlign: 'center' },
  goalRow: { flexDirection: 'row', justifyContent: 'center', gap: 8 },
  goalPill: { minWidth: 44, paddingVertical: 8, borderWidth: 1, borderRadius: 999, alignItems: 'center' },
  goalText: { fontSize: 12, fontWeight: '800', letterSpacing: 0.8 },
  btn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    marginHorizontal: 24,
    paddingVertical: 16,
    borderRadius: 14,
  },
  btnText: { color: '#fff', fontSize: 14, fontWeight: '900', letterSpacing: 3 },
  secondaryBtn: {
    marginTop: 10,
    marginHorizontal: 24,
    paddingVertical: 14,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderRadius: 10,
  },
  secondaryBtnText: { fontSize: 12, fontWeight: '800', letterSpacing: 1.6 },
  restoreBtn: {
    marginTop: 10,
    marginHorizontal: 24,
    paddingVertical: 12,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderRadius: 10,
  },
  restoreBtnText: { fontSize: 11, fontWeight: '700', letterSpacing: 1.4 },
});



