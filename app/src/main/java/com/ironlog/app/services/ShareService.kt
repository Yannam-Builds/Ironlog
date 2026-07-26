package com.ironlog.app.services

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ShareService {
    data class ShareMetric(
        val label: String,
        val value: String,
    )

    fun sharePlainText(context: Context, title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareFile(context: Context, title: String, uri: android.net.Uri, mimeType: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareMinimalCardImage(
        context: Context,
        title: String,
        headline: String,
        metrics: List<ShareMetric>,
        footnote: String? = null,
    ) {
        runCatching {
            val width = 1080
            val height = 1350
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    Color.parseColor("#0D1017"),
                    Color.parseColor("#161B25"),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

            val card = RectF(60f, 120f, width - 60f, height - 120f)
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2532") }
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.parseColor("#2A3344")
            }
            canvas.drawRoundRect(card, 46f, 46f, cardPaint)
            canvas.drawRoundRect(card, 46f, 46f, strokePaint)

            val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5A1F") }
            canvas.drawRoundRect(RectF(card.left + 24f, card.top + 24f, card.left + 34f, card.bottom - 24f), 8f, 8f, accent)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#98A2B8")
                textSize = 36f
                isFakeBoldText = true
                letterSpacing = 0.08f
            }
            val headlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 72f
                isFakeBoldText = true
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#98A2B8")
                textSize = 28f
                isFakeBoldText = true
                letterSpacing = 0.05f
            }
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 48f
                isFakeBoldText = true
            }
            val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B7C0D4")
                textSize = 30f
            }

            canvas.drawText(title.uppercase(), card.left + 70f, card.top + 90f, titlePaint)
            canvas.drawText(headline, card.left + 70f, card.top + 190f, headlinePaint)

            var y = card.top + 300f
            metrics.forEach {
                canvas.drawText(it.label.uppercase(), card.left + 70f, y, labelPaint)
                y += 58f
                canvas.drawText(it.value, card.left + 70f, y, valuePaint)
                y += 84f
            }

            footnote?.takeIf { it.isNotBlank() }?.let {
                canvas.drawText(it, card.left + 70f, card.bottom - 70f, footPaint)
            }

            val outFile = File(context.cacheDir, "ironlog_share_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            shareFile(context, "Share", uri, "image/png")
        }.onFailure {
            sharePlainText(context, title, buildString {
                appendLine(headline)
                metrics.forEach { appendLine("${it.label}: ${it.value}") }
                if (!footnote.isNullOrBlank()) appendLine(footnote)
            })
        }
    }
}
