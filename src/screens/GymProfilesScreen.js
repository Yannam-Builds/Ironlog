
import React, { useContext, useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, StyleSheet } from 'react-native';
import CustomAlert from '../components/CustomAlert';
import { Ionicons } from '@expo/vector-icons';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';
import { triggerHaptic } from '../services/hapticsEngine';

export default function GymProfilesScreen({ navigation }) {
  const { gymProfiles, activeGymProfileId, saveGymProfiles, setActiveGymProfileId, settings } = useContext(AppContext);
  const colors = useTheme();
  const haptic = settings?.hapticFeedback !== false;

  const [alertConfig, setAlertConfig] = useState(null);

  const deleteProfile = (profile) => {
    if (gymProfiles.length <= 1) {
      setAlertConfig({ title: 'Cannot delete', message: 'You need at least one gym profile.', buttons: [{ text: 'OK', style: 'default' }] });
      return;
    }
    triggerHaptic('destructiveAction', { enabled: haptic }).catch(() => {});
    setAlertConfig({
      title: 'Delete profile?',
      message: profile.name,
      buttons: [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete', style: 'destructive', onPress: () => {
            const updated = gymProfiles.filter(p => p.id !== profile.id);
            if (activeGymProfileId === profile.id) setActiveGymProfileId(updated[0]?.id || null);
            saveGymProfiles(updated);
          },
        },
      ],
    });
  };

  return (
    <View style={[s.container, { backgroundColor: colors.bg }]}>
      <FlatList
        data={gymProfiles}
        keyExtractor={p => p.id}
        contentContainerStyle={{ padding: 16, gap: 10, paddingBottom: 80 }}
        renderItem={({ item: profile }) => {
          const isActive = profile.id === activeGymProfileId;
          return (
            <TouchableOpacity
              style={[s.card, { backgroundColor: colors.card, borderColor: isActive ? colors.accent : colors.cardBorder }]}
              onPress={() => { triggerHaptic('selection', { enabled: haptic }).catch(() => {}); setActiveGymProfileId(profile.id); }}
              activeOpacity={0.8}>
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <Ionicons
                  name={isActive ? 'radio-button-on' : 'radio-button-off'}
                  size={20}
                  color={isActive ? colors.accent : colors.muted}
                  style={{ marginRight: 12 }}
                />
                <View style={{ flex: 1 }}>
                  <Text style={[s.name, { color: colors.text }]}>{profile.name}</Text>
                  <Text style={[s.meta, { color: colors.muted }]}>
                    {profile.barWeight}kg bar · {(profile.plates || []).map(p => p.weight + 'kg').join(', ')}
                  </Text>
                </View>
                {isActive && (
                  <View style={[s.activeBadge, { backgroundColor: colors.accentSoft, borderColor: colors.accentBorder }]}>
                    <Text style={[s.activeBadgeText, { color: colors.accent }]}>ACTIVE</Text>
                  </View>
                )}
              </View>
              <View style={{ flexDirection: 'row', gap: 10, marginTop: 12 }}>
                <TouchableOpacity
                  style={[s.actionBtn, { borderColor: colors.faint }]}
                  onPress={() => navigation.navigate('GymProfileEditor', { profile })}>
                  <Ionicons name="create-outline" size={13} color={colors.muted} />
                  <Text style={[s.actionText, { color: colors.muted }]}>EDIT</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[s.actionBtn, { borderColor: '#CC222233' }]}
                  onPress={() => deleteProfile(profile)}>
                  <Ionicons name="trash-outline" size={13} color="#CC2222" />
                  <Text style={[s.actionText, { color: '#CC2222' }]}>DELETE</Text>
                </TouchableOpacity>
              </View>
            </TouchableOpacity>
          );
        }}
        ListFooterComponent={
          <TouchableOpacity
            style={[s.addBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
            onPress={() => navigation.navigate('GymProfileEditor', { profile: null })}>
            <Ionicons name="add" size={20} color={colors.accent} />
            <Text style={[s.addText, { color: colors.accent }]}>ADD PROFILE</Text>
          </TouchableOpacity>
        }
      />
      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  card: { padding: 16, borderWidth: 1 },
  name: { fontSize: 17, fontWeight: '800' },
  meta: { fontSize: 12, marginTop: 3 },
  activeBadge: { paddingHorizontal: 8, paddingVertical: 3, borderWidth: 1, borderRadius: 3 },
  activeBadgeText: { fontSize: 9, fontWeight: '800', letterSpacing: 1 },
  actionBtn: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 12, paddingVertical: 8, borderWidth: 1 },
  actionText: { fontSize: 10, fontWeight: '700', letterSpacing: 1 },
  addBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, padding: 16, borderWidth: 1, borderStyle: 'dashed', marginTop: 4 },
  addText: { fontSize: 12, fontWeight: '700', letterSpacing: 2 },
});
