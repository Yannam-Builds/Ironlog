package com.ironlog.app.domain.intelligence

import com.ironlog.app.ui.model.HistoryEntry
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * HTTP engine for BYOK Cloud AI.
 * Supports OpenAI-compatible providers (apiFormat = "openai") and Anthropic Claude (apiFormat = "anthropic").
 * All suspend functions run natively via Ktor coroutines and never throw — they return safe fallback strings.
 */
object CloudAiEngine {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        expectSuccess = true   // throw ResponseException on non-2xx so callers get a status-code error
        engine {
            connectTimeout = 30_000
            socketTimeout = 60_000
        }
    }

    // ── Serializable request shapes ───────────────────────────────────────────

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class OpenAiRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val max_tokens: Int = 1024,
        val temperature: Double = 0.7,
        val response_format: ResponseFormat? = null,
    )

    @Serializable
    private data class ResponseFormat(val type: String)

    @Serializable
    private data class AnthropicRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val max_tokens: Int = 1024,
        val temperature: Double = 0.7,
        val system: String? = null,
    )

    // ── Model listing ─────────────────────────────────────────────────────────

    /**
     * Lists available models from the provider.
     * Both OpenAI and Anthropic return a "data" array with "id" fields at their /models endpoints.
     * Returns Result.failure on network error or non-2xx response.
     */
    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        apiFormat: String,
    ): Result<List<String>> = runCatching {
        val url = if (apiFormat == "anthropic")
            "https://api.anthropic.com/v1/models"
        else
            "${baseUrl.trimEnd('/')}/models"

        val body: JsonObject = client.get(url) {
            if (apiFormat == "anthropic") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
            } else {
                header("Authorization", "Bearer $apiKey")
            }
        }.body()

        val arr: JsonArray = body["data"]?.jsonArray ?: JsonArray(emptyList())
        // removePrefix("models/") strips the Gemini-style "models/..." prefix; no-op for OpenAI/Anthropic IDs
        arr.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content?.removePrefix("models/") }
    }.onFailure { Timber.w(it, "fetchModels failed") }

    // ── Connection verification ───────────────────────────────────────────────

    /**
     * Fires a minimal request (max_tokens=1) to verify the key and endpoint work.
     * Returns Result.success(Unit) on any 2xx, Result.failure otherwise.
     */
    suspend fun verify(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        apiFormat: String,
    ): Result<Unit> = runCatching {
        if (apiFormat == "anthropic") {
            client.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(AnthropicRequest(
                    model = modelName,
                    messages = listOf(ChatMessage("user", "Reply OK.")),
                    max_tokens = 8,
                    temperature = 0.0,
                ))
            }
        } else {
            client.post("${baseUrl.trimEnd('/')}/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(OpenAiRequest(
                    model = modelName,
                    messages = listOf(ChatMessage("user", "Reply OK.")),
                    max_tokens = 8,
                    temperature = 0.0,
                ))
            }
        }
        Unit
    }.onFailure { Timber.w(it, "verify failed") }

    // ── Four insight query functions ──────────────────────────────────────────

    suspend fun askRecovery(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        apiFormat: String,
        readiness: Map<String, Double>,
    ): String {
        if (apiKey.isBlank() || baseUrl.isBlank()) return "Configure your Cloud AI key in Settings."
        val readinessText = readiness.entries
            .sortedBy { it.key }
            .joinToString(", ") { (k, v) -> "$k ${(v * 100).toInt()}%" }
        val prompt = """You are a concise personal trainer AI inside the IronLog workout app.
Based on these muscle group recovery scores, give ONE actionable recommendation for today in 2 sentences max.
Recovery: $readinessText
Rules: under 60 words, plain text only, no markdown, no bullet points."""
        return runCatching { chat(baseUrl, apiKey, modelName, apiFormat, prompt) }
            .getOrElse { Timber.w(it, "askRecovery failed"); "" }
    }

    suspend fun askSplitSuggestion(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        apiFormat: String,
        history: List<HistoryEntry>,
        weeklyGoalDays: Int,
        goalMode: String,
    ): String {
        if (apiKey.isBlank() || baseUrl.isBlank()) return "Configure your Cloud AI key in Settings."
        val goal = when (goalMode) {
            "strength"        -> "strength and powerlifting"
            "general_fitness" -> "general fitness and conditioning"
            else              -> "muscle hypertrophy"
        }
        val recentMuscles = history.take(20)
            .flatMap { it.exercises }
            .mapNotNull { it.primaryMuscle?.takeIf { m -> m.isNotBlank() } ?: it.name.takeIf { n -> n.isNotBlank() } }
            .distinct().take(12).joinToString(", ").ifBlank { "various muscle groups" }
        val prompt = """You are a concise personal trainer AI inside the IronLog workout app.
Suggest a $weeklyGoalDays-day weekly split optimised for $goal in 3–4 sentences.
The athlete has recently trained: $recentMuscles.
Be specific (Push/Pull/Legs, Upper/Lower, etc.). Under 80 words. Plain text only, no markdown."""
        return runCatching { chat(baseUrl, apiKey, modelName, apiFormat, prompt) }
            .getOrElse { Timber.w(it, "askSplitSuggestion failed"); "" }
    }

    suspend fun askDayEvaluation(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        apiFormat: String,
        dayName: String,
        exerciseNames: List<String>,
        goalMode: String,
    ): String {
        if (apiKey.isBlank() || baseUrl.isBlank()) return "Configure your Cloud AI key in Settings."
        val goal = when (goalMode) {
            "strength"        -> "strength"
            "general_fitness" -> "general fitness"
            else              -> "hypertrophy"
        }
        val exList = exerciseNames.take(8).joinToString(", ").ifBlank { "no exercises listed" }
        val prompt = """You are a concise personal trainer AI inside the IronLog workout app.
Evaluate this "$dayName" session for $goal: $exList.
Note any imbalances or missing movement patterns in 2–3 sentences. Under 70 words. Plain text only."""
        return runCatching { chat(baseUrl, apiKey, modelName, apiFormat, prompt) }
            .getOrElse { Timber.w(it, "askDayEvaluation failed"); "" }
    }

    suspend fun askProgressionExplanation(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        apiFormat: String,
        exerciseName: String,
        recentWeightKg: Double,
        recentReps: Int,
        trend: String,
    ): String {
        if (apiKey.isBlank() || baseUrl.isBlank()) return "Configure your Cloud AI key in Settings."
        val prompt = """You are a concise personal trainer AI inside the IronLog workout app.
$exerciseName: working weight ${recentWeightKg}kg × $recentReps reps, progress trend is $trend.
In 1–2 sentences explain the recommended next progression step. Under 50 words. Plain text only."""
        return runCatching { chat(baseUrl, apiKey, modelName, apiFormat, prompt) }
            .getOrElse { Timber.w(it, "askProgressionExplanation failed"); "" }
    }

    suspend fun generatePlanJson(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        apiFormat: String,
        daysPerWeek: Int,
        goalMode: String,
        equipment: List<String>,
        sessionDurationMin: Int,
        cardioEverySession: Boolean,
        exerciseCatalogMarkdown: String,
    ): String {
        if (apiKey.isBlank() || baseUrl.isBlank()) return ""
        val equipmentText = equipment.joinToString(", ").ifBlank { "Barbell, Dumbbell, Bodyweight" }
        val cardioNote = if (cardioEverySession) "Include a short low-intensity cardio exercise only if the user explicitly requested cardio; do not mark it as a warmup." else ""

        val systemPrompt = "You are a JSON API. Output ONLY a single raw JSON object. " +
            "Never write any text, explanation, markdown, or code fences before or after the JSON."

        val userPrompt = """Create a $daysPerWeek-day weekly strength training plan.
Goal: $goalMode
Equipment: $equipmentText
Session length: ~$sessionDurationMin minutes
$cardioNote

Output a single JSON object matching this schema exactly — no deviations, no extra keys at the root:
{
  "type": "ironlog_plan",
  "version": 1,
  "plan": {
    "name": "<descriptive plan name>",
    "days": [
      {
        "name": "<day name e.g. Push / Pull / Legs A>",
        "color": "<hex e.g. #FF4500>",
        "exercises": [
          {
            "exerciseName": "<exercise name>",
            "primaryMuscle": "<Chest | Back | Shoulders | Biceps | Triceps | Quads | Hamstrings | Glutes | Calves | Core | Forearms | Traps | Cardio>",
            "secondaryMuscles": ["<optional secondary muscle group names>"],
            "equipment": "<Barbell | Dumbbell | Cable | Machine | Bodyweight | Band | Kettlebell | Other>",
            "category": "<strength | cardio | stretching>",
            "trackingType": "<weight_reps | reps_only | duration | distance | weight_duration>",
            "movementPattern": "<hinge | squat | push | pull | carry | lunge | rotation | isolation | conditioning>",
            "difficulty": "<beginner | intermediate | advanced | expert>",
            "isBodyweight": <true | false>,
            "sets": <integer>,
            "reps": "<string e.g. 8-12>",
            "restSeconds": <integer>,
            "isWarmup": false,
            "supersetGroup": null,
            "notes": ""
          }
        ]
      }
    ]
  }
}

Strict rules:
1. Root must have "type": "ironlog_plan" and "version": 1.
2. "exerciseName" key only — never "name" for exercises.
3. 4-12 exercises per day depending on session length and goal. Do not add warmup exercises or warmup sets. Every exercise must use "isWarmup": false.
4. sets = integer, reps = string ("8-12" or "12"), restSeconds = integer (60-180).
5. Day colors: Push=#FF4500, Pull=#0080FF, Legs=#00C170, Upper=#A020F0, Full Body=#FF8C00, other=#888888.
6. Use common exercise names: "Barbell Bench Press", "Pull-Up", "Barbell Squat", "Romanian Deadlift", etc.
7. If you invent an exercise or use a name that may not exist in Ironlog, include complete metadata: primaryMuscle, secondaryMuscles, equipment, category, trackingType, movementPattern, difficulty, and isBodyweight. This lets Ironlog add it to the local exercise library automatically.
8. Output ONLY the JSON object. Nothing before it. Nothing after it.
9. Write all $daysPerWeek days completely — do not truncate or summarise."""

        val promptWithCatalog = """
$userPrompt

Exercise catalog (compact markdown, source of truth for naming + metadata):
$exerciseCatalogMarkdown
""".trimIndent()

        return runCatching {
            chatWithSystem(baseUrl, apiKey, modelName, apiFormat, systemPrompt, promptWithCatalog, maxTokens = 8192, temperature = 0.1, jsonMode = true)
        }.getOrElse { Timber.e(it, "generatePlanJson failed"); "" }
    }

    suspend fun askStatsSummary(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        apiFormat: String,
        totalSessions: Int,
        streak: Int,
        totalSets: Int,
        avgDurationMin: Int,
        topExercise: String,
        weightUnit: String,
    ): String {
        if (apiKey.isBlank() || baseUrl.isBlank()) return ""
        val prompt = """You are a concise personal trainer AI inside the IronLog workout app.
Summarise this athlete's training stats in 1-2 upbeat sentences. Mention highlights and one actionable tip.
Stats: $totalSessions sessions total, $streak-day streak, $totalSets total sets, avg session ${avgDurationMin}min, top exercise: $topExercise, weight unit: $weightUnit.
Under 60 words. Plain text only. No markdown."""
        return runCatching { chat(baseUrl, apiKey, modelName, apiFormat, prompt) }
            .getOrElse { Timber.w(it, "askStatsSummary failed"); "" }
    }

    // ── Private HTTP helpers ──────────────────────────────────────────────────

    /** Dispatches to openai or anthropic format. Throws on any error — callers wrap in runCatching. */
    private suspend fun chat(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        apiFormat: String,
        prompt: String,
        maxTokens: Int = 1024,
        temperature: Double = 0.7,
    ): String = chatWithSystem(baseUrl, apiKey, modelName, apiFormat, null, prompt, maxTokens, temperature)

    /**
     * Like [chat] but accepts an optional system prompt and forwards it via the appropriate
     * API mechanism (system field for Anthropic, system role message for OpenAI).
     * @param jsonMode Pass `true` to enable `response_format: json_object` on OpenAI-compatible
     *   endpoints. Must be set explicitly — there is no auto-detection based on systemPrompt.
     *   Only [generatePlanJson] should pass `true` here.
     */
    private suspend fun chatWithSystem(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        apiFormat: String,
        systemPrompt: String?,
        userPrompt: String,
        maxTokens: Int = 1024,
        temperature: Double = 0.7,
        jsonMode: Boolean = false,
    ): String = if (apiFormat == "anthropic")
        chatAnthropic(apiKey, modelName, systemPrompt, userPrompt, maxTokens, temperature)
    else
        chatOpenAi(baseUrl, apiKey, modelName, systemPrompt, userPrompt, maxTokens, temperature, jsonMode)

    private suspend fun chatOpenAi(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        systemPrompt: String?,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double,
        jsonMode: Boolean,
    ): String {
        val messages = buildList {
            if (!systemPrompt.isNullOrBlank()) add(ChatMessage("system", systemPrompt))
            add(ChatMessage("user", userPrompt))
        }
        val response: JsonObject = client.post("${baseUrl.trimEnd('/')}/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(OpenAiRequest(
                model = modelName,
                messages = messages,
                max_tokens = maxTokens,
                temperature = temperature,
                response_format = if (jsonMode) ResponseFormat("json_object") else null,
            ))
        }.body()
        return response["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?.trim()
            ?: error("Empty OpenAI response")
    }

    private suspend fun chatAnthropic(
        apiKey: String,
        modelName: String,
        systemPrompt: String?,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double,
    ): String {
        val response: JsonObject = client.post("https://api.anthropic.com/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(AnthropicRequest(
                model = modelName,
                messages = listOf(ChatMessage("user", userPrompt)),
                max_tokens = maxTokens,
                temperature = temperature,
                system = systemPrompt?.takeIf { it.isNotBlank() },
            ))
        }.body()
        return response["content"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?.trim()
            ?: error("Empty Anthropic response")
    }
}
