
import React, { useContext, useState } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet, ScrollView,
  TextInput, Share, ActivityIndicator, Switch,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Ionicons from 'react-native-vector-icons/Ionicons';
import * as Sharing from '../platform/sharing';
import * as FileSystem from '../platform/filesystem';
import { useTheme } from '../context/ThemeContext';
import useWatermelonPlans from '../hooks/useWatermelonPlans';
import useWatermelonSettings from '../hooks/useWatermelonSettings';
import { getSetting } from '../db/repositories/settingsRepository';
import { getExerciseIndex, saveCustomExercise } from '../services/ExerciseLibraryService';
import CustomAlert from '../components/CustomAlert';
import { fireHaptic } from '../services/hapticsEngine';
import { textOnColor } from '../utils/colorUtils';

const MUSCLE_OPTIONS = ['Chest', 'Back', 'Shoulders', 'Biceps', 'Triceps', 'Quads', 'Hamstrings', 'Glutes', 'Calves', 'Core', 'Forearms', 'Traps', 'Cardio'];
const EQUIP_OPTIONS = ['Barbell', 'Dumbbell', 'Cable', 'Machine', 'Bodyweight', 'Band', 'Kettlebell', 'Other'];

function genId() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 7); }

function getActiveProfile(gymProfiles, activeGymProfileId) {
  if (!Array.isArray(gymProfiles)) return null;
  return gymProfiles.find(p => p.id === activeGymProfileId) || gymProfiles[0] || null;
}

const GOAL_OPTIONS = ['Hypertrophy', 'Strength', 'Fat Loss', 'General Fitness', 'Custom'];
const DURATION_OPTIONS = ['45', '60', '75', '90', '120', '150'];

function buildPromptText({ equipment, days, goal, sessionDuration, cardio }) {
  const equipList = Array.isArray(equipment) && equipment.length
    ? equipment.join(', ')
    : 'Barbell, Dumbbell, Cable, Machine';
  const cardioNote = cardio ? 'Include 10-15 min cardio warm-up every session.' : 'No dedicated cardio needed.';

  return `You are a professional strength & conditioning coach. Create a ${days}-day-per-week training plan optimised for ${goal}. Each session should be around ${sessionDuration} minutes. ${cardioNote}

AVAILABLE EQUIPMENT: ${equipList}

OUTPUT RULES — read carefully:
1. Respond ONLY with a single JSON object. No markdown code fences, no prose, no comments.
2. The JSON must match this exact schema:

{
  "version": 1,
  "type": "ironlog_plan",
  "exportedAt": "<ISO timestamp>",
  "plan": {
    "name": "<plan name>",
    "days": [
      {
        "name": "<day label e.g. Push / Pull / Legs A>",
        "color": "<hex color e.g. #FF4500>",
        "exercises": [
          {
            "exerciseName": "<name — from library OR a new exercise you invent>",
            "primaryMuscle": "<muscle group — required if exercise is NOT in the library>",
            "equipment": "<equipment type — required if exercise is NOT in the library>",
            "category": "<strength | cardio | stretching — required if exercise is NOT in the library>",
            "movementPattern": "<hinge | squat | push | pull | carry | lunge | rotation | isolation | conditioning — optional, include for new exercises>",
            "difficulty": "<beginner | intermediate | advanced | expert — optional, include for new exercises>",
            "isBodyweight": <true | false — optional, set true only for bodyweight exercises>,
            "sets": <number>,
            "reps": "<rep range e.g. 8-12>",
            "restSeconds": <seconds between sets>,
            "supersetGroup": null,
            "isWarmup": false,
            "notes": ""
          }
        ]
      }
    ]
  }
}

3. Prefer exercise names from the attached exercise library (v2 format). The library file includes movementPattern, difficulty, and isBodyweight fields — use these to pick the best match for the goal. If no suitable exercise exists, invent a clear descriptive name and MUST include primaryMuscle, equipment, and category fields.
4. Include 4-7 exercises per day. Add 1-2 warmup sets (isWarmup: true) for the first exercise.
5. Use these day colors: Push #FF4500, Pull #0080FF, Legs #00C170, Upper #A020F0, Full Body #FF8C00, other days use #888888.
6. restSeconds: compound lifts 120-180s, isolation 60-90s.
7. Valid primaryMuscle values: Chest, Back, Shoulders, Biceps, Triceps, Quads, Hamstrings, Glutes, Calves, Core, Forearms, Traps, Cardio.
8. Valid equipment values: Barbell, Dumbbell, Cable, Machine, Bodyweight, Band, Kettlebell, Other.
9. For ${goal} training: prefer movementPatterns that match the goal (e.g. hinge/squat/push/pull compounds for Strength, higher isolation volume for Hypertrophy, mixed patterns for General Fitness).

Attach the exercise library JSON file I shared.
Generate the plan now. Output ONLY the JSON object.`;
}

