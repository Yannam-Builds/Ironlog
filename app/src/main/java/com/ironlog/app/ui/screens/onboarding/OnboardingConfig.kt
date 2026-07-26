package com.ironlog.app.ui.screens.onboarding

import androidx.compose.ui.graphics.Color

object OnboardingConfig {

    val accentBlue = Color(0xFFC7B6FF)
    val accentGold = Color(0xFFD8C6A1)
    val bgDark = Color(0xFF111018)
    val surfaceDark = Color(0xFF1D1B24)
    val cardBorder = Color(0xFF393441)
    val textMuted = Color(0xFFB9B0C8)
    val grantedColor = Color(0xFF26A69A)
    val deniedColor = Color(0xFFEF5350)

    enum class AiProvider(val key: String, val displayName: String, val baseUrl: String, val apiFormat: String) {
        GEMINI("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "openai"),
        OPENAI("openai", "OpenAI", "https://api.openai.com/v1", "openai"),
        CLAUDE("claude", "Claude", "https://api.anthropic.com", "anthropic"),
        DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com", "openai"),
        KIMI("kimi", "Kimi", "https://api.moonshot.ai/v1", "openai"),
        OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai"),
        CUSTOM("custom", "Custom", "", "openai"),
    }

    data class AiModelOption(
        val modelId: String,
        val displayName: String,
        val subtitle: String,
    )

    val geminiModels = listOf(
        AiModelOption("gemini-2.0-flash-lite", "Gemini 2.0 Flash-Lite", "Recommended free tier"),
        AiModelOption("gemini-2.0-flash", "Gemini 2.0 Flash", "Free, fast, 1M context"),
        AiModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", "Fast, stronger reasoning"),
        AiModelOption("gemini-2.5-pro", "Gemini 2.5 Pro", "Best reasoning"),
    )

    val openAiModels = listOf(
        AiModelOption("gpt-4o-mini", "GPT-4o Mini", "Fast and cost-efficient"),
        AiModelOption("gpt-4o", "GPT-4o", "Most capable"),
    )

    val claudeModels = listOf(
        AiModelOption("claude-3-5-haiku-latest", "Claude 3.5 Haiku", "Fast plan drafting"),
        AiModelOption("claude-3-5-sonnet-latest", "Claude 3.5 Sonnet", "Stronger reasoning"),
    )

    val deepSeekModels = listOf(
        AiModelOption("deepseek-chat", "DeepSeek Chat", "Low-cost structured output"),
        AiModelOption("deepseek-reasoner", "DeepSeek Reasoner", "More deliberate planning"),
    )

    val kimiModels = listOf(
        AiModelOption("moonshot-v1-8k", "Kimi 8K", "Fast"),
        AiModelOption("moonshot-v1-32k", "Kimi 32K", "Longer plans"),
        AiModelOption("moonshot-v1-128k", "Kimi 128K", "Large context"),
    )

    val openRouterModels = listOf(
        AiModelOption("google/gemini-2.0-flash-001", "Gemini 2.0 Flash", "OpenRouter"),
        AiModelOption("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", "OpenRouter"),
        AiModelOption("openai/gpt-4o-mini", "GPT-4o Mini", "OpenRouter"),
    )

    fun modelsFor(provider: AiProvider): List<AiModelOption> = when (provider) {
        AiProvider.GEMINI -> geminiModels
        AiProvider.OPENAI -> openAiModels
        AiProvider.CLAUDE -> claudeModels
        AiProvider.DEEPSEEK -> deepSeekModels
        AiProvider.KIMI -> kimiModels
        AiProvider.OPENROUTER -> openRouterModels
        AiProvider.CUSTOM -> emptyList()
    }

    fun defaultModelFor(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> "gemini-2.0-flash-lite"
        AiProvider.OPENAI -> "gpt-4o-mini"
        AiProvider.CLAUDE -> "claude-3-5-haiku-latest"
        AiProvider.DEEPSEEK -> "deepseek-chat"
        AiProvider.KIMI -> "moonshot-v1-32k"
        AiProvider.OPENROUTER -> "google/gemini-2.0-flash-001"
        AiProvider.CUSTOM -> ""
    }

    fun providerFor(key: String): AiProvider =
        AiProvider.entries.firstOrNull { it.key == key.lowercase() || it.name == key.uppercase() } ?: AiProvider.GEMINI

    fun isKeyFormatValid(provider: AiProvider, key: String): Boolean = key.trim().length >= 12

    const val AI_STUDIO_URL = "https://aistudio.google.com/app/apikey"

    data class BaselineOption(
        val code: String,
        val label: String,
        val description: String,
        val progressionStyle: String,
        val defaultGoalMode: String,
        val accent: Color,
    )

    val baselineOptions = listOf(
        BaselineOption("01", "Rebuilding", "New or returning to training", "LINEAR", "STRENGTH", Color(0xFFB8B2C7)),
        BaselineOption("02", "Consistent", "Training regularly for months", "DOUBLE_PROGRESSION", "HYPERTROPHY", accentBlue),
        BaselineOption("03", "Experienced", "Years of structured work", "UNDULATING", "PERFORMANCE", accentGold),
    )

    enum class OnboardingPermission { CAMERA, HEALTH_CONNECT, NOTIFICATIONS }

    val permissionOrder = listOf(
        OnboardingPermission.CAMERA,
        OnboardingPermission.HEALTH_CONNECT,
        OnboardingPermission.NOTIFICATIONS,
    )

    const val PARTICLE_COUNT_DRIFT = 0
    const val PARTICLE_COUNT_BURST = 0
    const val TYPEWRITER_DELAY_MS = 40L
    const val SCREEN1_AUTO_ADVANCE_MS = 3000L
    const val COMPLETION_REVEAL_DELAY_MS = 3000L
}
