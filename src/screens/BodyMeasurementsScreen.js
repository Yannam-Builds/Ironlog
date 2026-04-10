import React, { useContext, useState, useEffect } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  TextInput,
  StyleSheet,
  Modal,
  Dimensions,
} from 'react-native';
import CustomAlert from '../components/CustomAlert';
import Svg, { Polyline, Line, Circle, Text as SvgText } from 'react-native-svg';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { AppContext } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';
import { triggerHaptic } from '../services/hapticsEngine';
import { convertUnitToKg, formatWeightFromKg } from '../utils/weightUnits';

const STORAGE_KEY = '@ironlog/bodyMeasurements';
const SCREEN_WIDTH = Dimensions.get('window').width;
const MINI_CHART_W = (SCREEN_WIDTH - 48) / 2 - 16;
const MINI_CHART_H = 60;

const MEASUREMENT_FIELDS = [
  { key: 'chest',     label: 'Chest',     unit: 'cm' },
  { key: 'waist',     label: 'Waist',     unit: 'cm' },
  { key: 'hips',      label: 'Hips',      unit: 'cm' },
  { key: 'shoulders', label: 'Shoulders', unit: 'cm' },
  { key: 'neck',      label: 'Neck',      unit: 'cm' },
  { key: 'arms',      label: 'Arms',      unit: 'cm' },
  { key: 'thighs',    label: 'Thighs',    unit: 'cm' },
  { key: 'calves',    label: 'Calves',    unit: 'cm' },
  { key: 'bodyFat',   label: 'Body Fat',  unit: '%'  },
];

function MiniChart({ data, colors }) {
  if (!data || data.length < 2) return null;
  const W = MINI_CHART_W;
  const H = MINI_CHART_H;
  const PAD = 6;
  const vals = data.map(d => d.value);
  const minV = Math.min(...vals);
  const maxV = Math.max(...vals);
  const range = maxV - minV || 1;
  const pts = data.map((d, i) => {
    const x = PAD + (i / (data.length - 1)) * (W - PAD * 2);
    const y = H - PAD - ((d.value - minV) / range) * (H - PAD * 2);
    return `${x},${y}`;
  }).join(' ');
  const last = data[data.length - 1];
  const lx = W - PAD;
  const ly = H - PAD - ((last.value - minV) / range) * (H - PAD * 2);
  return (
    <Svg width={W} height={H}>
      <Polyline points={pts} fill="none" stroke={colors.accent} strokeWidth={1.5} />
      <Circle cx={lx} cy={ly} r={3} fill={colors.accent} />
    </Svg>
  );
}

