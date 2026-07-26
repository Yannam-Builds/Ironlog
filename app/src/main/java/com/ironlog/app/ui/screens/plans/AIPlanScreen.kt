package com.ironlog.app.ui.screens.plans

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ironlog.app.data.model.CreateExerciseInput
import com.ironlog.app.data.model.FullPlanDay
import com.ironlog.app.data.model.FullPlanObject
import com.ironlog.app.data.model.LegacyExerciseShape
import com.ironlog.app.data.model.PlanExerciseInput
import com.ironlog.app.data.repository.ExerciseRepository
import com.ironlog.app.data.repository.PlanRepository
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.domain.ai.ExerciseResolutionEngine
import com.ironlog.app.domain.ai.ResolutionStatus
import com.ironlog.app.domain.ai.SmartPlanStructuringEngine
import com.ironlog.app.domain.intelligence.CloudAiEngine
import com.ironlog.app.domain.intelligence.CloudAiKeyStore
import com.ironlog.app.services.ShareService
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import com.ironlog.app.util.normalizeExerciseNameKey
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

private enum class Step { INTRO, QUIZ, PROMPT, LIBRARY, PASTE, PREVIEW, CLOUD_GENERATING }

private data class AlertData(
    val title: String,
    val message: String,
    val confirmLabel: String = "OK",
    val onConfirm: (() -> Unit)? = null
)
private data class NeedsReviewRow(
    val suggestedId: String?,
    val suggestedText: String,
    val candidates: List<Pair<String, String>>,
)

private val MUSCLE_OPTIONS = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Quads", "Hamstrings", "Glutes", "Calves", "Core", "Forearms", "Traps", "Cardio")
private val EQUIP_OPTIONS = listOf("Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight", "Band", "Kettlebell", "Other")
private val GOAL_OPTIONS = listOf("Hypertrophy", "Strength", "Fat Loss", "General Fitness", "Custom")
private val DURATION_OPTIONS = listOf("45", "60", "75", "90", "120", "150")

