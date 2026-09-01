package com.ironlog.app.domain.intelligence

const val INTELLIGENCE_MODE_BUILTIN = "builtin"
const val INTELLIGENCE_MODE_GEMINI_NANO = "gemini_nano"
const val INTELLIGENCE_MODE_CLOUD_AI = "cloud_ai"

/**
 * Converts both current values and values written by older onboarding builds into the only
 * intelligence-mode identifiers understood by the runtime screens.
 */
fun canonicalIntelligenceMode(value: String?): String = when (value?.trim()?.lowercase()) {
    INTELLIGENCE_MODE_CLOUD_AI, "auto", "cloud", "cloudai" -> INTELLIGENCE_MODE_CLOUD_AI
    INTELLIGENCE_MODE_GEMINI_NANO, "gemini nano", "apex", "apex_engine" -> INTELLIGENCE_MODE_GEMINI_NANO
    INTELLIGENCE_MODE_BUILTIN, "local", "training_intelligence", "training intelligence" -> INTELLIGENCE_MODE_BUILTIN
    else -> INTELLIGENCE_MODE_BUILTIN
}
