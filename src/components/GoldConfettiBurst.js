import React, { memo, useCallback, useEffect, useMemo, useState } from 'react';
import { Dimensions, StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';

const GOLD = ['#FFD700', '#F6C34A', '#FFB84D', '#E6B800'];
const MULTICOLOR = ['#FFD700', '#F6C34A', '#FFB84D', '#E6B800', '#00C170', '#FF4500', '#0080FF', '#8A5CFF'];

function rand(min, max) {
  return min + Math.random() * (max - min);
}

function hexAlpha(hex, a) {
  if (typeof hex !== 'string' || !hex.startsWith('#')) return hex;
  return `${hex}${Math.round(a * 255).toString(16).padStart(2, '0')}`;
}

function buildPalette({ colors, colorMode, themeColors }) {
  if (Array.isArray(colors) && colors.length) return colors;
  if (colorMode === 'theme' && themeColors) {
    const accent = typeof themeColors.accent === 'string' && themeColors.accent.startsWith('#')
      ? themeColors.accent : '#FF4500';
    // Shimmery palette: accent variants + white glint + gold shimmer
    return [
      accent,
      hexAlpha(accent, 0.85),
      hexAlpha(accent, 0.60),
      '#FFD700',
      '#FFFFFF',
      accent,
      hexAlpha(accent, 0.75),
      '#FFD700',
    ];
  }
  if (colorMode === 'multicolor') return MULTICOLOR;
  return GOLD;
}

function resolveCoord(value, size, fallbackRatio) {
  if (Number.isFinite(value)) {
    if (value >= 0 && value <= 1) return size * value;
    return value;
  }
  return size * fallbackRatio;
}

function buildParticles(count, variant, dims, origin = null, durationScale = 1.2) {
  const { width, height } = dims;
  const originX = resolveCoord(origin?.x, width, 0.5);
  const originY = resolveCoord(
    origin?.y,
    height,
    variant === 'fountain' ? 0.88 : variant === 'wave' ? 0.2 : variant === 'fireworks' ? 0.34 : 0.7
  );
  const minDuration = Math.round(
    (variant === 'fountain' ? 1950 : variant === 'fireworks' ? 1800 : variant === 'wave' ? 1550 : 1750) * durationScale
  );
  const maxDuration = Math.round(
    (variant === 'fountain' ? 3200 : variant === 'fireworks' ? 3050 : variant === 'wave' ? 2500 : 2850) * durationScale
  );
  const particles = [];
  const resolvedCount = count;
  for (let i = 0; i < resolvedCount; i += 1) {
    const wide = Math.random() > 0.5;
    const pieceWidth = wide ? Math.round(rand(8, 14)) : Math.round(rand(5, 8));
    const pieceHeight = wide ? Math.round(rand(4, 7)) : Math.round(rand(10, 16));
    const base = {
      key: `p_${variant}_${i}_${Math.random().toString(16).slice(2)}`,
      width: pieceWidth,
      height: pieceHeight,
      delayMs: Math.round(rand(0, variant === 'wave' ? 380 : variant === 'fireworks' ? 280 : 220)),
      durationMs: Math.round(rand(minDuration, maxDuration)),
      spinA: rand(-180, 180),
      spinB: rand(200, 520),
      flipFreq: rand(1.8, 4.2),   // flips per second — drives scaleX oscillation
      flipPhase: rand(0, Math.PI * 2), // random starting phase so pieces are out of sync
      round: Math.random() > 0.85 ? 999 : 0,
      path: 'fall',
      wind: rand(-26, 26),
    };

    if (variant === 'burst') {
      const angle = rand(-Math.PI, Math.PI);
      const speed = rand(280, 560);
      particles.push({
        ...base,
        delayMs: Math.round(rand(0, 100)),
        durationMs: Math.round(rand(1400, 2100) * durationScale),
        left: originX + rand(-10, 10),
        top: originY + rand(-10, 10),
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed,
        gravity: rand(900, 1200),
        drag: rand(0.984, 0.991),
        sway: rand(-10, 10),
        path: 'burst',
      });
      continue;
    }

    if (variant === 'fountain') {
      particles.push({
        ...base,
        left: originX + rand(-60, 60),
        top: originY,
        vx: rand(-200, 200),
        vy: rand(-700, -480),
        gravity: rand(1400, 1850),
        drag: rand(0.985, 0.992),
        sway: rand(-12, 12),
        path: 'arc',
      });
      continue;
    }

    if (variant === 'fireworks') {
      const angle = rand(-Math.PI * 0.92, -Math.PI * 0.08);
      const speed = rand(240, 520);
      const ring = rand(0, 1) < 0.5;
      particles.push({
        ...base,
        left: originX + Math.cos(angle) * rand(0, 18) + rand(-10, 10),
        top: originY + Math.sin(angle) * rand(0, 18) + rand(-10, 10),
        vx: Math.cos(angle) * speed * (ring ? 0.84 : 1),
        vy: Math.sin(angle) * speed,
        gravity: rand(1150, 1450),
        drag: rand(0.986, 0.993),
        sway: rand(-12, 12),
        delayMs: base.delayMs + Math.round(rand(0, 120)),
      });
      continue;
    }

    if (variant === 'wave') {
      particles.push({
        ...base,
        left: (width * i) / Math.max(count - 1, 1) + rand(-20, 20),
        top: -pieceHeight,
        vx: rand(-100, 100),
        vy: rand(60, 160),
        gravity: rand(1100, 1350),
        drag: rand(0.987, 0.994),
        sway: rand(-16, 16),
        delayMs: Math.round((i / Math.max(count, 1)) * 420),
      });
      continue;
    }

    const angle = rand(-Math.PI * 0.95, -Math.PI * 0.05);
    const speed = rand(240, 620);
    particles.push({
      ...base,
      left: originX + rand(-28, 28),
      top: originY + rand(-12, 12),
      vx: Math.cos(angle) * speed + rand(-60, 60),
      vy: Math.sin(angle) * speed,
      gravity: rand(1180, 1600),
      drag: rand(0.984, 0.992),
      sway: rand(-14, 14),
    });
  }
  return particles;
}

const ConfettiParticle = memo(function ConfettiParticle({ particle, color }) {
  const progress = useSharedValue(0);

  useEffect(() => {
    progress.value = 0;
    progress.value = withDelay(
      particle.delayMs,
      withTiming(1, {
        duration: particle.durationMs,
        easing: Easing.linear,
      })
    );
  }, [particle.delayMs, particle.durationMs, progress]);

  const animatedStyle = useAnimatedStyle(() => {
    const p = progress.value;
    const time = p * 1.25;
    const drag = particle.drag || 0.989;
    const vx = (particle.vx || 0) * Math.pow(drag, time * 60);
    const vy = (particle.vy || 0) + (particle.gravity || 1350) * time * 0.72;
    const sway = Math.sin(time * Math.PI * 2.3) * (particle.sway || particle.wind || 0);
    const drift = Math.cos(time * Math.PI * 1.4) * (particle.sway || particle.wind || 0) * 0.4;

    let translateX = vx * time * 0.82 + sway + drift;
    let translateY = vy * time * 0.5;
    if (particle.path === 'arc' || particle.path === 'burst') {
      translateX = vx * time * 0.88 + sway;
      translateY = (particle.vy || 0) * time * 0.5 + (particle.gravity || 1350) * time * time * 0.2;
    }

    const rotate = `${particle.spinA + particle.spinB * p}deg`;
    const scale = p < 0.14 ? 0.58 + p * 2.1 : p > 0.84 ? 1 - (p - 0.84) * 0.45 : 1;
    const opacity = p < 0.07 ? p / 0.07 : p > 0.72 ? Math.max(0, 1 - (p - 0.72) / 0.28) : 1;
    // 3-D flutter: cos oscillates piece between face-on (1) and edge-on (0) and back (-1 = face-on flipped)
    const scaleX = Math.cos(time * Math.PI * 2 * (particle.flipFreq || 2.5) + (particle.flipPhase || 0));

    return {
      opacity,
      transform: [{ translateX }, { translateY }, { rotate }, { scale }, { scaleX }],
    };
  });

  return (
      <Animated.View
      style={[
        styles.particle,
        {
          left: particle.left,
          top: particle.top,
          width: particle.width,
          height: particle.height,
          borderRadius: particle.round,
          backgroundColor: color,
        },
        animatedStyle,
      ]}
    />
  );
});

export default function GoldConfettiBurst({
  enabled = true,
  count = 60,
  colors,
  colorMode = 'gold',
  variant = 'burst',
  themeColors = null,
  origin = null,
  durationScale = 1.0,
  onDone,
  fullScreen = false,
  style = null,
}) {
  const [visible, setVisible] = useState(Boolean(enabled));
  const [layoutDims, setLayoutDims] = useState(null);
  const screen = Dimensions.get('window');
  const dims = useMemo(
    () => layoutDims || {
      width: screen.width || 360,
      height: fullScreen ? (screen.height || 800) : 360,
    },
    [fullScreen, layoutDims, screen.height, screen.width]
  );
  const palette = useMemo(() => buildPalette({ colors, colorMode, themeColors }), [colors, colorMode, themeColors]);
  const particles = useMemo(
    () => buildParticles(count, variant, dims, origin, durationScale),
    [count, dims, durationScale, origin, variant]
  );

  const handleLayout = useCallback((event) => {
    const { width, height } = event?.nativeEvent?.layout || {};
    if (!width || !height) return;
    setLayoutDims((current) => {
      if (current && Math.abs(current.width - width) < 2 && Math.abs(current.height - height) < 2) {
        return current;
      }
      return { width, height };
    });
  }, []);

  useEffect(() => {
    if (!enabled) {
      setVisible(false);
      return undefined;
    }
    setVisible(true);
    const maxMs = particles.reduce((max, p) => Math.max(max, p.delayMs + p.durationMs), 0) + 60;
    const timer = setTimeout(() => {
      setVisible(false);
      if (typeof onDone === 'function') onDone();
    }, maxMs);
    return () => clearTimeout(timer);
  }, [enabled, onDone, particles]);

  if (!visible) return null;

  return (
    <View pointerEvents="none" onLayout={handleLayout} style={[fullScreen ? styles.full : styles.root, style]}>
      {particles.map((particle, index) => (
        <ConfettiParticle
          key={particle.key}
          particle={particle}
          color={palette[index % palette.length]}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  full: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 200,
  },
  root: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    height: 360,
    overflow: 'hidden',
    zIndex: 50,
  },
  particle: {
    position: 'absolute',
  },
});
