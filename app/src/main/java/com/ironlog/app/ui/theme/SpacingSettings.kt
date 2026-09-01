package com.ironlog.app.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

object SpacingSettings {
    const val DEFAULT = 1f
    const val MIN = 0.85f
    const val MAX = 1.25f
    fun normalize(raw: Any?): Float {
        val value = (raw as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: return DEFAULT
        return ((value.coerceIn(MIN, MAX) * 20f).roundToInt() / 20f).coerceIn(MIN, MAX)
    }

    fun scaleGapDp(value: Float, scale: Float): Float =
        if (value.isFinite() && value > 0f) value * normalize(scale) else value
}

interface SpacingStorage {
    fun read(): Any?
    fun write(scale: Float)
}

/** Independent rhythm settings. Existing installations inherit the old global preference. */
enum class SpacingRole(val preferenceKey: String) {
    CARDS("spacing_cards"), PADDING("spacing_padding"), CONTENT("spacing_content");

    fun initialValue(preferences: Map<String, *>): Float =
        SpacingSettings.normalize(preferences[preferenceKey] ?: preferences[AppearancePreferenceKeys.GLOBAL_SPACING])
}

data class SpacingSaveRequest(val scale: Float, val revision: Long)

class SpacingStore(private val storage: SpacingStorage) {
    private val mutableScale = MutableStateFlow(SpacingSettings.normalize(storage.read()))
    @Volatile private var persistedScale = mutableScale.value
    private val previewLock = Any()
    private var previewRevision = 0L
    val scale = mutableScale.asStateFlow()

    fun preview(value: Float) = synchronized(previewLock) {
        previewRevision += 1
        mutableScale.value = SpacingSettings.normalize(value)
    }

    /** Capture on release, before queueing. Each accepted save is a distinct intent, even at the same value. */
    fun prepareSave(): SpacingSaveRequest = synchronized(previewLock) {
        previewRevision += 1
        SpacingSaveRequest(mutableScale.value, previewRevision)
    }

    /** Caller serializes disk writes on IO; newer drag previews are never overwritten by an older save. */
    @Synchronized
    fun persistPreview(request: SpacingSaveRequest) {
        val normalized = SpacingSettings.normalize(request.scale)
        if (normalized == persistedScale) return
        try {
            storage.write(normalized)
            persistedScale = normalized
        } catch (error: Exception) {
            // This separate, short lock never guards disk IO or blocks a drag for a disk write.
            synchronized(previewLock) {
                if (previewRevision == request.revision) {
                    previewRevision += 1
                    mutableScale.value = persistedScale
                }
            }
            throw error
        }
    }
    @Synchronized
    fun update(value: Float) {
        val request = synchronized(previewLock) {
            previewRevision += 1
            mutableScale.value = SpacingSettings.normalize(value)
            SpacingSaveRequest(mutableScale.value, previewRevision)
        }
        persistPreview(request)
    }

    /** Publishes a value already committed atomically by the full-backup appearance store. */
    internal fun publishRestored(value: Float) = synchronized(previewLock) {
        val normalized = SpacingSettings.normalize(value)
        previewRevision += 1
        persistedScale = normalized
        mutableScale.value = normalized
    }
}
