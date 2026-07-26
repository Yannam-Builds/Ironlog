package com.ironlog.app.domain.sharing

import android.graphics.Bitmap
import com.ironlog.app.ui.model.UiPlan
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import qrcode.QRCode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Converts a [UiPlan] to a compact Base64-encoded payload suitable for QR codes,
 * and back again.
 *
 * Pipeline: UiPlan → JSON → GZIP → Base64 → QR
 * Reverse:  QR text → Base64 → GZIP decompress → JSON → UiPlan
 */
class PlanQrCodec {

    companion object {
        private const val MAX_PAYLOAD_CHARS = 8_192
        private const val MAX_COMPRESSED_BYTES = 64 * 1024
        private const val MAX_DECOMPRESSED_BYTES = 512 * 1024
        private const val MAX_PLAN_DAYS = 31
        private const val MAX_EXERCISES = 500
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Encode [plan] to a QR-embeddable string.
     * Returns Base64(GZIP(JSON)) of the plan.
     */
    fun encodeToPayload(plan: UiPlan): String {
        val jsonString = json.encodeToString(plan)
        val compressed = gzip(jsonString.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    /**
     * Decode a [payload] string back to a [UiPlan].
     * Returns null if the payload is malformed or cannot be decoded.
     */
    fun decodeFromPayload(payload: String): UiPlan? {
        if (payload.isBlank() || payload.length > MAX_PAYLOAD_CHARS) return null
        return runCatching {
            val compressed = Base64.getUrlDecoder().decode(payload)
            require(compressed.size <= MAX_COMPRESSED_BYTES)
            val jsonBytes = gunzip(compressed)
            json.decodeFromString<UiPlan>(String(jsonBytes, Charsets.UTF_8))
                .takeIf(::isStructurallySafe)
        }.getOrNull()
    }

    /**
     * Generate a [Bitmap] QR code from [payload].
     * Returns null if payload is too large for a QR code.
     */
    fun generateQrBitmap(payload: String, sizePx: Int = 800): Bitmap? {
        return runCatching {
            val qrCode = QRCode.ofSquares()
                .build(payload)
            val rendered = qrCode.render()
            // qrcode-kotlin-android returns an Android Bitmap directly via nativeImage()
            val nativeResult = rendered.nativeImage()
            val sourceBitmap = nativeResult as Bitmap
            Bitmap.createScaledBitmap(sourceBitmap, sizePx, sizePx, false)
        }.getOrNull()
    }

    // ── Compression helpers ───────────────────────────────────────────────

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray {
        val bis = ByteArrayInputStream(data)
        return GZIPInputStream(bis).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_DECOMPRESSED_BYTES) { "Plan payload is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun isStructurallySafe(plan: UiPlan): Boolean {
        if (plan.name.length > 200 || plan.description.length > 10_000) return false
        if (plan.days.size > MAX_PLAN_DAYS) return false
        if (plan.days.sumOf { it.exercises.size } > MAX_EXERCISES) return false
        return plan.days.all { day ->
            day.name.length <= 200 && day.exercises.all { exercise ->
                exercise.name.length <= 300 &&
                    exercise.notes.length <= 5_000 &&
                    exercise.sets in 1..100 &&
                    exercise.restSeconds in 0..86_400
            }
        }
    }
}
