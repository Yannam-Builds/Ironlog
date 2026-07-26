package com.ironlog.app.domain.intelligence

import android.content.Context
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.ironlog.app.ui.model.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Availability enum exposed to UI ──────────────────────────────────────────

enum class NanoAvailability {
    /** Device supports Gemini Nano and the model is ready. */
    SUPPORTED,
    /** Model must be downloaded first (may take several minutes on Wi-Fi). */
    NEEDS_DOWNLOAD,
    /**
     * AICore is present but the Gemini Nano LLM feature (ID ≥ 10000) has not been
     * deployed to this device yet. This is resolved via a Google Play system update,
     * not by the app itself.
     */
    NEEDS_SYSTEM_UPDATE,
    /** This device/OS version does not support on-device Gemini Nano. */
    UNSUPPORTED,
}

// ── Engine singleton ──────────────────────────────────────────────────────────

object GeminiNanoEngine {

    // Lazy-init so we don't pay model-init cost unless the user enables APEX ENGINE.
    @Volatile private var _model: GenerativeModel? = null

    // Cached availability result — avoids repeated IPC probes from Settings + HomeScreen.
    @Volatile private var _cachedAvailability: NanoAvailability? = null

    /** Last exception detail from checkAvailability — exposed for diagnostic display in UI. */
    @Volatile var lastAvailabilityError: String = ""
        private set

    private fun model(context: Context): GenerativeModel =
        _model ?: synchronized(this) {
            // Double-checked locking — avoids race where two coroutines both see null.
            _model ?: run {
                val config = generationConfig {
                    this.context = context.applicationContext
                    temperature = 0.7f
                    topK = 40
                    maxOutputTokens = 512
                }
                GenerativeModel(generationConfig = config, downloadConfig = DownloadConfig())
                    .also { _model = it }
            }
        }

    // ── Availability ──────────────────────────────────────────────────────────

