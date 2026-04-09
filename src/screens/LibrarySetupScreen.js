
import React from 'react';
import { View, Text, StyleSheet, ActivityIndicator } from 'react-native';

export default function LibrarySetupScreen({ status }) {
  const msg = status === 'offline'
    ? 'Offline — using bundled exercises.\nFull library will download on next launch.'
    : 'Setting up exercise library...';
  return (
    <View style={s.container}>
      <ActivityIndicator size="large" color="#FF4500" style={{ marginBottom: 24 }} />
      <Text style={s.title}>IRONLOG</Text>
      <Text style={s.msg}>{msg}</Text>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808', alignItems: 'center', justifyContent: 'center', padding: 40 },
  title: { fontSize: 32, fontWeight: '900', color: '#f0f0f0', letterSpacing: -1, marginBottom: 8 },
  msg: { fontSize: 13, color: '#666', textAlign: 'center', letterSpacing: 0.5 },
});
