
import React, { useEffect, useState } from 'react';
import { View, Image, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { getImageUri } from '../services/ExerciseImageCache';
import { useTheme } from '../context/ThemeContext';

export default function CachedExerciseImage({ imagePath, style, iconSize = 32 }) {
  const colors = useTheme();
  const [uri, setUri] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!imagePath) return;
    let cancelled = false;
    getImageUri(imagePath).then(u => {
      if (!cancelled) { if (u) setUri(u); else setFailed(true); }
    });
    return () => { cancelled = true; };
  }, [imagePath]);

  if (!imagePath || failed) return (
    <View style={[s.placeholder, { backgroundColor: colors.card }, style]}>
      <Ionicons name="barbell-outline" size={iconSize} color={colors.muted} />
    </View>
  );

  if (!uri) return (
    <View style={[s.placeholder, { backgroundColor: colors.card }, style]} />
  );

  return <Image source={{ uri }} style={[s.img, style]} resizeMode="cover" />;
}

const s = StyleSheet.create({
  placeholder: { alignItems: 'center', justifyContent: 'center' },
  img: {},
});