    /**
     * AICore ErrorCode constants (from GenerativeAIException.ErrorCode, which is an IntDef annotation):
     *   NOT_AVAILABLE     =   8  → model not yet downloaded, device IS supported
     *   BINDING_FAILURE   = 601  → AICore service not present on this device
     *   SERVICE_DISCONNECTED = 602
     *   BINDING_DIED      = 603
     *   NEEDS_SYSTEM_UPDATE = 604 → OS too old / AICore not installed
     *   NULL_BINDING      = 605  → AICore service returned null
     *
     * Only cache SUPPORTED — negative results are not cached so the user can retry
     * (e.g. after enabling AICore in device settings or after a system update).
     */
    suspend fun checkAvailability(context: Context): NanoAvailability {
        _cachedAvailability?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                model(context).prepareInferenceEngine()
                lastAvailabilityError = ""
                NanoAvailability.SUPPORTED.also { _cachedAvailability = it }
            } catch (e: GenerativeAIException) {
                lastAvailabilityError = "${e::class.simpleName}(code=${e.errorCode}): ${e.message}"
                val msg = e.message.orEmpty()
                when {
                    // AICore service can't be bound → device doesn't ship AICore at all
                    e.errorCode in listOf(601, 602, 603, 604, 605) -> NanoAvailability.UNSUPPORTED
                    // "Required LLM feature not found" → AICore present but Google hasn't
                    // pushed the Gemini Nano feature package to this device yet.
                    // User needs a Google Play system update, not an in-app download.
                    msg.contains("LLM feature not found", ignoreCase = true) ||
                    msg.contains("NOT_AVAILABLE", ignoreCase = true) -> NanoAvailability.NEEDS_SYSTEM_UPDATE
                    // Feature registered but not yet downloaded
                    msg.contains("downloading", ignoreCase = true) -> NanoAvailability.NEEDS_DOWNLOAD
                    else -> NanoAvailability.NEEDS_SYSTEM_UPDATE
                }
            } catch (e: Exception) {
                lastAvailabilityError = "${e::class.simpleName}: ${e.message}"
                NanoAvailability.NEEDS_SYSTEM_UPDATE
            }
        }
    }

    suspend fun downloadModel(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            model(context).prepareInferenceEngine()
            _cachedAvailability = NanoAvailability.SUPPORTED
        }
    }

    // ── Query functions ───────────────────────────────────────────────────────

    suspend fun askRecovery(
        context: Context,
        readiness: Map<String, Double>,
    ): String = withContext(Dispatchers.IO) {
        val readinessText = readiness.entries
            .sortedBy { it.key }
            .joinToString(", ") { (k, v) -> "$k ${(v * 100).toInt()}%" }

        val prompt = """You are a concise personal trainer AI inside the IronLog workout app.
Based on these muscle group recovery scores, give ONE actionable recommendation for today in 2 sentences max.
Recovery: $readinessText
Rules: under 60 words, plain text only, no markdown, no bullet points."""

        runCatching { model(context).generateContent(prompt).text?.trim() }
            .getOrNull() ?: "Train the most recovered groups today and keep volume moderate."
    }

    suspend fun askSplitSuggestion(
        context: Context,
        history: List<HistoryEntry>,
        weeklyGoalDays: Int,
        goalMode: String,
    ): String = withContext(Dispatchers.IO) {
        val goal = when (goalMode) {
            "strength"        -> "strength and powerlifting"
            "general_fitness" -> "general fitness and conditioning"
            else              -> "muscle hypertrophy"
        }
        val recentMuscles = history.take(20)
            .flatMap { it.exercises }
            .mapNotNull { it.primaryMuscle?.takeIf { m -> m.isNotBlank() } ?: it.name.takeIf { n -> n.isNotBlank() } }
            .distinct()
            .take(12)
            .joinToString(", ")
            .ifBlank { "various muscle groups" }

        val prompt = """You are a concise personal trainer AI inside the IronLog workout app.
Suggest a $weeklyGoalDays-day weekly split optimised for $goal in 3–4 sentences.
The athlete has recently trained: $recentMuscles.
Be specific (Push/Pull/Legs, Upper/Lower, etc.). Under 80 words. Plain text only, no markdown."""

        runCatching { model(context).generateContent(prompt).text?.trim() }
            .getOrNull() ?: "A Push/Pull/Legs split repeated across $weeklyGoalDays days suits your goal well."
    }

    suspend fun askDayEvaluation(
        context: Context,
        dayName: String,
        exerciseNames: List<String>,
        goalMode: String,
    ): String = withContext(Dispatchers.IO) {
        val goal = when (goalMode) {
            "strength"        -> "strength"
            "general_fitness" -> "general fitness"
            else              -> "hypertrophy"
        }
        val exList = exerciseNames.take(8).joinToString(", ").ifBlank { "no exercises listed" }

        val prompt = """You are a concise personal trainer AI inside the IronLog workout app.
Evaluate this "$dayName" session for $goal: $exList.
Note any imbalances or missing movement patterns in 2–3 sentences. Under 70 words. Plain text only."""

        runCatching { model(context).generateContent(prompt).text?.trim() }
            .getOrNull() ?: "The selection looks balanced. Ensure compound movements come first for best results."
    }

    suspend fun askProgressionExplanation(
        context: Context,
        exerciseName: String,
        recentWeightKg: Double,
        recentReps: Int,
        trend: String,
    ): String = withContext(Dispatchers.IO) {
        val prompt = """You are a concise personal trainer AI inside the IronLog workout app.
$exerciseName: working weight ${recentWeightKg}kg × $recentReps reps, progress trend is $trend.
In 1–2 sentences explain the recommended next progression step. Under 50 words. Plain text only."""

        runCatching { model(context).generateContent(prompt).text?.trim() }
            .getOrNull() ?: "Aim to add a small amount of weight or an extra rep next session."
    }
}
