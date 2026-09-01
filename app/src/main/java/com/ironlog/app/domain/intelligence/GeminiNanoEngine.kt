package com.ironlog.app.domain.intelligence

import android.content.Context
import android.os.Build
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

/**
 * API-neutral contract implemented by the Android 12+ AICore adapter.
 *
 * This interface and the public facade deliberately contain no AICore types. The experimental
 * AICore AAR declares minSdk 31, so loading one of its classes on API 26-30 is unsafe even though
 * IronLog itself supports those releases.
 */
internal interface NanoRuntime {
    val lastAvailabilityError: String

    suspend fun checkAvailability(): NanoAvailability
    suspend fun downloadModel()
    suspend fun generate(prompt: String): String?
}

/** Pure gate kept separate so legacy-API behavior is regression-testable on the JVM. */
internal object NanoRuntimeApiGate {
    @JvmStatic
    fun supports(apiLevel: Int): Boolean = apiLevel >= Build.VERSION_CODES.S

    @JvmStatic
    fun <T> loadIfSupported(apiLevel: Int, loader: () -> T): T? =
        if (supports(apiLevel)) loader() else null
}

// ── Engine singleton ──────────────────────────────────────────────────────────

object GeminiNanoEngine {
    private const val AICORE_RUNTIME_CLASS =
        "com.ironlog.app.domain.intelligence.AicoreNanoRuntime"
    private const val LEGACY_API_MESSAGE = "Gemini Nano requires Android 12 (API 31) or newer."

    // The concrete adapter is reflectively loaded only after the API gate. Keeping its class name
    // out of a type reference prevents ART from verifying the minSdk-31 AICore graph on API 26-30.
    @Volatile private var _runtime: NanoRuntime? = null

    // Cached availability result — avoids repeated IPC probes from Settings + HomeScreen.
    @Volatile private var _cachedAvailability: NanoAvailability? = null

    /** Last exception detail from checkAvailability — exposed for diagnostic display in UI. */
    @Volatile var lastAvailabilityError: String = ""
        private set

    private fun runtime(context: Context): NanoRuntime? =
        NanoRuntimeApiGate.loadIfSupported(Build.VERSION.SDK_INT) {
            _runtime ?: synchronized(this) {
                _runtime ?: createRuntime(context.applicationContext)?.also { _runtime = it }
            }
        }

    private fun createRuntime(context: Context): NanoRuntime? = runCatching {
        val runtimeClass = Class.forName(AICORE_RUNTIME_CLASS)
        runtimeClass.getDeclaredConstructor(Context::class.java)
            .newInstance(context) as NanoRuntime
    }.onFailure { error ->
        lastAvailabilityError = describeFailure(error)
    }.getOrNull()

    private fun describeFailure(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        return "${root::class.simpleName}: ${root.message.orEmpty()}".trimEnd()
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
        if (!NanoRuntimeApiGate.supports(Build.VERSION.SDK_INT)) {
            lastAvailabilityError = LEGACY_API_MESSAGE
            return NanoAvailability.UNSUPPORTED
        }
        _cachedAvailability?.let { return it }
        return withContext(Dispatchers.IO) {
            val runtime = runtime(context) ?: return@withContext NanoAvailability.UNSUPPORTED
            try {
                runtime.checkAvailability().also { availability ->
                    lastAvailabilityError = runtime.lastAvailabilityError
                    if (availability == NanoAvailability.SUPPORTED) {
                        _cachedAvailability = availability
                    }
                }
            } catch (error: Throwable) {
                lastAvailabilityError = describeFailure(error)
                NanoAvailability.UNSUPPORTED
            }
        }
    }

    suspend fun downloadModel(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        if (!NanoRuntimeApiGate.supports(Build.VERSION.SDK_INT)) {
            return@withContext Result.failure(UnsupportedOperationException(LEGACY_API_MESSAGE))
        }
        val runtime = runtime(context)
            ?: return@withContext Result.failure(IllegalStateException(lastAvailabilityError))
        runCatching {
            runtime.downloadModel()
            _cachedAvailability = NanoAvailability.SUPPORTED
            lastAvailabilityError = ""
        }.onFailure { error -> lastAvailabilityError = describeFailure(error) }
    }

    private suspend fun generateOrFallback(
        context: Context,
        prompt: String,
        fallback: String,
    ): String {
        val runtime = runtime(context) ?: return fallback
        return runCatching { runtime.generate(prompt) }
            .onFailure { error -> lastAvailabilityError = describeFailure(error) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallback
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

        generateOrFallback(
            context = context,
            prompt = prompt,
            fallback = "Train the most recovered groups today and keep volume moderate.",
        )
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

        generateOrFallback(
            context = context,
            prompt = prompt,
            fallback = "A Push/Pull/Legs split repeated across $weeklyGoalDays days suits your goal well.",
        )
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

        generateOrFallback(
            context = context,
            prompt = prompt,
            fallback = "The selection looks balanced. Ensure compound movements come first for best results.",
        )
    }

    suspend fun askProgressionExplanation(
        context: Context,
        exerciseName: String,
        recentWeightKg: Double,
        recentReps: Int,
        trend: String,
        policy: ResolvedProgressionPolicy = ResolvedProgressionPolicy.conservativeDefault(),
    ): String = withContext(Dispatchers.IO) {
        val prompt = buildProgressionExplanationPrompt(exerciseName, recentWeightKg, recentReps, trend, policy)

        generateOrFallback(
            context = context,
            prompt = prompt,
            fallback = "Use ${policy.label.lowercase()}: confirm the planned reps and effort target first, then use only the smallest policy-consistent rep or load change.",
        )
    }
}
