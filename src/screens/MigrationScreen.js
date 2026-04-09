
import React from 'react';
import { View, Text, StyleSheet, ActivityIndicator } from 'react-native';

export default function MigrationScreen({ step, total, message }) {
  return (
    <View style={s.container}>
      <ActivityIndicator size="large" color="#FF4500" style={{ marginBottom: 24 }} />
      <Text style={s.title}>IRONLOG</Text>
      <Text style={s.msg}>{message || 'Updating your data...'}</Text>
      {step > 0 && (
        <Text style={s.step}>Step {step} of {total}</Text>
      )}
      <View style={s.barBg}>
        <View style={[s.barFill, { width: `${(step / total) * 100}%` }]} />
      </View>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808', alignItems: 'center', justifyContent: 'center', padding: 40 },
  title: { fontSize: 32, fontWeight: '900', color: '#f0f0f0', letterSpacing: -1, marginBottom: 8 },
  msg: { fontSize: 13, color: '#666', textAlign: 'center', marginBottom: 8, letterSpacing: 1 },
  step: { fontSize: 11, color: '#444', marginBottom: 20 },
  barBg: { width: '100%', height: 3, backgroundColor: '#111', marginTop: 8 },
  barFill: { height: 3, backgroundColor: '#FF4500' },
});