export default function BodyMeasurementsScreen() {
  const { bodyWeight, logBodyWeight, settings } = useContext(AppContext);
  const colors = useTheme();
  const haptic = settings?.hapticFeedback !== false;
  const weightUnit = settings?.weightUnit || 'kg';

  const [activeTab, setActiveTab] = useState('WEIGHT');
  const [weightInput, setWeightInput] = useState('');
  const [measurements, setMeasurements] = useState([]);
  const [modalVisible, setModalVisible] = useState(false);
  const [formValues, setFormValues] = useState({});
  const [alertConfig, setAlertConfig] = useState(null);

  // Load measurements from AsyncStorage
  useEffect(() => {
    (async () => {
      try {
        const raw = await AsyncStorage.getItem(STORAGE_KEY);
        if (raw) setMeasurements(JSON.parse(raw));
      } catch (e) {
        console.warn('Failed to load body measurements', e);
      }
    })();
  }, []);

  const saveMeasurements = async (updated) => {
    try {
      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
    } catch (e) {
      console.warn('Failed to save body measurements', e);
    }
  };

  // ── WEIGHT TAB ──────────────────────────────────────────────────────────────

  const logWeight = () => {
    const inputWeight = parseFloat(weightInput);
    const w = convertUnitToKg(inputWeight, weightUnit, 2);
    if (!inputWeight || w < 20 || w > 300) {
      triggerHaptic('invalidAction', { enabled: haptic }).catch(() => {});
      const min = formatWeightFromKg(20, weightUnit);
      const max = formatWeightFromKg(300, weightUnit);
      setAlertConfig({ title: 'Invalid weight', message: `Enter a value between ${min} and ${max}.`, buttons: [{ text: 'OK', style: 'default' }] });
      return;
    }
    logBodyWeight({ date: new Date().toISOString(), weight: w });
    setWeightInput('');
    triggerHaptic('success', { enabled: haptic }).catch(() => {});
  };

  const recentWeights = bodyWeight.slice(0, 30).reverse();
  const lastWeek = bodyWeight[7];
  const delta =
    lastWeek && bodyWeight[0]
      ? (bodyWeight[0].weight - lastWeek.weight).toFixed(1)
      : null;

  const buildWeightChart = () => {
    if (recentWeights.length < 2) return null;
    const W = SCREEN_WIDTH - 40;
    const H = 120;
    const PAD = 20;
    const weights = recentWeights.map(e => e.weight);
    const minW = Math.min(...weights) - 1;
    const maxW = Math.max(...weights) + 1;
    const range = maxW - minW || 1;
    const pts = recentWeights.map((e, i) => {
      const x = PAD + (i / (recentWeights.length - 1)) * (W - PAD * 2);
      const y = H - PAD - ((e.weight - minW) / range) * (H - PAD * 2);
      return `${x},${y}`;
    }).join(' ');
    const last = recentWeights[recentWeights.length - 1];
    const lx = W - PAD;
    const ly = H - PAD - ((last.weight - minW) / range) * (H - PAD * 2);
    return (
      <Svg width={W} height={H} style={{ marginVertical: 8 }}>
        <Line x1={PAD} y1={H - PAD} x2={W - PAD} y2={H - PAD} stroke={colors.faint} strokeWidth={1} />
        <Polyline points={pts} fill="none" stroke={colors.accent} strokeWidth={2} />
        <Circle cx={lx} cy={ly} r={4} fill={colors.accent} />
        <SvgText x={lx} y={ly - 8} textAnchor="middle" fill={colors.text} fontSize={11}>
          {formatWeightFromKg(last.weight, weightUnit)}
        </SvgText>
      </Svg>
    );
  };

  // ── MEASUREMENTS TAB ────────────────────────────────────────────────────────

  const openModal = () => {
    setFormValues({});
    setModalVisible(true);
  };

  const saveMeasurementEntry = async () => {
    const hasAny = MEASUREMENT_FIELDS.some(f => formValues[f.key] && formValues[f.key].trim() !== '');
    if (!hasAny) {
      triggerHaptic('invalidAction', { enabled: haptic }).catch(() => {});
      setAlertConfig({ title: 'No data', message: 'Enter at least one measurement value.', buttons: [{ text: 'OK', style: 'default' }] });
      return;
    }
    const entry = { date: new Date().toISOString() };
    MEASUREMENT_FIELDS.forEach(f => {
      const v = parseFloat(formValues[f.key]);
      if (!isNaN(v) && v > 0) entry[f.key] = v;
    });
    const updated = [entry, ...measurements];
    setMeasurements(updated);
    await saveMeasurements(updated);
    setModalVisible(false);
    triggerHaptic('success', { enabled: haptic }).catch(() => {});
  };

  const getMiniChartData = (fieldKey) => {
    return measurements
      .filter(m => m[fieldKey] != null)
      .slice(0, 20)
      .reverse()
      .map(m => ({ value: m[fieldKey], date: m.date }));
  };

  const getCurrentAndDelta = (fieldKey) => {
    const entries = measurements.filter(m => m[fieldKey] != null);
    if (!entries.length) return { current: null, delta: null };
    const current = entries[0][fieldKey];
    const prev = entries[1] ? entries[1][fieldKey] : null;
    const diff = prev != null ? (current - prev).toFixed(1) : null;
    return { current, delta: diff };
  };

  const renderMeasurementCard = (field) => {
    const { current, delta } = getCurrentAndDelta(field.key);
    const chartData = getMiniChartData(field.key);
    const isEmpty = current == null;
    return (
      <View
        key={field.key}
        style={[s.measureCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}
      >
        <Text style={[s.measureLabel, { color: colors.muted }]}>{field.label.toUpperCase()}</Text>
        {isEmpty ? (
          <Text style={[s.measureEmpty, { color: colors.faint }]}>—</Text>
        ) : (
          <>
            <View style={s.measureValueRow}>
              <Text style={[s.measureValue, { color: colors.text }]}>
                {current}
                <Text style={[s.measureUnit, { color: colors.subtext }]}> {field.unit}</Text>
              </Text>
              {delta !== null && (
                <Text style={[
                  s.measureDelta,
                  { color: getDeltaColor(field.key, parseFloat(delta)) },
                ]}>
                  {parseFloat(delta) > 0 ? '+' : ''}{delta}
                </Text>
              )}
            </View>
            {chartData.length >= 2 && (
              <MiniChart data={chartData} colors={colors} />
            )}
          </>
        )}
      </View>
    );
  };

  // For most measurements smaller delta is neutral/good; waist/hips going down is positive.
  // For body fat, smaller is better. We use a neutral color scheme: green = decrease, red = increase
  // except for muscles (chest, shoulders, arms, thighs, calves) where increase = good.
  const INCREASE_GOOD = new Set(['chest', 'shoulders', 'arms', 'thighs', 'calves']);
  const getDeltaColor = (key, diff) => {
    if (diff === 0) return colors.subtext;
    const bigger = diff > 0;
    const good = INCREASE_GOOD.has(key) ? bigger : !bigger;
    return good ? '#00C170' : '#FF4500';
  };

  // ── RENDER ──────────────────────────────────────────────────────────────────

  return (
    <View style={[s.root, { backgroundColor: colors.bg }]}>
      {/* Tab bar */}
      <View style={[s.tabBar, { borderBottomColor: colors.faint, backgroundColor: colors.bg }]}>
        {['WEIGHT', 'MEASUREMENTS'].map(tab => (
          <TouchableOpacity
            key={tab}
            style={[s.tabBtn, activeTab === tab && { borderBottomColor: colors.accent, borderBottomWidth: 2 }]}
            onPress={() => { setActiveTab(tab); triggerHaptic('selection', { enabled: haptic }).catch(() => {}); }}
          >
            <Text style={[
              s.tabText,
              { color: activeTab === tab ? colors.accent : colors.muted },
            ]}>
              {tab}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* Weight tab */}
      {activeTab === 'WEIGHT' && (
        <ScrollView contentContainerStyle={s.tabContent}>
          {/* Input card */}
          <View style={[s.card, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
            <Text style={[s.sectionLabel, { color: colors.muted }]}>{`TODAY'S WEIGHT (${String(weightUnit).toUpperCase()})`}</Text>
            <View style={s.inputRow}>
              <TextInput
                style={[s.weightInput, { color: colors.text, borderBottomColor: colors.accent }]}
                value={weightInput}
                onChangeText={setWeightInput}
                keyboardType="decimal-pad"
                placeholder="0.0"
                placeholderTextColor={colors.muted}
              />
              <TouchableOpacity style={[s.logBtn, { backgroundColor: colors.accent }]} onPress={logWeight}>
                <Text style={s.logBtnText}>LOG</Text>
              </TouchableOpacity>
            </View>
            {delta !== null && (
              <Text style={[s.deltaText, { color: parseFloat(delta) <= 0 ? '#00C170' : '#FF4500' }]}>
                {parseFloat(delta) > 0 ? '+' : ''}{formatWeightFromKg(Math.abs(Number(delta)), weightUnit)} vs last week
              </Text>
            )}
          </View>

          {/* Chart */}
          {recentWeights.length >= 2 && (
            <View style={[s.card, s.chartCard, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
              <Text style={[s.sectionLabel, { color: colors.muted }]}>LAST 30 DAYS</Text>
              {buildWeightChart()}
            </View>
          )}

          {/* History list */}
          {bodyWeight.slice(0, 14).map((e, i) => (
            <View key={i} style={[s.histRow, { borderBottomColor: colors.faint }]}>
              <Text style={[s.histDate, { color: colors.subtext }]}>
                {new Date(e.date).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })}
              </Text>
              <Text style={[s.histWeight, { color: colors.text }]}>{formatWeightFromKg(e.weight, weightUnit)}</Text>
            </View>
          ))}
        </ScrollView>
      )}

      {/* Measurements tab */}
      {activeTab === 'MEASUREMENTS' && (
        <ScrollView contentContainerStyle={s.tabContent}>
          <TouchableOpacity
            style={[s.addBtn, { backgroundColor: colors.accent }]}
            onPress={openModal}
          >
            <Text style={s.addBtnText}>+ ADD MEASUREMENT</Text>
          </TouchableOpacity>

          {/* 2-column grid */}
          <View style={s.grid}>
            {MEASUREMENT_FIELDS.map(f => renderMeasurementCard(f))}
          </View>

          {/* Last logged date */}
          {measurements.length > 0 && (
            <Text style={[s.lastLogged, { color: colors.muted }]}>
              Last logged:{' '}
              {new Date(measurements[0].date).toLocaleDateString('en-GB', {
                day: '2-digit', month: 'short', year: 'numeric',
              })}
            </Text>
          )}
        </ScrollView>
      )}

      {/* Add Measurement Modal */}
      <Modal
        visible={modalVisible}
        animationType="slide"
        transparent
        onRequestClose={() => setModalVisible(false)}
      >
        <View style={s.modalOverlay}>
          <View style={[s.modalSheet, { backgroundColor: colors.card }]}>
            <View style={[s.modalHeader, { borderBottomColor: colors.faint }]}>
              <Text style={[s.modalTitle, { color: colors.text }]}>ADD MEASUREMENT</Text>
              <TouchableOpacity onPress={() => setModalVisible(false)}>
                <Text style={[s.modalClose, { color: colors.muted }]}>✕</Text>
              </TouchableOpacity>
            </View>

            <ScrollView contentContainerStyle={s.modalBody} keyboardShouldPersistTaps="handled">
              {MEASUREMENT_FIELDS.map(f => (
                <View key={f.key} style={[s.modalField, { borderBottomColor: colors.faint }]}>
                  <Text style={[s.modalFieldLabel, { color: colors.subtext }]}>
                    {f.label} ({f.unit})
                  </Text>
                  <TextInput
                    style={[s.modalInput, { color: colors.text, borderBottomColor: colors.accent }]}
                    value={formValues[f.key] || ''}
                    onChangeText={v => setFormValues(prev => ({ ...prev, [f.key]: v }))}
                    keyboardType="decimal-pad"
                    placeholder="—"
                    placeholderTextColor={colors.faint}
                  />
                </View>
              ))}

              <TouchableOpacity
                style={[s.saveBtn, { backgroundColor: colors.accent }]}
                onPress={saveMeasurementEntry}
              >
                <Text style={s.saveBtnText}>SAVE</Text>
              </TouchableOpacity>
            </ScrollView>
          </View>
        </View>
      </Modal>
      <CustomAlert visible={!!alertConfig} title={alertConfig?.title} message={alertConfig?.message} buttons={alertConfig?.buttons || []} onDismiss={() => setAlertConfig(null)} />
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1 },

  // Tab bar
  tabBar: {
    flexDirection: 'row',
    borderBottomWidth: 1,
  },
  tabBtn: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 14,
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  tabText: {
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 2,
  },

  // Shared content padding
  tabContent: {
    padding: 16,
    paddingBottom: 48,
  },

  // Weight tab
  card: {
    padding: 20,
    borderWidth: 1,
    marginBottom: 16,
  },
  chartCard: {
    alignItems: 'center',
  },
  sectionLabel: {
    fontSize: 10,
    letterSpacing: 3,
    marginBottom: 12,
  },
  inputRow: {
    flexDirection: 'row',
    gap: 12,
    alignItems: 'center',
  },
  weightInput: {
    flex: 1,
    fontSize: 36,
    fontWeight: '900',
    borderBottomWidth: 2,
    paddingVertical: 6,
  },
  logBtn: {
    paddingHorizontal: 24,
    paddingVertical: 16,
  },
  logBtnText: {
    color: '#fff',
    fontWeight: '800',
    fontSize: 13,
    letterSpacing: 2,
  },
  deltaText: {
    fontSize: 13,
    marginTop: 8,
  },
  histRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  histDate: {
    fontSize: 14,
  },
  histWeight: {
    fontSize: 18,
    fontWeight: '700',
  },

  // Measurements tab
  addBtn: {
    paddingVertical: 14,
    alignItems: 'center',
    marginBottom: 20,
  },
  addBtnText: {
    color: '#fff',
    fontWeight: '800',
    fontSize: 13,
    letterSpacing: 2,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  measureCard: {
    width: (SCREEN_WIDTH - 48) / 2,
    padding: 12,
    borderWidth: 1,
    minHeight: 90,
  },
  measureLabel: {
    fontSize: 9,
    letterSpacing: 2,
    marginBottom: 6,
  },
  measureEmpty: {
    fontSize: 22,
    fontWeight: '300',
    marginTop: 4,
  },
  measureValueRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 6,
    marginBottom: 6,
  },
  measureValue: {
    fontSize: 20,
    fontWeight: '800',
  },
  measureUnit: {
    fontSize: 12,
    fontWeight: '400',
  },
  measureDelta: {
    fontSize: 12,
    fontWeight: '600',
  },
  lastLogged: {
    fontSize: 11,
    textAlign: 'center',
    marginTop: 20,
    letterSpacing: 1,
  },

  // Modal
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.65)',
    justifyContent: 'flex-end',
  },
  modalSheet: {
    maxHeight: '90%',
    borderTopLeftRadius: 0,
    borderTopRightRadius: 0,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
  },
  modalTitle: {
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 3,
  },
  modalClose: {
    fontSize: 18,
    paddingHorizontal: 4,
  },
  modalBody: {
    padding: 20,
    paddingBottom: 40,
  },
  modalField: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  modalFieldLabel: {
    fontSize: 14,
    flex: 1,
  },
  modalInput: {
    fontSize: 18,
    fontWeight: '700',
    borderBottomWidth: 1.5,
    minWidth: 80,
    textAlign: 'right',
    paddingVertical: 4,
  },
  saveBtn: {
    marginTop: 28,
    paddingVertical: 16,
    alignItems: 'center',
  },
  saveBtnText: {
    color: '#fff',
    fontWeight: '800',
    fontSize: 13,
    letterSpacing: 2,
  },
});
