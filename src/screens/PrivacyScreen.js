import React from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../context/ThemeContext';

const PRIVACY_POINTS = [
  'Your workout data stays local on this device unless you explicitly enable encrypted backup.',
  'Encrypted backup uploads only structured app data in this release. Progress photos and custom media stay local.',
  'Google Drive backups are written into the app-specific hidden Drive storage area, not your visible Drive folders.',
  'Backups are encrypted before upload using your passphrase. Restoring on a new phone requires that passphrase.',
  'IronLog never silently restores over live data. Every restore shows a preview first and creates a rollback snapshot when possible.',
];

export default function PrivacyScreen() {
  const colors = useTheme();

  return (
    <ScrollView style={[s.container, { backgroundColor: colors.bg }]} contentContainerStyle={{ padding: 20, gap: 16 }}>
      <View style={[s.hero, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.heroTitle, { color: colors.text }]}>LOCAL-FIRST BY DEFAULT</Text>
        <Text style={[s.heroBody, { color: colors.subtext }]}>
          IronLog stores your training history locally first. Cloud backup is optional, encrypted, and only runs after you turn it on.
        </Text>
      </View>

      {PRIVACY_POINTS.map((point) => (
        <View key={point} style={[s.pointCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
          <Text style={[s.pointText, { color: colors.text }]}>{point}</Text>
        </View>
      ))}
    </ScrollView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  hero: { borderWidth: 1, padding: 20, gap: 10 },
  heroTitle: { fontSize: 22, fontWeight: '900', letterSpacing: -0.8 },
  heroBody: { fontSize: 13, lineHeight: 20 },
  pointCard: { borderWidth: 1, padding: 16 },
  pointText: { fontSize: 13, lineHeight: 20 },
});
