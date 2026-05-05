
import React, { useRef, useState, useEffect } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet, Dimensions,
  FlatList, StatusBar,
} from 'react-native';
import Reanimated, { useSharedValue, withSpring, useAnimatedStyle } from 'react-native-reanimated';
import Ionicons from 'react-native-vector-icons/Ionicons';
import Svg, { Path } from 'react-native-svg';
import { getSetting, setSetting } from '../db/repositories/settingsRepository';
import { useTheme } from '../context/ThemeContext';
import { ONBOARDING_TEMPLATE_ID } from '../data/programTemplates';
import { APP_VERSION_LABEL } from '../platform/appInfo';
import { textOnColor } from '../utils/colorUtils';

// White variant of the IronLog logo rendered from the SVG path (viewBox 1254×1254)
function IronLogLogo({ size = 140, color = '#FFFFFF' }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 1254 1254">
      <Path
        fillRule="evenodd"
        fill={color}
        d="M568.5 206L581 205.5L593 210.5L598.5 216L603.5 226L603.5 299L595.5 316L576 330.5L388 450.5L381.5 457L376.5 469L376.5 776L379.5 784L387 792.5L440 825.5L447.5 835L451.5 847L450.5 926L442 937.5L435 940.5L427 940.5L286 853.5L274.5 842L268.5 828L267.5 418L274.5 398L284 387.5L388 321.5L560 208.5L568.5 206ZM675.5 206L687 205.5L701 210.5L970 385.5L978.5 394L984.5 405L986.5 413L986.5 824L981 829.5L974 830.5L959 822.5L886 777.5L879.5 768L879.5 469L876.5 460L870 451.5L678 328.5L658.5 313L653.5 304L651.5 295L651.5 232L656.5 218L664 210.5L675.5 206ZM575.5 387L585 386.5L592 389.5L599.5 397L602.5 404L602.5 1012L598.5 1021L591 1028.5L584 1031.5L572 1031.5L567 1029.5L501 985.5L492.5 976L487.5 964L487.5 462L494.5 445L502 436.5L568 389.5L575.5 387ZM670.5 387L680 386.5L687 389.5L753 435.5L762.5 447L766.5 458L766.5 835L768.5 840L776 845.5L784 844.5L855 798.5L861 795.5L870 795.5L953 844.5L957.5 850L957.5 858L953 863.5L697 1028.5L685 1032.5L674 1032.5L663 1027.5L655.5 1019L651.5 1007L651.5 409L656.5 396L663 389.5L670.5 387Z"
      />
    </Svg>
  );
}

const { width, height } = Dimensions.get('window');

const SLIDES = [
  {
    id: 'welcome',
    icon: 'barbell-outline',
    title: 'WELCOME TO\nIronlog',
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
    body: `Ironlog ${APP_VERSION_LABEL} adds smart notifications, encrypted local backups, and manual export/restore. We'll guide you through the setup after onboarding.`,
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
  const primaryTextColor = textOnColor(colors.accent, colors.textOnAccent);
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

  const renderSlide = ({ item }) => (
    <View style={[s.slide, { width }]}>
      {item.id === 'welcome' ? (
        <View style={s.logoWrap}>
          <IronLogLogo size={148} color="#FFFFFF" />
        </View>
      ) : (
        <View style={[s.iconCircle, { backgroundColor: colors.accentSoft, borderColor: colors.accentBorder }]}>
          <Ionicons name={item.icon} size={56} color={colors.accent} />
        </View>
      )}
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
        <Text style={[s.btnText, { color: primaryTextColor }]}>
          {currentIndex === SLIDES.length - 1 ? "LET'S GO" : 'NEXT'}
        </Text>
        <Ionicons
          name={currentIndex === SLIDES.length - 1 ? 'flash' : 'arrow-forward'}
          size={18}
          color={primaryTextColor}
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
  logoWrap: {
    width: 148,
    height: 148,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 34,
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
  btnText: { fontSize: 14, fontWeight: '900', letterSpacing: 3 },
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