@Composable
fun AIPlanScreen(planRepo: PlanRepository = PlanRepository(), onBack: () -> Unit = {}) {
    val c = useTheme()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository() }
    val exerciseRepo = remember { ExerciseRepository(context) }

    val appVm: AppDataViewModel = viewModel()
    val appState by appVm.state.collectAsState()
    val cloudSettings = appState.settings
    val cloudApiKey = remember(cloudSettings.cloudAiProviderPreset) {
        CloudAiKeyStore.load(context, cloudSettings.cloudAiProviderPreset)
    }
    val cloudConfigured = cloudApiKey.isNotBlank()
        && cloudSettings.cloudAiBaseUrl.isNotBlank()
        && cloudSettings.cloudAiModelName.isNotBlank()

    var step by remember { mutableStateOf(Step.INTRO) }
    var equipment by remember { mutableStateOf<List<String>>(emptyList()) }
    var haptic by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            haptic = settingsRepo.getBoolean("hapticFeedback", true)
            // Just use a default equipment list if profiles are missing for now to keep it simple,
            // or fetch from profiles if available.
            val gpStr = settingsRepo.getString("ironlog_gym_profiles")
            val actId = settingsRepo.getString("ironlog_active_gym_profile_id")
            if (gpStr != null && actId != null) {
                try {
                    val arr = JSONArray(gpStr)
                    for (i in 0 until arr.length()) {
                        val p = arr.getJSONObject(i)
                        if (p.optString("id") == actId) {
                            val eqArr = p.optJSONArray("equipment") ?: JSONArray()
                            val eqList = mutableListOf<String>()
                            for (j in 0 until eqArr.length()) eqList += eqArr.getString(j)
                            equipment = eqList
                            break
                        }
                    }
                } catch (e: Exception) {}
            }
        }
    }

    var daysPerWeek by remember { mutableStateOf(4) }
    var goalOption by remember { mutableStateOf("Hypertrophy") }
    var customGoal by remember { mutableStateOf("") }
    var sessionDuration by remember { mutableStateOf("60") }
    var cardioEverySession by remember { mutableStateOf(false) }

    var editablePrompt by remember { mutableStateOf("") }
    var pastedJson by remember { mutableStateOf("") }
    
    var parsedPlan by remember { mutableStateOf<FullPlanObject?>(null) }
    var parseWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var parseNeedsReview by remember { mutableStateOf<Map<String, NeedsReviewRow>>(emptyMap()) }
    var manualReviewResolution by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // exerciseName -> exerciseId
    var pickingReviewName by remember { mutableStateOf<String?>(null) }
    var smartNotes by remember { mutableStateOf<List<String>>(emptyList()) }
    var missingConfigs by remember { mutableStateOf<Map<String, Pair<String, String>>>(emptyMap()) } // map of Name -> (Muscle, Equipment)
    var autoAddedCount by remember { mutableStateOf(0) }

    var loading by remember { mutableStateOf(false) }
    var alertConfig by remember { mutableStateOf<AlertData?>(null) }
    var promptShared by remember { mutableStateOf(false) }
    var libraryShared by remember { mutableStateOf(false) }

    fun buildExerciseCatalogMarkdown(exercises: List<LegacyExerciseShape>): String {
        val header = """
# Ironlog Exercise Catalog (Compact)

Use this catalog as the source of truth for exercise naming and metadata.
Format: `Exercise Name | Primary Muscle | Equipment | Category | Tracking Type | Movement Pattern`
""".trimIndent()
        val lines = exercises
            .sortedBy { it.name.lowercase() }
            .map { ex ->
                val primary = ex.primaryMuscle ?: ex.primaryMuscles.firstOrNull() ?: "Unknown"
                "${ex.name} | $primary | ${ex.equipment} | ${ex.category} | ${ex.trackingType} | ${ex.movementPattern}"
            }
        return buildString {
            appendLine(header)
            appendLine()
            appendLine("```text")
            lines.forEach { appendLine(it) }
            appendLine("```")
        }
    }

    fun buildPrompt(exerciseCatalogMarkdown: String) {
        val g = if (goalOption == "Custom") customGoal.trim().ifEmpty { "general fitness" } else goalOption
        val eqList = if (equipment.isNotEmpty()) equipment.joinToString(", ") else "Barbell, Dumbbell, Cable, Machine"
        val cardioNote = if (cardioEverySession) "Include 10-15 min low-intensity cardio only if it fits the plan. Do not mark it as a warmup." else "No dedicated cardio needed."
        editablePrompt = """
You are a professional strength & conditioning coach. Create a ${daysPerWeek}-day-per-week training plan optimised for ${g}. Each session should be around ${sessionDuration} minutes. ${cardioNote}

AVAILABLE EQUIPMENT: ${eqList}

OUTPUT RULES - read carefully:
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
            "exerciseName": "<name - from library OR a new exercise you invent>",
            "primaryMuscle": "<muscle group - required if exercise is NOT in the library>",
            "secondaryMuscles": ["<secondary muscle group names - optional, include for new exercises>"],
            "equipment": "<equipment type - required if exercise is NOT in the library>",
            "category": "<strength | cardio | stretching - required if exercise is NOT in the library>",
            "trackingType": "<weight_reps | reps_only | duration | distance | weight_duration - optional, include for new exercises>",
            "movementPattern": "<hinge | squat | push | pull | carry | lunge | rotation | isolation | conditioning - optional, include for new exercises>",
            "difficulty": "<beginner | intermediate | advanced | expert - optional, include for new exercises>",
            "isBodyweight": <true | false - optional, set true only for bodyweight exercises>,
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

3. Prefer exercise names from the compact exercise catalog provided below. If no suitable exercise exists, invent a clear descriptive name and MUST include primaryMuscle, secondaryMuscles, equipment, category, trackingType, movementPattern, difficulty, and isBodyweight so Ironlog can add it to the exercise library.
4. Include 4-7 exercises per day. Do not add warmup exercises or warmup sets. Every exercise must use "isWarmup": false.
5. Use these day colors: Push #FF4500, Pull #0080FF, Legs #00C170, Upper #A020F0, Full Body #FF8C00, other days use #888888.
6. restSeconds: compound lifts 120-180s, isolation 60-90s.
7. Valid primaryMuscle values: Chest, Back, Shoulders, Biceps, Triceps, Quads, Hamstrings, Glutes, Calves, Core, Forearms, Traps, Cardio.
8. Valid equipment values: Barbell, Dumbbell, Cable, Machine, Bodyweight, Band, Kettlebell, Other.
9. For ${g} training: prefer movementPatterns that match the goal (e.g. hinge/squat/push/pull compounds for Strength, higher isolation volume for Hypertrophy, mixed patterns for General Fitness).

Compact exercise catalog:
$exerciseCatalogMarkdown

Generate the plan now. Output ONLY the JSON object.
""".trimIndent()
        step = Step.PROMPT
    }

    suspend fun doParsePlan(jsonString: String, exercises: List<LegacyExerciseShape>): Pair<Boolean, String?> {
        val data = try {
            // 1. Strip markdown code fences anywhere in the string
            var cleaned = jsonString.trim()
                .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("```\\s*"), "")
                .trim()
            // 2. Try direct parse first
            runCatching { JSONObject(cleaned) }.getOrElse {
                // 3. Fall back: extract the outermost {...} block in case the model added prose
                val start = cleaned.indexOf('{')
                val end = cleaned.lastIndexOf('}')
                if (start != -1 && end > start) {
                    JSONObject(cleaned.substring(start, end + 1))
                } else {
                    throw it
                }
            }
        } catch (e: Exception) {
            return false to "Could not parse JSON. Make sure you pasted the full AI response."
        }
        if (!data.optString("type").equals("ironlog_plan", ignoreCase = true) || data.optJSONObject("plan")?.optString("name").isNullOrBlank() || data.optJSONObject("plan")?.optJSONArray("days") == null) {
            return false to "Invalid plan format. The AI response must follow the IronLog schema."
        }

        val warnings = mutableListOf<String>()
        val needsReview = mutableMapOf<String, NeedsReviewRow>()
        val newExs = mutableListOf<CreateExerciseInput>()
        
        val p = data.getJSONObject("plan")
        val daysArr = p.getJSONArray("days")
        val parsedDays = mutableListOf<FullPlanDay>()

        for (i in 0 until daysArr.length()) {
            val d = daysArr.getJSONObject(i)
            val exArr = d.optJSONArray("exercises") ?: JSONArray()
            val parsedExs = mutableListOf<PlanExerciseInput>()
            for (j in 0 until exArr.length()) {
                val ex = exArr.getJSONObject(j)
                val exName = ex.optString("exerciseName", "Unknown")
                val resolution = ExerciseResolutionEngine.resolve(exName, exercises)
                val exactMatch = findExactExercise(exName, exercises)
                val matched = exactMatch ?: if (resolution.status == ResolutionStatus.MATCHED) resolution.matched else null
                when {
                    matched != null -> {}
                    resolution.status == ResolutionStatus.NEEDS_REVIEW -> {
                        val custom = customExerciseFromAiMetadata(ex, exName)
                        if (custom != null && !hasExactExerciseName(exName, exercises)) {
                            newExs.add(custom)
                        } else {
                            val suggested = resolution.matched?.name ?: "Unknown"
                            needsReview[exName] = NeedsReviewRow(
                                suggestedId = resolution.matched?.id,
                                suggestedText = "Suggested: $suggested (${resolution.confidence}%)",
                                candidates = resolution.topCandidates.map { (exRow, score) -> exRow.id to "${exRow.name} ($score%)" },
                            )
                        }
                    }
                    resolution.status == ResolutionStatus.UNRESOLVED -> {
                        val custom = customExerciseFromAiMetadata(ex, exName)
                        if (custom != null) {
                            newExs.add(custom)
                        } else {
                            warnings.add(exName)
                        }
                    }
                    else -> {}
                }
                parsedExs.add(PlanExerciseInput(
                    exerciseId = matched?.id,
                    name = exName,
                    sets = ex.optInt("sets", 3),
                    reps = ex.optString("reps", "10"),
                    restSeconds = ex.optInt("restSeconds", 90),
                    supersetGroup = if (ex.isNull("supersetGroup")) null else ex.optString("supersetGroup").takeIf { it.isNotBlank() },
                    isWarmup = false,
                    notes = ex.optString("notes", "")
                ))
            }
            parsedDays.add(FullPlanDay(
                name = d.optString("name", "Day"),
                color = d.optString("color", "#888888"),
                exercises = parsedExs
            ))
        }

        if (newExs.isNotEmpty()) {
            var added = 0
            for (nx in newExs.distinctBy { normalizeExerciseNameKey(it.name.orEmpty()) }) {
                try { exerciseRepo.createCustomExercise(nx); added++ } catch (e: Exception) {}
            }
            autoAddedCount = added
            // Re-validate to pick up the newly added ones
            val refreshed = exerciseRepo.getExercisesSnapshot()
            val reWarnings = mutableListOf<String>()
            val reNeedsReview = mutableMapOf<String, NeedsReviewRow>()
            val reParsedDays = mutableListOf<FullPlanDay>()
            for (i in 0 until daysArr.length()) {
                val d = daysArr.getJSONObject(i)
                val exArr = d.optJSONArray("exercises") ?: JSONArray()
                val parsedExs = mutableListOf<PlanExerciseInput>()
                for (j in 0 until exArr.length()) {
                    val ex = exArr.getJSONObject(j)
                    val exName = ex.optString("exerciseName", "Unknown")
                    val resolution = ExerciseResolutionEngine.resolve(exName, refreshed)
                    val exactMatch = findExactExercise(exName, refreshed)
                    val matched = exactMatch ?: if (resolution.status == ResolutionStatus.MATCHED) resolution.matched else null
                    when {
                        matched != null -> {}
                        resolution.status == ResolutionStatus.NEEDS_REVIEW -> {
                            val custom = customExerciseFromAiMetadata(ex, exName)
                            if (custom != null && !hasExactExerciseName(exName, refreshed)) {
                                try { exerciseRepo.createCustomExercise(custom) } catch (e: Exception) {}
                            } else if (!reNeedsReview.containsKey(exName)) {
                                val suggested = resolution.matched?.name ?: "Unknown"
                                reNeedsReview[exName] = NeedsReviewRow(
                                    suggestedId = resolution.matched?.id,
                                    suggestedText = "Suggested: $suggested (${resolution.confidence}%)",
                                    candidates = resolution.topCandidates.map { (exRow, score) -> exRow.id to "${exRow.name} ($score%)" },
                                )
                            }
                        }
                        resolution.status == ResolutionStatus.UNRESOLVED -> {
                            if (!reWarnings.contains(exName)) reWarnings.add(exName)
                        }
                    }
                    parsedExs.add(PlanExerciseInput(
                        exerciseId = matched?.id,
                        name = exName,
                        sets = ex.optInt("sets", 3),
                        reps = ex.optString("reps", "10"),
                        restSeconds = ex.optInt("restSeconds", 90),
                        supersetGroup = if (ex.isNull("supersetGroup")) null else ex.optString("supersetGroup").takeIf { it.isNotBlank() },
                        isWarmup = false,
                        notes = ex.optString("notes", "")
                    ))
                }
                reParsedDays.add(FullPlanDay(
                    name = d.optString("name", "Day"),
                    color = d.optString("color", "#888888"),
                    exercises = parsedExs
                ))
            }
            val finalLibrary = exerciseRepo.getExercisesSnapshot()
            val structured = SmartPlanStructuringEngine.structure(
                plan = FullPlanObject(name = p.optString("name"), goal = "", description = "", days = reParsedDays),
                library = finalLibrary,
            )
            parsedPlan = structured.plan
            parseWarnings = reWarnings
            parseNeedsReview = reNeedsReview
            manualReviewResolution = emptyMap()
            smartNotes = structured.notes
            missingConfigs = (reWarnings + reNeedsReview.keys).distinct().associateWith { "" to "Other" }
            return true to null
        }

        val structured = SmartPlanStructuringEngine.structure(
            plan = FullPlanObject(name = p.optString("name"), goal = "", description = "", days = parsedDays),
            library = exercises,
        )
        parsedPlan = structured.plan
        parseWarnings = warnings
        parseNeedsReview = needsReview
        manualReviewResolution = emptyMap()
        smartNotes = structured.notes
        missingConfigs = (warnings + needsReview.keys).distinct().associateWith { "" to "Other" }
        return true to null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ScreenHeader(title = "AI PLAN CREATOR", onBack = onBack) }
        if (step == Step.INTRO) {
            item {
                Column(Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(18.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("CREATE PLAN WITH AI", color = c.text, fontWeight = FontWeight.Black, fontSize = IronLogType.title.fontSize.sp, letterSpacing = 1.5.sp)
                    Text("Use any AI assistant (Claude, ChatGPT, Gemini) to build a custom training plan - then import it directly into IronLog.", color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
                    listOf("Answer a few questions about your goals", "Copy the tailored prompt to your AI app", "Share the exercise library file", "Paste the AI response back & import").forEachIndexed { i, txt ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(26.dp).background(c.accentSoft, RoundedCornerShape(13.dp)).border(1.dp, c.accentBorder, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = c.accent, fontWeight = FontWeight.Black, fontSize = IronLogType.meta.fontSize.sp)
                            }
                            Text(txt, color = c.text, fontSize = IronLogType.body.fontSize.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    Button(onClick = { step = Step.QUIZ }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("GET STARTED") }
                }
            }
        }

        if (step == Step.QUIZ) {
            item {
                Column(Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(18.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(color = c.accentSoft, shape = RoundedCornerShape(999.dp)) { Text("STEP 1 OF 4", color = c.accent, fontWeight = FontWeight.Black, fontSize = IronLogType.eyebrow.fontSize.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
                    Text("PLAN DETAILS", color = c.text, fontWeight = FontWeight.Black, fontSize = IronLogType.title.fontSize.sp, letterSpacing = 1.5.sp)
                    
                    Text("DAYS PER WEEK", color = c.muted, fontWeight = FontWeight.Black, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 1.5.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { daysPerWeek = maxOf(1, daysPerWeek - 1) }) { Text("-", color = c.text) }
                        Text(daysPerWeek.toString(), color = c.text, fontWeight = FontWeight.Black, fontSize = IronLogType.title.fontSize.sp, modifier = Modifier.padding(horizontal = 20.dp))
                        OutlinedButton(onClick = { daysPerWeek = minOf(7, daysPerWeek + 1) }) { Text("+", color = c.text) }
                    }

                    Text("GOAL", color = c.muted, fontWeight = FontWeight.Black, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 1.5.sp)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GOAL_OPTIONS.forEach { opt ->
                            FilterChip(selected = goalOption == opt, onClick = { goalOption = opt }, label = { Text(opt) })
                        }
                    }
                    if (goalOption == "Custom") {
                        OutlinedTextField(value = customGoal, onValueChange = { customGoal = it }, placeholder = { Text("Describe your goal...") }, modifier = Modifier.fillMaxWidth())
                    }

                    Text("SESSION LENGTH", color = c.muted, fontWeight = FontWeight.Black, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 1.5.sp)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DURATION_OPTIONS.forEach { opt ->
                            FilterChip(selected = sessionDuration == opt, onClick = { sessionDuration = opt }, label = { Text("$opt min") })
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("CARDIO EVERY SESSION", color = c.muted, fontWeight = FontWeight.Black, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 1.5.sp)
                            Text("Include optional short cardio", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                        }
                        Switch(checked = cardioEverySession, onCheckedChange = { cardioEverySession = it })
                    }

                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val catalogMarkdown = exerciseRepo.getCompactCatalogMarkdown()
                            withContext(Dispatchers.Main) {
                                buildPrompt(catalogMarkdown)
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("BUILD PROMPT ->") }
                    if (cloudConfigured) {
                        Button(
                            onClick = {
                                step = Step.CLOUD_GENERATING
                                scope.launch {
                                    val allExercises = withContext(Dispatchers.IO) { exerciseRepo.getExercisesSnapshot() }
                                    val exerciseCatalogMarkdown = withContext(Dispatchers.IO) { exerciseRepo.getCompactCatalogMarkdown() }
                                    val json = CloudAiEngine.generatePlanJson(
                                        baseUrl            = cloudSettings.cloudAiBaseUrl,
                                        apiKey             = cloudApiKey,
                                        modelName          = cloudSettings.cloudAiModelName,
                                        apiFormat          = cloudSettings.cloudAiApiFormat,
                                        daysPerWeek        = daysPerWeek,
                                        goalMode           = if (goalOption == "Custom") customGoal else goalOption,
                                        equipment          = equipment,
                                        sessionDurationMin = sessionDuration.toIntOrNull() ?: 60,
                                        cardioEverySession = cardioEverySession,
                                        exerciseCatalogMarkdown = exerciseCatalogMarkdown,
                                    )
                                    if (json.isBlank()) {
                                        alertConfig = AlertData("Generation Failed", "Cloud AI returned an empty response. Check your settings and try again.")
                                        step = Step.QUIZ
                                        return@launch
                                    }
                                    val (ok, error) = withContext(Dispatchers.IO) { doParsePlan(json, allExercises) }
                                    if (ok && parsedPlan != null) {
                                        step = Step.PREVIEW
                                    } else {
                                        alertConfig = AlertData("Generation Failed", error ?: "The AI response could not be parsed. Please try again.")
                                        step = Step.QUIZ
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                        ) {
                            Text("CREATE WITH CLOUD AI ?", color = c.textOnAccent)
                        }
                    }
                }
            }
        }

        if (step == Step.CLOUD_GENERATING) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(18.dp)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = c.accent, modifier = Modifier.size(36.dp))
                    Text("Generating your plan…", color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.section.fontSize.sp)
                    Text(
                        "${cloudSettings.cloudAiDisplayName.ifBlank { "Cloud AI" }} is creating a personalised ${daysPerWeek}-day plan for you.",
                        color = c.subtext,
                        fontSize = IronLogType.body.fontSize.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (step == Step.PROMPT) {
            item {
                Column(Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(18.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(color = c.accentSoft, shape = RoundedCornerShape(999.dp)) { Text("STEP 2 OF 4", color = c.accent, fontWeight = FontWeight.Black, fontSize = IronLogType.eyebrow.fontSize.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
                    Text("REVIEW PROMPT", color = c.text, fontWeight = FontWeight.Black, fontSize = IronLogType.title.fontSize.sp, letterSpacing = 1.5.sp)
                    OutlinedTextField(
                        value = editablePrompt,
                        onValueChange = { editablePrompt = it },
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = IronLogType.meta.fontSize.sp, color = c.text)
                    )
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(editablePrompt))
                        ShareService.sharePlainText(context, "IronLog AI Plan Prompt", editablePrompt)
                        promptShared = true
                    }, modifier = Modifier.fillMaxWidth()) { Text("SHARE PROMPT") }
                    
                    if (promptShared) {
                        Text("Prompt shared - proceed to step 3", color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(onClick = { step = Step.LIBRARY }, modifier = Modifier.fillMaxWidth()) { Text("ALREADY COPIED -> NEXT STEP") }
                }
            }
        }

        if (step == Step.LIBRARY) {
            item {
                Column(Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(18.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(color = c.accentSoft, shape = RoundedCornerShape(999.dp)) { Text("STEP 3 OF 4", color = c.accent, fontWeight = FontWeight.Black, fontSize = IronLogType.eyebrow.fontSize.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
                    Text("SHARE EXERCISE CATALOG", color = c.text, fontWeight = FontWeight.Black, fontSize = IronLogType.title.fontSize.sp, letterSpacing = 1.5.sp)
                    Text("Share your exercise catalog as a compact Markdown file. The AI should use this list for names and metadata.", color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
                    
                    if (equipment.isNotEmpty()) {
                        Text("Filtered to your gym: ${equipment.joinToString(", ")}", color = c.accent, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.background(c.accentSoft, RoundedCornerShape(8.dp)).padding(12.dp))
                    }

                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            loading = true
                            try {
                                val allExercises = exerciseRepo.getExercisesSnapshot()
                                val filtered = if (equipment.isNotEmpty()) {
                                    allExercises.filter { ex -> ex.equipment == "Other" || equipment.any { e -> ex.equipment.lowercase().contains(e.lowercase()) } }
                                } else allExercises

                                val payload = buildExerciseCatalogMarkdown(filtered)

                                val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
                                val file = File(cacheDir, "ironlog_exercise_catalog_compact.md")
                                file.writeText(payload)
                                
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                withContext(Dispatchers.Main) {
                                    ShareService.shareFile(context, "Share Exercise Catalog", uri, "text/markdown")
                                    libraryShared = true
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { alertConfig = AlertData("Share failed", e.message ?: "Unknown error") }
                            } finally {
                                loading = false
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !loading) { Text(if (loading) "PREPARING..." else "SHARE CATALOG FILE") }

                    if (libraryShared) {
                        Text("Library shared - generate your plan, then come back", color = c.accent, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(onClick = { step = Step.PASTE }, modifier = Modifier.fillMaxWidth()) { Text("ALREADY DONE -> PASTE RESPONSE") }
                }
            }
        }

        if (step == Step.PASTE) {
            item {
                Column(Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(18.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(color = c.accentSoft, shape = RoundedCornerShape(999.dp)) { Text("STEP 4 OF 4", color = c.accent, fontWeight = FontWeight.Black, fontSize = IronLogType.eyebrow.fontSize.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
                    Text("PASTE AI RESPONSE", color = c.text, fontWeight = FontWeight.Black, fontSize = IronLogType.title.fontSize.sp, letterSpacing = 1.5.sp)
                    
                    OutlinedTextField(
                        value = pastedJson,
                        onValueChange = { pastedJson = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        placeholder = { Text("{\n  \"version\": 1,\n  \"type\": \"ironlog_plan\",\n  ...\n}") },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = IronLogType.meta.fontSize.sp, color = c.text)
                    )

                    Button(onClick = {
                        if (pastedJson.isBlank()) {
                            alertConfig = AlertData("Nothing pasted", "Paste the AI-generated JSON first.")
                            return@Button
                        }
                        scope.launch(Dispatchers.IO) {
                            loading = true
                            try {
                                val exercises = exerciseRepo.getExercisesSnapshot()
                                val (ok, error) = doParsePlan(pastedJson, exercises)
                                withContext(Dispatchers.Main) {
                                    if (!ok) alertConfig = AlertData("Parse error", error ?: "Unknown error")
                                    else step = Step.PREVIEW
                                }
                            } finally {
                                loading = false
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !loading && pastedJson.isNotBlank()) { Text(if (loading) "VALIDATING..." else "VALIDATE & PREVIEW") }
                }
            }
        }

        if (step == Step.PREVIEW && parsedPlan != null) {
            val pPlan = parsedPlan!!
            item {
                Column(Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(18.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("PLAN PREVIEW", color = c.text, fontWeight = FontWeight.Black, fontSize = IronLogType.title.fontSize.sp, letterSpacing = 1.5.sp)
                    
                    Column(Modifier.fillMaxWidth().background(c.accentSoft, RoundedCornerShape(12.dp)).border(1.dp, c.accentBorder, RoundedCornerShape(12.dp)).padding(14.dp)) {
                        Text(pPlan.name ?: "Plan", color = c.accent, fontWeight = FontWeight.Black, fontSize = IronLogType.section.fontSize.sp)
                        Text("${pPlan.days.size} days / ${pPlan.days.sumOf { it.exercises.size }} exercises", color = c.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.meta.fontSize.sp)
                    }

                    if (autoAddedCount > 0) {
                        Text("$autoAddedCount new exercise(s) added to your library automatically", color = c.success, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.fillMaxWidth().background(c.success.copy(alpha = 0.13f), RoundedCornerShape(10.dp)).padding(12.dp))
                    }
                    if (smartNotes.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().background(c.accentSoft, RoundedCornerShape(12.dp)).border(1.dp, c.accentBorder, RoundedCornerShape(12.dp)).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("SMART PLAN ADJUSTMENTS", color = c.accent, fontWeight = FontWeight.ExtraBold, fontSize = IronLogType.meta.fontSize.sp)
                            smartNotes.forEach { note ->
                                Text("• $note", color = c.text, fontSize = IronLogType.meta.fontSize.sp)
                            }
                        }
                    }

                    if (parseWarnings.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().background(c.warning.copy(alpha = 0.07f), RoundedCornerShape(18.dp)).border(1.dp, c.warning.copy(alpha = 0.33f), RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${parseWarnings.size} exercise(s) not in library - configure to add", color = c.accent, fontWeight = FontWeight.ExtraBold, fontSize = IronLogType.meta.fontSize.sp)
                            parseWarnings.forEach { name ->
                                val cfg = missingConfigs[name] ?: ("" to "Other")
                                Column(Modifier.fillMaxWidth().border(1.dp, c.warning.copy(alpha = 0.20f), RoundedCornerShape(12.dp)).padding(10.dp)) {
                                    Text(name, color = c.accent, fontWeight = FontWeight.ExtraBold, fontSize = IronLogType.body.fontSize.sp)
                                    Text("PRIMARY MUSCLE", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        MUSCLE_OPTIONS.forEach { m ->
                                            FilterChip(selected = cfg.first == m, onClick = { missingConfigs = missingConfigs.toMutableMap().apply { this[name] = m to cfg.second } }, label = { Text(m) })
                                        }
                                    }
                                    Text("EQUIPMENT", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        EQUIP_OPTIONS.forEach { eq ->
                                            FilterChip(selected = cfg.second == eq, onClick = { missingConfigs = missingConfigs.toMutableMap().apply { this[name] = cfg.first to eq } }, label = { Text(eq) })
                                        }
                                    }
                                }
                            }
                            Button(onClick = {
                                val names = missingConfigs.keys.filter { missingConfigs[it]?.first?.isNotBlank() == true }
                                if (names.isEmpty()) {
                                    alertConfig = AlertData("Select muscle", "Choose a primary muscle for each exercise first.")
                                    return@Button
                                }
                                scope.launch(Dispatchers.IO) {
                                    loading = true
                                    try {
                                        for (n in names) {
                                            val c = missingConfigs[n]!!
                                            try { exerciseRepo.createCustomExercise(CreateExerciseInput(name = n, primaryMuscle = c.first, equipment = c.second, category = "strength")) } catch(e: Exception){}
                                        }
                                        val (ok, _) = doParsePlan(pastedJson, exerciseRepo.getExercisesSnapshot())
                                        withContext(Dispatchers.Main) { if (ok) { /* updated */ } }
                                    } finally {
                                        loading = false
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.Black)) { Text("ADD TO LIBRARY & RE-MATCH") }
                        }
                    }
                    if (parseNeedsReview.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().background(c.warning.copy(alpha = 0.08f), RoundedCornerShape(18.dp)).border(1.dp, c.warning.copy(alpha = 0.35f), RoundedCornerShape(18.dp)).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("NEEDS REVIEW (${parseNeedsReview.size})", color = c.warning, fontWeight = FontWeight.ExtraBold, fontSize = IronLogType.meta.fontSize.sp)
                            Text("These matches are ambiguous and were not auto-linked.", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                            parseNeedsReview.entries.forEach { (name, meta) ->
                                val selectedId = manualReviewResolution[name]
                                val selectedLabel = meta.candidates.firstOrNull { it.first == selectedId }?.second
                                Column(
                                    modifier = Modifier.fillMaxWidth().border(1.dp, c.warning.copy(alpha = 0.25f), RoundedCornerShape(10.dp)).padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(name, color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp)
                                    Text(meta.suggestedText, color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                                    if (selectedLabel != null) {
                                        Text("Selected: $selectedLabel", color = c.success, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (!meta.suggestedId.isNullOrBlank()) {
                                            Button(onClick = {
                                                manualReviewResolution = manualReviewResolution.toMutableMap().also { it[name] = meta.suggestedId }
                                                parseNeedsReview = parseNeedsReview.toMutableMap().also { it.remove(name) }
                                            }) { Text("USE SUGGESTED") }
                                        }
                                        OutlinedButton(onClick = { pickingReviewName = name }) { Text("PICK MATCH") }
                                        if (selectedId != null) {
                                            TextButton(onClick = {
                                                manualReviewResolution = manualReviewResolution.toMutableMap().also { it.remove(name) }
                                            }) { Text("CLEAR") }
                                        }
                                    }
                                }
                            }
                            Text("Resolve by editing JSON names or using 'ADD TO LIBRARY & RE-MATCH' to create explicit custom exercises.", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                        }
                    }

                    pPlan.days.forEach { day ->
                        Column(Modifier.fillMaxWidth().border(1.dp, c.faint, RoundedCornerShape(18.dp))) {
                            Row(Modifier.fillMaxWidth().padding(14.dp, 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(day.name ?: "Day", color = c.text, fontWeight = FontWeight.Black, fontSize = IronLogType.body.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${day.exercises.size} ex", color = c.muted, fontWeight = FontWeight.Bold, fontSize = IronLogType.meta.fontSize.sp)
                            }
                            day.exercises.forEachIndexed { idx, ex ->
                                if (idx > 0) HorizontalDivider(color = c.faint)
                                Row(Modifier.fillMaxWidth().padding(14.dp, 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${if (ex.isWarmup == true) "(W) " else ""}${ex.name}", color = if (ex.exerciseId != null) c.text else c.accent, fontSize = IronLogType.body.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${ex.sets}x${ex.reps}", color = c.muted, fontWeight = FontWeight.Bold, fontSize = IronLogType.meta.fontSize.sp)
                                }
                            }
                        }
                    }

                    val unresolvedReviewNames = parseNeedsReview.keys.filter { manualReviewResolution[it].isNullOrBlank() }
                    val canImport = parseWarnings.isEmpty() && unresolvedReviewNames.isEmpty()
                    Button(onClick = {
                        val resolvedPlan = pPlan.copy(
                            days = pPlan.days.map { day ->
                                day.copy(
                                    exercises = day.exercises.map { ex ->
                                        val name = ex.name.orEmpty()
                                        val overrideId = manualReviewResolution[name]
                                        if (!overrideId.isNullOrBlank()) ex.copy(exerciseId = overrideId) else ex
                                    }
                                )
                            }
                        )
                        scope.launch(Dispatchers.IO) {
                            try {
                                planRepo.importFullPlan(resolvedPlan)
                                withContext(Dispatchers.Main) {
                                    alertConfig = AlertData("Plan imported!", "\"${resolvedPlan.name}\" has been added to your plans.", "Done", onBack)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { alertConfig = AlertData("Import failed", e.message ?: "Unknown error") }
                            }
                        }
                    }, enabled = canImport, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("IMPORT TO PLANS") }
                    if (!canImport) {
                        Text("Import is locked until unresolved and review-required exercises are resolved.", color = c.warning, fontSize = IronLogType.meta.fontSize.sp)
                    }
                    
                    OutlinedButton(onClick = { step = Step.PASTE }, modifier = Modifier.fillMaxWidth()) { Text("<- BACK TO PASTE") }
                }
            }
        }
    }

    alertConfig?.let { a ->
        AlertDialog(
            onDismissRequest = { alertConfig = null },
            title = { Text(a.title) },
            text = { Text(a.message) },
            confirmButton = {
                TextButton(onClick = { alertConfig = null; a.onConfirm?.invoke() }) {
                    Text(a.confirmLabel)
                }
            }
        )
    }

    pickingReviewName?.let { reviewName ->
        val row = parseNeedsReview[reviewName]
        if (row != null) {
            AlertDialog(
                onDismissRequest = { pickingReviewName = null },
                title = { Text("Pick match for $reviewName") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.candidates.forEach { (id, label) ->
                            TextButton(
                                onClick = {
                                    manualReviewResolution = manualReviewResolution.toMutableMap().also { it[reviewName] = id }
                                    parseNeedsReview = parseNeedsReview.toMutableMap().also { it.remove(reviewName) }
                                    pickingReviewName = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(label) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { pickingReviewName = null }) { Text("CLOSE") }
                }
            )
        }
    }
}

private fun findExactExercise(name: String, exercises: List<LegacyExerciseShape>): LegacyExerciseShape? {
    val key = normalizeExerciseNameKey(name)
    if (key.isBlank()) return null
    return exercises.firstOrNull { normalizeExerciseNameKey(it.name) == key }
}

private fun hasExactExerciseName(name: String, exercises: List<LegacyExerciseShape>): Boolean =
    findExactExercise(name, exercises) != null

private fun customExerciseFromAiMetadata(ex: JSONObject, exName: String): CreateExerciseInput? {
    val primary = ex.optString("primaryMuscle")
        .ifBlank { ex.optJSONArray("primaryMuscles")?.optString(0).orEmpty() }
        .trim()
        .takeIf { it.isNotBlank() }
    val equipment = ex.optString("equipment")
        .trim()
        .takeIf { it.isNotBlank() }
    if (primary == null || equipment == null) return null

    val secondary = readAiStringList(ex, "secondaryMuscles")
        .ifEmpty { readAiStringList(ex, "secondaryMuscle") }
        .filterNot { it.equals(primary, ignoreCase = true) }
        .distinct()

    return CreateExerciseInput(
        name = exName,
        primaryMuscle = primary,
        equipment = equipment,
        category = ex.optString("category").trim().ifBlank { "strength" },
        trackingType = ex.optString("trackingType").trim().takeIf { it.isNotBlank() },
        movementPattern = ex.optString("movementPattern").trim().takeIf { it.isNotBlank() },
        difficulty = ex.optString("difficulty").trim().takeIf { it.isNotBlank() },
        isBodyweight = ex.optBoolean("isBodyweight", equipment.equals("Bodyweight", ignoreCase = true)),
        secondaryMuscles = secondary.takeIf { it.isNotEmpty() },
    )
}

private fun readAiStringList(obj: JSONObject, key: String): List<String> {
    val arr = obj.optJSONArray(key)
    if (arr != null) {
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }
    return obj.optString(key)
        .split(",", "/", "|")
        .map { it.trim() }
        .filter { it.isNotBlank() }
}


