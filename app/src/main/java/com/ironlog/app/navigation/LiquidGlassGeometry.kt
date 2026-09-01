package com.ironlog.app.navigation

import kotlin.math.abs
import kotlin.math.min

internal data class GlassLens(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Drawing coordinates only: no layout node or hit target follows this stretch. */
internal fun liquidGlassLens(width: Float, height: Float, tabs: Int, offset: Float, target: Int, density: Float): GlassLens {
    val pad = min(5f * density, min(width, height) / 2f).coerceAtLeast(0f)
    val count = tabs.coerceAtLeast(1)
    val position = (offset.takeIf { it.isFinite() } ?: target.toFloat()).coerceIn(0f, (count - 1).toFloat())
    val tabWidth = width / count
    val baseWidth = (tabWidth - 2 * pad).coerceAtLeast(0f)
    val travel = abs(target.coerceIn(0, count - 1) - position).coerceIn(0f, 1f)
    val lensWidth = (baseWidth * (1f + .15f * travel)).coerceAtMost((width - 2 * pad).coerceAtLeast(0f))
    val left = (tabWidth * (position + .5f) - lensWidth / 2f).coerceIn(pad, (width - pad - lensWidth).coerceAtLeast(pad))
    return GlassLens(left, pad, left + lensWidth, (height - pad).coerceAtLeast(pad))
}
