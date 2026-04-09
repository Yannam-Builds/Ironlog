import AsyncStorage from '@react-native-async-storage/async-storage';
import * as FileSystem from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import * as DocumentPicker from 'expo-document-picker';

const KEYS = {
  PLANS: 'ironlog_plans',
  HISTORY: 'ironlog_history',
  PRS: 'ironlog_prs',
  SETTINGS: 'ironlog_settings',
  BODY_WEIGHT: 'ironlog_bodyweight',
  INITIALIZED: 'ironlog_initialized',
};

export { KEYS };

// ── Plans ──────────────────────────────────────────────────────────────────
export async function savePlans(plans) {
  try {
    await AsyncStorage.setItem(KEYS.PLANS, JSON.stringify(plans));
  } catch (e) {
    console.error('savePlans error', e);
  }
}

export async function loadPlans() {
  try {
    const raw = await AsyncStorage.getItem(KEYS.PLANS);
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    console.error('loadPlans error', e);
    return [];
  }
}

// ── History ────────────────────────────────────────────────────────────────
export async function saveHistory(history) {
  try {
    await AsyncStorage.setItem(KEYS.HISTORY, JSON.stringify(history));
  } catch (e) {
    console.error('saveHistory error', e);
  }
}

export async function loadHistory() {
  try {
    const raw = await AsyncStorage.getItem(KEYS.HISTORY);
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    console.error('loadHistory error', e);
    return [];
  }
}

// ── PRs ────────────────────────────────────────────────────────────────────
export async function savePRs(prs) {
  try {
    await AsyncStorage.setItem(KEYS.PRS, JSON.stringify(prs));
  } catch (e) {
    console.error('savePRs error', e);
  }
}

export async function loadPRs() {
  try {
    const raw = await AsyncStorage.getItem(KEYS.PRS);
    return raw ? JSON.parse(raw) : {};
  } catch (e) {
    console.error('loadPRs error', e);
    return {};
  }
}

// ── Settings ───────────────────────────────────────────────────────────────
export async function saveSettings(settings) {
  try {
    await AsyncStorage.setItem(KEYS.SETTINGS, JSON.stringify(settings));
  } catch (e) {
    console.error('saveSettings error', e);
  }
}

export async function loadSettings() {
  try {
    const raw = await AsyncStorage.getItem(KEYS.SETTINGS);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    console.error('loadSettings error', e);
    return null;
  }
}

// ── Body Weight ────────────────────────────────────────────────────────────
export async function saveBodyWeight(entries) {
  try {
    await AsyncStorage.setItem(KEYS.BODY_WEIGHT, JSON.stringify(entries));
  } catch (e) {
    console.error('saveBodyWeight error', e);
  }
}

export async function loadBodyWeight() {
  try {
    const raw = await AsyncStorage.getItem(KEYS.BODY_WEIGHT);
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    console.error('loadBodyWeight error', e);
    return [];
  }
}

// ── Export / Import ────────────────────────────────────────────────────────
export async function exportAllData() {
  try {
    const [plans, history, prs, settings, bodyWeight] = await Promise.all([
      loadPlans(),
      loadHistory(),
      loadPRs(),
      loadSettings(),
      loadBodyWeight(),
    ]);

    const data = {
      exportedAt: new Date().toISOString(),
      version: 1,
      plans,
      history,
      prs,
      settings,
      bodyWeight,
    };

    const json = JSON.stringify(data, null, 2);
    const fileName = `ironlog_backup_${new Date().toISOString().split('T')[0]}.json`;
    const fileUri = FileSystem.documentDirectory + fileName;

    await FileSystem.writeAsStringAsync(fileUri, json, {
      encoding: FileSystem.EncodingType.UTF8,
    });

    const canShare = await Sharing.isAvailableAsync();
    if (canShare) {
      await Sharing.shareAsync(fileUri, {
        mimeType: 'application/json',
        dialogTitle: 'Export IRONLOG Backup',
      });
    }

    return { success: true, fileUri };
  } catch (e) {
    console.error('exportAllData error', e);
    return { success: false, error: e.message };
  }
}

export async function importAllData() {
  try {
    const result = await DocumentPicker.getDocumentAsync({
      type: 'application/json',
      copyToCacheDirectory: true,
    });

    if (result.canceled) {
      return { success: false, canceled: true };
    }

    const uri = result.assets[0].uri;
    const raw = await FileSystem.readAsStringAsync(uri, {
      encoding: FileSystem.EncodingType.UTF8,
    });

    const data = JSON.parse(raw);

    if (!data.version || !data.plans) {
      return { success: false, error: 'Invalid backup file' };
    }

    await Promise.all([
      savePlans(data.plans || []),
      saveHistory(data.history || []),
      savePRs(data.prs || {}),
      saveSettings(data.settings || null),
      saveBodyWeight(data.bodyWeight || []),
    ]);

    return { success: true, data };
  } catch (e) {
    console.error('importAllData error', e);
    return { success: false, error: e.message };
  }
}
