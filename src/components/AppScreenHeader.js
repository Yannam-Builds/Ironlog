import React, { useEffect } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import Ionicons from 'react-native-vector-icons/Ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import ReAnimated, { useSharedValue, withTiming, useAnimatedStyle, Easing } from 'react-native-reanimated';
import { useTheme } from '../context/ThemeContext';

const SIDE_WIDTH = 44;

export default function AppScreenHeader({ navigation, options, route, back }) {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const title = String(options?.title || route?.name || '').toUpperCase();
  const rightAction = options?.headerRightAction; // { icon: 'add', onPress: fn }

  const opacity = useSharedValue(0);
  const translateY = useSharedValue(4);
  useEffect(() => {
    opacity.value = withTiming(1, { duration: 240, easing: Easing.bezier(0.33, 1, 0.68, 1) });
    translateY.value = withTiming(0, { duration: 260, easing: Easing.bezier(0.33, 1, 0.68, 1) });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [title]);

  const titleStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [{ translateY: translateY.value }],
  }));

  return (
    <View style={[styles.container, { backgroundColor: colors.bg, borderBottomColor: colors.faint, paddingTop: insets.top + 4 }]}>
      <View style={styles.row}>
        <View style={styles.side}>
          {back ? (
            <TouchableOpacity accessibilityRole="button" accessibilityLabel="Go back"
              hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              onPress={navigation.goBack} style={styles.sideBtn} activeOpacity={0.6}>
              <Ionicons name="chevron-back" size={22} color={colors.text} />
            </TouchableOpacity>
          ) : null}
        </View>

        <ReAnimated.View style={[styles.titleWrap, titleStyle]}>
          <Text numberOfLines={1} style={[styles.title, { color: colors.text }]}>{title}</Text>
        </ReAnimated.View>

        <View style={styles.side}>
          {rightAction ? (
            <TouchableOpacity accessibilityRole="button"
              hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              onPress={rightAction.onPress} style={styles.sideBtn} activeOpacity={0.6}>
              <Ionicons name={rightAction.icon} size={22} color={colors.text} />
            </TouchableOpacity>
          ) : null}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { borderBottomWidth: 1 },
  row: { flexDirection: 'row', alignItems: 'center', minHeight: 56, paddingHorizontal: 10 },
  side: { width: SIDE_WIDTH, justifyContent: 'center', alignItems: 'center' },
  sideBtn: { width: SIDE_WIDTH, height: SIDE_WIDTH, alignItems: 'center', justifyContent: 'center' },
  titleWrap: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 8 },
  title: { fontSize: 14, fontWeight: '900', letterSpacing: 1.6 },
});