function fuzzyMatchExercise(name, exercises) {
  if (!name) return null;
  const lower = name.toLowerCase();
  const exact = exercises.find(e => e.name.toLowerCase() === lower);
  if (exact) return exact;
  const partial = exercises.find(e =>
    e.name.toLowerCase().includes(lower) || lower.includes(e.name.toLowerCase())
  );
  return partial || null;
}

function validateAndParseAIPlan(rawText, exercises) {
  let data;
  try {
    const trimmed = rawText.trim();
    const jsonText = trimmed.replace(/^```(?:json)?\s*/i, '').replace(/\s*```\s*$/, '');
    data = JSON.parse(jsonText);
  } catch {
    return { ok: false, error: 'Could not parse JSON. Make sure you pasted the full AI response.' };
  }
  if (data.type !== 'ironlog_plan' || !data.plan?.name || !Array.isArray(data.plan?.days)) {
    return { ok: false, error: 'Invalid plan format. The AI response must follow the IronLog schema.' };
  }
  // warnings = exercises not in library AND missing AI-provided metadata (need manual config)
  const warnings = [];
  // newExercises = exercises not in library but AI provided full metadata (auto-addable)
  const newExercises = [];
  const days = data.plan.days.map(day => ({
    id: genId(),
    name: day.name || 'Day',
    color: day.color || '#888888',
    exercises: (day.exercises || []).map(ex => {
      const matched = fuzzyMatchExercise(ex.exerciseName, exercises);
      if (!matched) {
        if (ex.primaryMuscle && ex.equipment && ex.category) {
          // AI supplied full metadata — queue for auto-add
          newExercises.push({
            name: ex.exerciseName,
            primaryMuscle: ex.primaryMuscle,
            equipment: ex.equipment,
            category: ex.category,
          });
        } else {
          warnings.push(ex.exerciseName);
        }
      }
      return {
        id: genId(),
        exerciseId: matched?.id || null,
        name: ex.exerciseName || '',
        sets: ex.sets ?? 3,
        reps: ex.reps ?? '10',
        restSeconds: ex.restSeconds ?? 90,
        supersetGroup: ex.supersetGroup ?? null,
        isWarmup: ex.isWarmup ?? false,
        notes: ex.notes ?? '',
        _newExMeta: !matched && ex.primaryMuscle ? { primaryMuscle: ex.primaryMuscle, equipment: ex.equipment, category: ex.category } : null,
      };
    }),
  }));
  return { ok: true, plan: { id: genId(), name: data.plan.name, days }, warnings, newExercises };
}

const STEP = { INTRO: 'intro', QUIZ: 'quiz', PROMPT: 'prompt', LIBRARY: 'library', PASTE: 'paste', PREVIEW: 'preview' };

