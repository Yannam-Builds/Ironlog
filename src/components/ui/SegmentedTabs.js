import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useTheme } from '../../context/ThemeContext';
import { RADIUS } from '../../utils/themes';

export default function SegmentedTabs({ items = [], value, onChange, compact = false, disabledIds = [] }) {
  const colors = useTheme();
  const disabledSet = new Set(disabledIds || []);

  return (
    <View style={[s.row, compact ? s.rowCompact : null]}>
      {items.map((item) => {
        const id = item?.id ?? item;
        const label = item?.label ?? String(item || '');
        const active = id === value;
        const disabled = disabledSet.has(id) || item?.disabled;
        return (
          <TouchableOpacity
            key={String(id)}
            style={[
              s.tab,
              compact ? s.tabCompact : null,
              {
                borderColor: active ? colors.accent : colors.faint,
                backgroundColor: active ? colors.accentSoft : colors.card,
                opacity: disabled ? 0.45 : 1,
              },
            ]}
            disabled={disabled}
            onPress={() => onChange?.(id)}
            activeOpacity={0.85}
          >
            <Text
              numberOfLines={1}
              adjustsFontSizeToFit
              minimumFontScale={0.78}
              style={[s.tabText, compact ? s.tabTextCompact : null, { color: active ? colors.accent : colors.subtext }]}>
              {String(label).toUpperCase()}
            </Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const s = StyleSheet.create({
  row: {
    flexDirection: 'row',
    gap: 10,
  },
  rowCompact: {
    gap: 8,
  },
  tab: {
    flex: 1,
    borderWidth: 1,
    borderRadius: RADIUS.md,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 8,
  },
  tabCompact: {
    minHeight: 40,
    borderRadius: RADIUS.md,
  },
  tabText: {
    fontSize: 10.5,
    fontWeight: '800',
    letterSpacing: 0.9,
    textAlign: 'center',
  },
  tabTextCompact: {
    fontSize: 10,
    letterSpacing: 0.7,
  },
});
