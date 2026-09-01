package com.ironlog.app.domain.intelligence

import android.content.Context
import android.os.Build
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig

/**
 * The only IronLog class allowed to link the minSdk-31 AICore library.
 *
 * [GeminiNanoEngine] reaches this adapter by name only after checking the device API level. [Keep]
 * is required because release builds use R8 and the class is constructed reflectively.
 */
@Keep
@RequiresApi(Build.VERSION_CODES.S)
internal class AicoreNanoRuntime(context: Context) : NanoRuntime {
    private val appContext = context.applicationContext

    @Volatile
    private var model: GenerativeModel? = null

    @Volatile
    override var lastAvailabilityError: String = ""
        private set

    private fun model(): GenerativeModel = model ?: synchronized(this) {
        model ?: run {
            val config = generationConfig {
                this.context = appContext
                temperature = 0.7f
                topK = 40
                maxOutputTokens = 512
            }
            GenerativeModel(generationConfig = config, downloadConfig = DownloadConfig())
                .also { model = it }
        }
    }

    override suspend fun checkAvailability(): NanoAvailability = try {
        model().prepareInferenceEngine()
        lastAvailabilityError = ""
        NanoAvailability.SUPPORTED
    } catch (error: GenerativeAIException) {
        lastAvailabilityError =
            "${error::class.simpleName}(code=${error.errorCode}): ${error.message}"
        mapAvailabilityError(error)
    } catch (error: Throwable) {
        lastAvailabilityError = "${error::class.simpleName}: ${error.message.orEmpty()}"
        NanoAvailability.UNSUPPORTED
    }

    override suspend fun downloadModel() {
        model().prepareInferenceEngine()
        lastAvailabilityError = ""
    }

    override suspend fun generate(prompt: String): String? =
        model().generateContent(prompt).text?.trim()

    private fun mapAvailabilityError(error: GenerativeAIException): NanoAvailability {
        val message = error.message.orEmpty()
        return when {
            // AICore service cannot be bound, so this device does not expose the runtime.
            error.errorCode in listOf(601, 602, 603, 604, 605) -> NanoAvailability.UNSUPPORTED

            // AICore is installed but Google has not deployed the required Gemini Nano feature.
            message.contains("LLM feature not found", ignoreCase = true) ||
                message.contains("NOT_AVAILABLE", ignoreCase = true) ->
                NanoAvailability.NEEDS_SYSTEM_UPDATE

            message.contains("downloading", ignoreCase = true) -> NanoAvailability.NEEDS_DOWNLOAD
            else -> NanoAvailability.NEEDS_SYSTEM_UPDATE
        }
    }
}
