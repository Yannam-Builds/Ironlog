/**
 * IronLog Animated Splash Screen
 * --------------------------------
 * Usage:
 *   1. Copy this file into src/screens/SplashScreen.js
 *   2. Copy assets/logo_iron.png and assets/logo_log.png into your
 *      app's assets folder (e.g. assets/images/)
 *   3. Update the two require() paths below to match.
 *   4. In your AppNavigator (or App.js), show this screen first,
 *      then call onFinish() to navigate into the app once ready.
 *
 * Props:
 *   onFinish  — called after animation completes (~2.4s)
 */

import React, { useEffect, useRef } from 'react';
import {
  Animated,
  Dimensions,
  Easing,
  Image,
  StyleSheet,
  View,
} from 'react-native';

// ── Update these paths to wherever you place the logo pieces ──────────────────
const IRON_IMG = require('../../assets/images/logo_iron.png');
const LOG_IMG  = require('../../assets/images/logo_log.png');
// ─────────────────────────────────────────────────────────────────────────────

// Original image dimensions (do not change)
const IRON_W = 330, LOG_W = 265, LOGO_H = 85, TOTAL_W = 595;

export default function SplashScreen({ onFinish }) {
  const { width: SW } = Dimensions.get('window');

  // Scale logo to 52% of screen width
  const scale     = (SW * 0.52) / TOTAL_W;
  const ironW     = IRON_W * scale;
  const logW      = LOG_W  * scale;
  const logoH     = LOGO_H * scale;

  // ── Animated values ───────────────────────────────────────────────────────
  const ironX     = useRef(new Animated.Value(-SW)).current;
  const logX      = useRef(new Animated.Value(SW)).current;
  const shakeX        = useRef(new Animated.Value(0)).current;
  const dot1Opacity   = useRef(new Animated.Value(0.15)).current;
  const dot2Opacity   = useRef(new Animated.Value(0.15)).current;
  const dot3Opacity   = useRef(new Animated.Value(0.15)).current;
  const dotsVisible   = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const CRASH_T = 700; // ms until pieces collide

    // 1. Pieces crash in
    const slide = Animated.parallel([
      Animated.timing(ironX, {
        toValue: 0,
        duration: CRASH_T,
        delay: 250,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }),
      Animated.timing(logX, {
        toValue: 0,
        duration: CRASH_T,
        delay: 250,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }),
    ]);

    // 2. Shake on impact
    const shake = Animated.sequence([
      Animated.timing(shakeX, { toValue: -5, duration: 50, useNativeDriver: true }),
      Animated.timing(shakeX, { toValue:  5, duration: 60, useNativeDriver: true }),
      Animated.timing(shakeX, { toValue: -3, duration: 50, useNativeDriver: true }),
      Animated.timing(shakeX, { toValue:  2, duration: 50, useNativeDriver: true }),
      Animated.timing(shakeX, { toValue:  0, duration: 40, useNativeDriver: true }),
    ]);

    // 3. Show dots, then loop pulse through them
    const showDots = Animated.timing(dotsVisible, {
      toValue: 1, duration: 400, delay: 200, useNativeDriver: true,
    });

    const pulseDot = (anim, delay) =>
      Animated.loop(
        Animated.sequence([
          Animated.delay(delay),
          Animated.timing(anim, { toValue: 1,    duration: 200, useNativeDriver: true }),
          Animated.timing(anim, { toValue: 0.15, duration: 500, useNativeDriver: true }),
          Animated.delay(1400 - delay - 700),
        ])
      );

    // ── Sequence ────────────────────────────────────────────────────────────
    Animated.sequence([
      slide,
      shake,
      Animated.parallel([
        showDots,
        pulseDot(dot1Opacity, 0),
        pulseDot(dot2Opacity, 220),
        pulseDot(dot3Opacity, 440),
      ]),
    ]).start();

    // Call onFinish after a minimum display time (adjust to your load time)
    // You should call onFinish() yourself once data is ready, not on a timer.
    // This timer is just a fallback demo.
    const timer = setTimeout(() => onFinish?.(), 3000);
    return () => clearTimeout(timer);
  }, []);

  return (
    <View style={styles.container}>

      {/* Logo */}
      <Animated.View style={[styles.logo, { transform: [{ translateX: shakeX }] }]}>
        <Animated.Image
          source={IRON_IMG}
          style={[{ width: ironW, height: logoH }, { transform: [{ translateX: ironX }] }]}
          resizeMode="contain"
        />
        <Animated.Image
          source={LOG_IMG}
          style={[{ width: logW, height: logoH }, { transform: [{ translateX: logX }] }]}
          resizeMode="contain"
        />
      </Animated.View>

      {/* Loading dots */}
      <Animated.View style={[styles.dotsRow, { opacity: dotsVisible }]}>
        <Animated.View style={[styles.dot, { opacity: dot1Opacity }]} />
        <Animated.View style={[styles.dot, { opacity: dot2Opacity }]} />
        <Animated.View style={[styles.dot, { opacity: dot3Opacity }]} />
      </Animated.View>

    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0a0a0a',
    alignItems: 'center',
    justifyContent: 'center',
  },
  logo: {
    flexDirection: 'row',
    alignItems: 'center',
    overflow: 'hidden',
  },
  dotsRow: {
    position: 'absolute',
    bottom: 52,
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
  },
  dot: {
    width: 5,
    height: 5,
    borderRadius: 3,
    backgroundColor: '#e63800',
  },
});