export default function AIPlanScreen({ navigation }) {
  const colors = useTheme();
  const insets = useSafeAreaInsets();
  const { plans, savePlans } = useWatermelonPlans();
  const { settings } = useWatermelonSettings();
  const [gymProfiles, setGymProfiles] = useState([]);
  const [activeGymProfileId, setActiveGymProfileId] = useState('');
  React.useEffect(() => {
    (async () => {
      try {
        const gp = await getSetting('ironlog_gym_profiles');
        const aid = await getSetting('ironlog_active_gym_profile_id');
        setGymProfiles(Array.isArray(gp) ? gp : []);
        setActiveGymProfileId(String(aid || ''));
      } catch (_) {}
    })();
  }, []);
  const haptic = settings?.hapticFeedback !== false;

  const [step, setStep] = useState(STEP.INTRO);

  // Quiz state
  const [daysPerWeek, setDaysPerWeek] = useState(settings?.weeklyGoalDays || 4);
  const [goalOption, setGoalOption] = useState('Hypertrophy');
  const [customGoal, setCustomGoal] = useState('');
  const [sessionDuration, setSessionDuration] = useState('60');
  const [cardioEverySession, setCardioEverySession] = useState(false);

  // Prompt step - editable
  const [editablePrompt, setEditablePrompt] = useState('');

  const [pastedJson, setPastedJson] = useState('');
  const [parsedPlan, setParsedPlan] = useState(null);
  const [parseWarnings, setParseWarnings] = useState([]);
  const [missingConfigs, setMissingConfigs] = useState({});
  const [autoAddedCount, setAutoAddedCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [alertConfig, setAlertConfig] = useState(null);
  const [promptShared, setPromptShared] = useState(false);
  const [libraryShared, setLibraryShared] = useState(false);

  const activeProfile = getActiveProfile(gymProfiles, activeGymProfileId);
  const equipment = activeProfile?.equipment || [];

  const goToPrompt = () => {
    const goal = goalOption === 'Custom' ? (customGoal.trim() || 'general fitness') : goalOption;
    const text = buildPromptText({ equipment, days: daysPerWeek, goal, sessionDuration, cardio: cardioEverySession });
    setEditablePrompt(text);
    setStep(STEP.PROMPT);
  };

  const sharePrompt = async () => {
    try {
      fireHaptic('selection', { enabled: haptic });
      await Share.share({ message: editablePrompt, title: 'IronLog AI Plan Prompt' });
      setPromptShared(true);
    } catch (e) {
      if (e?.message !== 'User did not share') {
        setAlertConfig({ title: 'Error', message: String(e?.message || e), buttons: [{ text: 'OK', style: 'cancel' }] });
      }
    }
  };

  const shareLibrary = async () => {
    try {
      setLoading(true);
      fireHaptic('selection', { enabled: haptic });
      const allExercises = await getExerciseIndex();
      const filtered = equipment.length
        ? allExercises.filter(ex => !ex.equipment || equipment.some(e =>
            ex.equipment.toLowerCase().includes(e.toLowerCase()) ||
            e.toLowerCase().includes(ex.equipment.toLowerCase())
          ))
        : allExercises;
      const libraryPayload = {
        type: 'ironlog_exercise_library',
        schemaVersion: '2.0.0',
        generatedAt: new Date().toISOString(),
        equipment: equipment.length ? equipment : ['All'],
        exercises: filtered.map(ex => ({
          name: ex.name,
          primaryMuscle: ex.primaryMuscle || (ex.primaryMuscles || [])[0] || '',
          equipment: ex.equipment || '',
          category: ex.category || 'strength',
          movementPattern: ex.movementPattern || null,
          difficulty: ex.difficulty || null,
          isBodyweight: ex.isBodyweight ?? false,
        })),
      };
      const filePath = FileSystem.cacheDirectory + 'ironlog_exercise_library.json';
      await FileSystem.writeAsStringAsync(filePath, JSON.stringify(libraryPayload, null, 2));
      await Sharing.shareAsync(filePath, { mimeType: 'application/json', dialogTitle: 'Share Exercise Library to AI App' });
      setLibraryShared(true);
    } catch (e) {
      setAlertConfig({ title: 'Share failed', message: String(e?.message || e), buttons: [{ text: 'OK', style: 'cancel' }] });
    } finally {
      setLoading(false);
    }
  };

  const parsePlan = async () => {
    if (!pastedJson.trim()) {
      setAlertConfig({ title: 'Nothing pasted', message: 'Paste the AI-generated JSON first.', buttons: [{ text: 'OK', style: 'cancel' }] });
      return;
    }
    setLoading(true);
    try {
      const exercises = await getExerciseIndex();
      const result = validateAndParseAIPlan(pastedJson, exercises);
      if (!result.ok) {
        setAlertConfig({ title: 'Parse error', message: result.error, buttons: [{ text: 'OK', style: 'cancel' }] });
        return;
      }
      // Auto-save exercises the AI provided full metadata for
      if (result.newExercises?.length) {
        for (const ex of result.newExercises) {
          await saveCustomExercise({
            id: ex.name.replace(/[^a-zA-Z0-9]/g, '_').toLowerCase(),
            name: ex.name,
            primaryMuscle: ex.primaryMuscle,
            primaryMuscles: [ex.primaryMuscle],
            equipment: ex.equipment,
            category: ex.category,
            isCustom: true,
          });
        }
        setAutoAddedCount(result.newExercises.length);
        // Re-validate so newly added exercises now match
        const refreshed = await getExerciseIndex();
        const result2 = validateAndParseAIPlan(pastedJson, refreshed);
        if (result2.ok) {
          setParsedPlan(result2.plan);
          setParseWarnings(result2.warnings);
          const init = {};
          result2.warnings.forEach(name => { init[name] = { primaryMuscle: '', equipment: 'Other' }; });
          setMissingConfigs(init);
          setStep(STEP.PREVIEW);
          return;
        }
      }
      setParsedPlan(result.plan);
      setParseWarnings(result.warnings);
      const init = {};
      result.warnings.forEach(name => { init[name] = { primaryMuscle: '', equipment: 'Other' }; });
      setMissingConfigs(init);
      setStep(STEP.PREVIEW);
    } finally {
      setLoading(false);
    }
  };

  const addMissingExercises = async () => {
    const names = Object.keys(missingConfigs).filter(n => missingConfigs[n].primaryMuscle);
    if (!names.length) {
      setAlertConfig({ title: 'Select muscle', message: 'Choose a primary muscle for each exercise first.', buttons: [{ text: 'OK', style: 'cancel' }] });
      return;
    }
    setLoading(true);
    try {
      for (const name of names) {
        const cfg = missingConfigs[name];
        await saveCustomExercise({
          id: name.replace(/[^a-zA-Z0-9]/g, '_').toLowerCase(),
          name,
          primaryMuscle: cfg.primaryMuscle,
          primaryMuscles: [cfg.primaryMuscle],
          equipment: cfg.equipment || 'Other',
          category: 'strength',
          isCustom: true,
        });
      }
      // Re-validate with updated library
      const exercises = await getExerciseIndex();
      const result = validateAndParseAIPlan(pastedJson, exercises);
      if (result.ok) {
        setParsedPlan(result.plan);
        setParseWarnings(result.warnings);
        const remaining = {};
        result.warnings.forEach(n => { remaining[n] = missingConfigs[n] || { primaryMuscle: '', equipment: 'Other' }; });
        setMissingConfigs(remaining);
      }
      fireHaptic('success', { enabled: haptic });
    } finally {
      setLoading(false);
    }
  };

  const importPlan = () => {
    if (!parsedPlan) return;
    let planName = parsedPlan.name;
    if (plans.some(p => p.name === planName)) planName += ' (AI)';
    savePlans([...plans, { ...parsedPlan, name: planName }]);
    fireHaptic('success', { enabled: haptic });
    setAlertConfig({
      title: 'Plan imported!',
      message: `"${planName}" has been added to your plans.`,
      buttons: [{ text: 'Done', style: 'default', onPress: () => navigation.goBack() }],
    });
  };

  const s = makeStyles(colors);

  const renderIntro = () => (
    <View style={s.stepCard}>
      <View style={s.aiIconWrap}>
        <Ionicons name="sparkles" size={32} color={colors.accent} />
      </View>
      <Text style={s.stepTitle}>CREATE PLAN WITH AI</Text>
      <Text style={s.stepBody}>
        Use any AI assistant (Claude, ChatGPT, Gemini) to build a custom training plan — then import it directly into IronLog.
      </Text>
      <View style={s.stepList}>
        {['Answer a few questions about your goals', 'Copy the tailored prompt to your AI app', 'Share the exercise library file', 'Paste the AI response back & import'].map((item, i) => (
          <View key={i} style={s.stepRow}>
            <View style={[s.stepNum, { backgroundColor: colors.accentSoft, borderColor: colors.accentBorder }]}>
              <Text style={[s.stepNumText, { color: colors.accent }]}>{i + 1}</Text>
            </View>
            <Text style={[s.stepRowText, { color: colors.text }]}>{item}</Text>
          </View>
        ))}
      </View>
      <TouchableOpacity style={[s.primaryBtn, { backgroundColor: colors.accent }]} onPress={() => setStep(STEP.QUIZ)}>
        <Text style={s.primaryBtnText}>GET STARTED</Text>
      </TouchableOpacity>
    </View>
  );

  const renderQuiz = () => (
    <View style={s.stepCard}>
      <View style={s.stepHeader}>
        <View style={[s.stepBadge, { backgroundColor: colors.accentSoft }]}>
          <Text style={[s.stepBadgeText, { color: colors.accent }]}>STEP 1 OF 4</Text>
        </View>
        <Text style={s.stepTitle}>PLAN DETAILS</Text>
        <Text style={s.stepBody}>Tell the AI what you want and it will build a plan around your goals.</Text>
      </View>

      {/* Days per week */}
      <View style={[s.quizBlock, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.quizLabel, { color: colors.muted }]}>DAYS PER WEEK</Text>
        <View style={s.stepperRow}>
          <TouchableOpacity
            onPress={() => { fireHaptic('selection', { enabled: haptic }); setDaysPerWeek(Math.max(1, daysPerWeek - 1)); }}
            style={[s.stepperBtn, { borderColor: colors.faint, backgroundColor: colors.surface }]}>
            <Text style={[s.stepperBtnText, { color: colors.text }]}>−</Text>
          </TouchableOpacity>
          <Text style={[s.stepperVal, { color: colors.text }]}>{daysPerWeek}</Text>
          <TouchableOpacity
            onPress={() => { fireHaptic('selection', { enabled: haptic }); setDaysPerWeek(Math.min(7, daysPerWeek + 1)); }}
            style={[s.stepperBtn, { borderColor: colors.faint, backgroundColor: colors.surface }]}>
            <Text style={[s.stepperBtnText, { color: colors.text }]}>+</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Goal */}
      <View style={[s.quizBlock, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.quizLabel, { color: colors.muted }]}>GOAL</Text>
        <View style={s.chipRow}>
          {GOAL_OPTIONS.map(opt => {
            const active = goalOption === opt;
            return (
              <TouchableOpacity
                key={opt}
                onPress={() => { fireHaptic('selection', { enabled: haptic }); setGoalOption(opt); }}
                style={[s.chip, { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' }]}>
                <Text style={[s.chipText, { color: active ? colors.accent : colors.subtext }]}>{opt}</Text>
              </TouchableOpacity>
            );
          })}
        </View>
        {goalOption === 'Custom' && (
          <TextInput
            style={[s.customGoalInput, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.surface }]}
            value={customGoal}
            onChangeText={setCustomGoal}
            placeholder="Describe your goal…"
            placeholderTextColor={colors.muted}
          />
        )}
      </View>

      {/* Session duration */}
      <View style={[s.quizBlock, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <Text style={[s.quizLabel, { color: colors.muted }]}>SESSION LENGTH</Text>
        <View style={s.chipRow}>
          {DURATION_OPTIONS.map(opt => {
            const active = sessionDuration === opt;
            return (
              <TouchableOpacity
                key={opt}
                onPress={() => { fireHaptic('selection', { enabled: haptic }); setSessionDuration(opt); }}
                style={[s.chip, { borderColor: active ? colors.accent : colors.faint, backgroundColor: active ? colors.accentSoft : 'transparent' }]}>
                <Text style={[s.chipText, { color: active ? colors.accent : colors.subtext }]}>{opt} min</Text>
              </TouchableOpacity>
            );
          })}
        </View>
      </View>

      {/* Cardio toggle */}
      <View style={[s.quizBlock, { backgroundColor: colors.card, borderColor: colors.cardBorder }]}>
        <View style={s.toggleRow}>
          <View>
            <Text style={[s.quizLabel, { color: colors.muted, marginBottom: 2 }]}>CARDIO EVERY SESSION</Text>
            <Text style={[s.quizSub, { color: colors.subtext }]}>Include a short cardio warm-up in each day</Text>
          </View>
          <Switch
            value={cardioEverySession}
            onValueChange={v => { fireHaptic('selection', { enabled: haptic }); setCardioEverySession(v); }}
            trackColor={{ false: colors.faint, true: colors.accentSoft }}
            thumbColor={cardioEverySession ? colors.accent : colors.muted}
          />
        </View>
      </View>

      <TouchableOpacity style={[s.primaryBtn, { backgroundColor: colors.accent }]} onPress={goToPrompt}>
        <Text style={s.primaryBtnText}>BUILD PROMPT →</Text>
      </TouchableOpacity>
    </View>
  );

  const renderPromptStep = () => (
    <View style={s.stepCard}>
      <View style={s.stepHeader}>
        <View style={[s.stepBadge, { backgroundColor: colors.accentSoft }]}>
          <Text style={[s.stepBadgeText, { color: colors.accent }]}>STEP 2 OF 4</Text>
        </View>
        <Text style={s.stepTitle}>REVIEW PROMPT</Text>
        <Text style={s.stepBody}>
          Edit the prompt if you want, then share it to your AI app.
        </Text>
      </View>
      <TextInput
        style={[s.promptEditInput, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.surface }]}
        value={editablePrompt}
        onChangeText={setEditablePrompt}
        multiline
        autoCorrect={false}
        autoCapitalize="none"
        textAlignVertical="top"
      />
      <TouchableOpacity style={[s.primaryBtn, { backgroundColor: colors.accent }]} onPress={sharePrompt}>
        <Ionicons name="share-social-outline" size={16} color="#fff" />
        <Text style={s.primaryBtnText}>SHARE PROMPT</Text>
      </TouchableOpacity>
      {promptShared ? (
        <View style={s.doneHint}>
          <Ionicons name="checkmark-circle" size={16} color="#79C98D" />
          <Text style={[s.doneHintText, { color: '#79C98D' }]}>Prompt shared — proceed to step 3</Text>
        </View>
      ) : null}
      <TouchableOpacity
        style={[s.secondaryBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
        onPress={() => setStep(STEP.LIBRARY)}>
        <Text style={[s.secondaryBtnText, { color: colors.accent }]}>ALREADY COPIED → NEXT STEP</Text>
      </TouchableOpacity>
    </View>
  );

  const renderLibraryStep = () => (
    <View style={s.stepCard}>
      <View style={s.stepHeader}>
        <View style={[s.stepBadge, { backgroundColor: colors.accentSoft }]}>
          <Text style={[s.stepBadgeText, { color: colors.accent }]}>STEP 3 OF 4</Text>
        </View>
        <Text style={s.stepTitle}>SHARE EXERCISE LIBRARY</Text>
        <Text style={s.stepBody}>
          Share your exercise library as a JSON file to the AI app — attach or upload it alongside the prompt. The AI will pick exercise names only from this list.
        </Text>
      </View>
      {equipment.length > 0 ? (
        <View style={[s.infoBox, { backgroundColor: colors.accentSoft, borderColor: colors.accentBorder }]}>
          <Ionicons name="fitness-outline" size={14} color={colors.accent} />
          <Text style={[s.infoText, { color: colors.accent }]}>
            Filtered to your gym: {equipment.join(', ')}
          </Text>
        </View>
      ) : null}
      <TouchableOpacity
        style={[s.primaryBtn, { backgroundColor: colors.accent }]}
        onPress={shareLibrary}
        disabled={loading}
      >
        {loading ? <ActivityIndicator color="#fff" size="small" /> : <Ionicons name="document-attach-outline" size={16} color="#fff" />}
        <Text style={s.primaryBtnText}>{loading ? 'PREPARING...' : 'SHARE LIBRARY FILE'}</Text>
      </TouchableOpacity>
      {libraryShared ? (
        <View style={s.doneHint}>
          <Ionicons name="checkmark-circle" size={16} color="#79C98D" />
          <Text style={[s.doneHintText, { color: '#79C98D' }]}>Library shared — generate your plan in the AI app, then come back</Text>
        </View>
      ) : null}
      <TouchableOpacity
        style={[s.secondaryBtn, { borderColor: colors.accent, backgroundColor: colors.accentSoft }]}
        onPress={() => setStep(STEP.PASTE)}>
        <Text style={[s.secondaryBtnText, { color: colors.accent }]}>ALREADY DONE → PASTE RESPONSE</Text>
      </TouchableOpacity>
    </View>
  );

  const renderPasteStep = () => (
    <View style={s.stepCard}>
      <View style={s.stepHeader}>
        <View style={[s.stepBadge, { backgroundColor: colors.accentSoft }]}>
          <Text style={[s.stepBadgeText, { color: colors.accent }]}>STEP 4 OF 4</Text>
        </View>
        <Text style={s.stepTitle}>PASTE AI RESPONSE</Text>
        <Text style={s.stepBody}>
          Copy the JSON the AI generated and paste it below. IronLog will validate, match exercises, and let you preview before importing.
        </Text>
      </View>
      <TextInput
        style={[s.pasteInput, { color: colors.text, borderColor: colors.faint, backgroundColor: colors.surface }]}
        value={pastedJson}
        onChangeText={setPastedJson}
        placeholder={'{\n  "version": 1,\n  "type": "ironlog_plan",\n  ...\n}'}
        placeholderTextColor={colors.muted}
        multiline
        autoCorrect={false}
        autoCapitalize="none"
        textAlignVertical="top"
      />
      <TouchableOpacity
        style={[s.primaryBtn, { backgroundColor: colors.accent, opacity: pastedJson.trim() ? 1 : 0.5 }]}
        onPress={parsePlan}
        disabled={loading || !pastedJson.trim()}
      >
        {loading ? <ActivityIndicator color="#fff" size="small" /> : <Ionicons name="checkmark-outline" size={16} color="#fff" />}
        <Text style={s.primaryBtnText}>{loading ? 'VALIDATING...' : 'VALIDATE & PREVIEW'}</Text>
      </TouchableOpacity>
    </View>
  );

  const renderPreview = () => {
    if (!parsedPlan) return null;
    const totalExercises = parsedPlan.days.reduce((sum, d) => sum + (d.exercises || []).length, 0);
    return (
      <View style={s.stepCard}>
        <Text style={s.stepTitle}>PLAN PREVIEW</Text>
        <View style={[s.planMeta, { backgroundColor: colors.accentSoft, borderColor: colors.accentBorder }]}>
          <Text style={[s.planMetaName, { color: colors.accent }]}>{parsedPlan.name}</Text>
          <Text style={[s.planMetaSub, { color: colors.accent }]}>{parsedPlan.days.length} days · {totalExercises} exercises</Text>
        </View>
        {autoAddedCount > 0 ? (
          <View style={[s.infoBox, { backgroundColor: '#00C17022', borderColor: '#00C17055' }]}>
            <Ionicons name="checkmark-circle-outline" size={14} color="#00C170" />
            <Text style={[s.infoText, { color: '#00C170' }]}>
              {autoAddedCount} new exercise{autoAddedCount > 1 ? 's' : ''} added to your library automatically
            </Text>
          </View>
        ) : null}
        {parseWarnings.length > 0 ? (
          <View style={[s.missingBox, { backgroundColor: '#E5C46A11', borderColor: '#E5C46A55' }]}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginBottom: 10 }}>
              <Ionicons name="warning-outline" size={14} color="#E5C46A" />
              <Text style={[s.warningTitle, { color: '#E5C46A' }]}>
                {parseWarnings.length} exercise{parseWarnings.length > 1 ? 's' : ''} not in library — configure to add
              </Text>
            </View>
            {parseWarnings.map(name => {
              const cfg = missingConfigs[name] || { primaryMuscle: '', equipment: 'Other' };
              return (
                <View key={name} style={[s.missingExRow, { borderColor: '#E5C46A33' }]}>
                  <Text style={[s.missingExName, { color: '#E5C46A' }]}>{name}</Text>
                  <Text style={[s.quizLabel, { color: colors.muted, marginTop: 6 }]}>PRIMARY MUSCLE</Text>
                  <View style={s.chipRow}>
                    {MUSCLE_OPTIONS.map(m => {
                      const active = cfg.primaryMuscle === m;
                      return (
                        <TouchableOpacity
                          key={m}
                          onPress={() => setMissingConfigs(prev => ({ ...prev, [name]: { ...cfg, primaryMuscle: m } }))}
                          style={[s.chip, { borderColor: active ? '#E5C46A' : colors.faint, backgroundColor: active ? '#E5C46A22' : 'transparent' }]}>
                          <Text style={[s.chipText, { color: active ? '#E5C46A' : colors.subtext }]}>{m}</Text>
                        </TouchableOpacity>
                      );
                    })}
                  </View>
                  <Text style={[s.quizLabel, { color: colors.muted, marginTop: 8 }]}>EQUIPMENT</Text>
                  <View style={s.chipRow}>
                    {EQUIP_OPTIONS.map(eq => {
                      const active = cfg.equipment === eq;
                      return (
                        <TouchableOpacity
                          key={eq}
                          onPress={() => setMissingConfigs(prev => ({ ...prev, [name]: { ...cfg, equipment: eq } }))}
                          style={[s.chip, { borderColor: active ? '#E5C46A' : colors.faint, backgroundColor: active ? '#E5C46A22' : 'transparent' }]}>
                          <Text style={[s.chipText, { color: active ? '#E5C46A' : colors.subtext }]}>{eq}</Text>
                        </TouchableOpacity>
                      );
                    })}
                  </View>
                </View>
              );
            })}
            <TouchableOpacity
              style={[s.primaryBtn, { backgroundColor: '#E5C46A', marginTop: 8 }]}
              onPress={addMissingExercises}
              disabled={loading}>
              {loading ? <ActivityIndicator color="#000" size="small" /> : <Ionicons name="add-circle-outline" size={16} color="#000" />}
              <Text style={[s.primaryBtnText, { color: '#000' }]}>ADD TO LIBRARY & RE-MATCH</Text>
            </TouchableOpacity>
          </View>
        ) : null}
        {parsedPlan.days.map((day) => (
          <View key={day.id} style={[s.dayPreviewCard, { borderColor: colors.faint }]}>
            <View style={[s.dayPreviewHeader, { borderLeftColor: day.color }]}>
              <Text style={[s.dayPreviewName, { color: day.color }]}>{day.name}</Text>
              <Text style={[s.dayPreviewCount, { color: colors.muted }]}>{day.exercises.length} ex</Text>
            </View>
            {day.exercises.map((ex) => (
              <View key={ex.id} style={s.exPreviewRow}>
                <Text style={[s.exPreviewName, { color: ex.exerciseId ? colors.text : '#E5C46A' }]}>
                  {ex.isWarmup ? '(W) ' : ''}{ex.name}
                </Text>
                <Text style={[s.exPreviewMeta, { color: colors.muted }]}>
                  {ex.sets}×{ex.reps}
                </Text>
              </View>
            ))}
          </View>
        ))}
        <TouchableOpacity style={[s.primaryBtn, { backgroundColor: colors.accent, marginTop: 12 }]} onPress={importPlan}>
          <Ionicons name="download-outline" size={16} color="#fff" />
          <Text style={s.primaryBtnText}>IMPORT TO PLANS</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[s.secondaryBtn, { borderColor: colors.faint }]} onPress={() => setStep(STEP.PASTE)}>
          <Text style={[s.secondaryBtnText, { color: colors.muted }]}>← BACK TO PASTE</Text>
        </TouchableOpacity>
      </View>
    );
  };

  return (
    <View style={[s.container, { backgroundColor: colors.bg }]}>
      <ScrollView
        contentContainerStyle={[s.content, { paddingBottom: insets.bottom + 32 }]}
        keyboardShouldPersistTaps="handled"
      >
        {step === STEP.INTRO && renderIntro()}
        {step === STEP.QUIZ && renderQuiz()}
        {step === STEP.PROMPT && renderPromptStep()}
        {step === STEP.LIBRARY && renderLibraryStep()}
        {step === STEP.PASTE && renderPasteStep()}
        {step === STEP.PREVIEW && renderPreview()}
      </ScrollView>
      <CustomAlert
        visible={!!alertConfig}
        title={alertConfig?.title}
        message={alertConfig?.message}
        buttons={alertConfig?.buttons || []}
        onDismiss={() => setAlertConfig(null)}
      />
    </View>
  );
}

function makeStyles(colors) {
  const CARD_RADIUS = 18;
  const BTN_RADIUS = 999;
  const primaryTextColor = textOnColor(colors.accent, colors.textOnAccent);
  return StyleSheet.create({
    container: { flex: 1 },
    content: { padding: 16, gap: 0 },
    stepCard: { gap: 14 },
    stepHeader: { gap: 8 },
    aiIconWrap: { alignItems: 'center', marginBottom: 4 },
    stepBadge: { alignSelf: 'flex-start', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999 },
    stepBadgeText: { fontSize: 9, fontWeight: '900', letterSpacing: 2 },
    stepTitle: { fontSize: 22, fontWeight: '900', letterSpacing: 1.5, color: colors.text },
    stepBody: { fontSize: 13, lineHeight: 20, color: colors.subtext },
    stepList: { gap: 10 },
    stepRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
    stepNum: { width: 26, height: 26, borderRadius: 13, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
    stepNumText: { fontSize: 12, fontWeight: '900' },
    stepRowText: { flex: 1, fontSize: 13, lineHeight: 18 },
    primaryBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, paddingVertical: 16, borderRadius: BTN_RADIUS },
    primaryBtnText: { color: primaryTextColor, fontSize: 13, fontWeight: '900', letterSpacing: 1.5 },
    secondaryBtn: { borderWidth: 1.5, paddingVertical: 14, borderRadius: BTN_RADIUS, alignItems: 'center' },
    secondaryBtnText: { fontSize: 12, fontWeight: '900', letterSpacing: 1 },
    quizBlock: { borderWidth: 1, borderRadius: CARD_RADIUS, padding: 14, gap: 10 },
    quizLabel: { fontSize: 10, fontWeight: '900', letterSpacing: 1.5 },
    quizSub: { fontSize: 12, lineHeight: 16 },
    chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
    chip: { borderWidth: 1, paddingHorizontal: 14, paddingVertical: 8, borderRadius: 999 },
    chipText: { fontSize: 12, fontWeight: '700' },
    customGoalInput: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 10, fontSize: 13, marginTop: 4 },
    stepperRow: { flexDirection: 'row', alignItems: 'center', gap: 0 },
    stepperBtn: { borderWidth: 1, width: 38, height: 38, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
    stepperBtnText: { fontSize: 20, fontWeight: '700', lineHeight: 24 },
    stepperVal: { fontSize: 22, fontWeight: '900', minWidth: 48, textAlign: 'center' },
    toggleRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
    promptEditInput: {
      borderWidth: 1, borderRadius: CARD_RADIUS, padding: 14,
      fontSize: 12, minHeight: 240, fontFamily: 'monospace', lineHeight: 18,
    },
    pasteInput: {
      borderWidth: 1, borderRadius: CARD_RADIUS, padding: 14,
      fontSize: 12, minHeight: 200, fontFamily: 'monospace',
    },
    doneHint: { flexDirection: 'row', alignItems: 'center', gap: 6 },
    doneHintText: { fontSize: 12, fontWeight: '700' },
    infoBox: { flexDirection: 'row', alignItems: 'center', gap: 8, borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 8 },
    infoText: { fontSize: 12, fontWeight: '700', flex: 1 },
    planMeta: { borderWidth: 1, borderRadius: CARD_RADIUS, padding: 14 },
    planMetaName: { fontSize: 18, fontWeight: '900' },
    planMetaSub: { fontSize: 12, marginTop: 2, fontWeight: '700' },
    warningTitle: { fontSize: 12, fontWeight: '800', flex: 1 },
    missingBox: { borderWidth: 1, borderRadius: CARD_RADIUS, padding: 14, gap: 0 },
    missingExRow: { borderWidth: 1, borderRadius: 12, padding: 10, marginBottom: 10 },
    missingExName: { fontSize: 13, fontWeight: '800' },
    dayPreviewCard: { borderWidth: 1, borderRadius: CARD_RADIUS, overflow: 'hidden' },
    dayPreviewHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 14, paddingVertical: 10, borderLeftWidth: 3 },
    dayPreviewName: { fontSize: 14, fontWeight: '900', letterSpacing: 0.5 },
    dayPreviewCount: { fontSize: 11, fontWeight: '700' },
    exPreviewRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 7, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: 'rgba(255,255,255,0.06)' },
    exPreviewName: { fontSize: 13, flex: 1 },
    exPreviewMeta: { fontSize: 11, fontWeight: '700', marginLeft: 10 },
  });
}

