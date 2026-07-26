package com.ironlog.app.ui.screens.body

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.ironlog.app.ui.context.useTheme
import org.json.JSONObject

// ── Shared types ─────────────────────────────────────────────────────────────

internal data class ViewBox(val x: Float, val y: Float, val width: Float, val height: Float)
internal data class BodyPathPiece(val region: String, val path: Path)
internal data class BodySide(
    val viewBox: ViewBox,
    val pieces: List<BodyPathPiece>,
    val alignmentBox: ViewBox = viewBox,
)
internal data class BodyMapDataset(val front: BodySide, val back: BodySide)

internal fun String.toNormalizedPath(): Path = PathParser().parsePathString(this).toPath()

// ── Transform helpers ─────────────────────────────────────────────────────────
//
//  SVG viewBox → canvas mapping:
//    canvas_x = offsetX + (svg_x - viewBox.x) * scale
//    canvas_y = offsetY + (svg_y - viewBox.y) * scale
//
//  Compose withTransform:  translate(a, b) then scale(s)  maps a draw-point P to:
//    pixel = (a + P.x * s,  b + P.y * s)
//
//  Setting  a = offsetX - viewBox.x * scale,  b = offsetY - viewBox.y * scale,  s = scale
//  gives exactly the desired mapping.
//
//  We use uniform (aspect-preserving) scale so the figures are never stretched.

internal data class BodyTransform(val scale: Float, val offsetX: Float, val offsetY: Float)
internal enum class VerticalAnchor { TOP, CENTER, BOTTOM }

private const val BODY_MAP_SCALE_FRACTION = 0.93f

/**
 * Computes a tight ViewBox that exactly encloses all path content (plus [padding] on each side).
 *
 * **Why this is needed:** the raw JSON viewBox includes large dead space at the top
 * (FRONT body paths start at SVG y≈278, BACK at y≈290, but both viewBoxes start at y=95).
 * Even after using a tight box, the FRONT/BACK heights differ slightly — which is why
 * `BodyHalfCanvas` is rendered with the TOP anchor so heads always line up at the top of
 * the canvas regardless of figure height differences.
 *
 * **Why PathMeasure instead of computeBounds:** Android's `Path.computeBounds()` returns
 * the AABB including Bezier control points, which lie OUTSIDE the visible curve. That makes
 * the box bigger than the actual visible content, and the inflation is different for FRONT
 * vs BACK (different curve shapes), causing the very misalignment we're trying to fix.
 * Sampling positions along the path via `PathMeasure` gives true visible bounds.
 */
internal fun computeTightViewBox(
    pieces: List<BodyPathPiece>,
    padding: Float = 12f,
    samplesPerContour: Int = 512,
): ViewBox? {
    if (pieces.isEmpty()) return null
    var minX =  Float.MAX_VALUE; var minY =  Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    val pm = android.graphics.PathMeasure()
    val pos = FloatArray(2)
    for (piece in pieces) {
        val ap = piece.path.asAndroidPath()
        pm.setPath(ap, false)
        do {
            val len = pm.length
            if (len <= 0f) continue
            for (i in 0..samplesPerContour) {
                val d = (i / samplesPerContour.toFloat()) * len
                if (pm.getPosTan(d, pos, null)) {
                    if (pos[0] < minX) minX = pos[0]
                    if (pos[1] < minY) minY = pos[1]
                    if (pos[0] > maxX) maxX = pos[0]
                    if (pos[1] > maxY) maxY = pos[1]
                }
            }
        } while (pm.nextContour())
    }
    if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite()) return null
    return ViewBox(
        x      = minX - padding,
        y      = minY - padding,
        width  = (maxX - minX) + padding * 2,
        height = (maxY - minY) + padding * 2,
    )
}

internal fun computeBodyTransform(
    canvasW: Float,
    canvasH: Float,
    viewBox: ViewBox,
    fitWidth: Float = viewBox.width,
    fitHeight: Float = viewBox.height,
    verticalAnchor: VerticalAnchor = VerticalAnchor.TOP,
): BodyTransform {
    if (viewBox.width <= 0f || viewBox.height <= 0f || fitWidth <= 0f || fitHeight <= 0f) {
        return BodyTransform(1f, 0f, 0f)
    }
    val scaleX = canvasW / fitWidth
    val scaleY = canvasH / fitHeight

    // Fit each side into the same canonical box when fitWidth/fitHeight are shared.
    val scale  = minOf(scaleX, scaleY) * BODY_MAP_SCALE_FRACTION

    val offsetX = (canvasW - viewBox.width * scale) / 2f

    val offsetY = when (verticalAnchor) {
        VerticalAnchor.TOP -> 0f
        VerticalAnchor.CENTER -> (canvasH - viewBox.height * scale) / 2f
        VerticalAnchor.BOTTOM -> (canvasH - viewBox.height * scale)
    }

    return BodyTransform(scale, offsetX, offsetY)
}

// ── Readiness normalisation (group keys → body-map region keys) ───────────────

internal fun buildDisplayReadiness(groupReadiness: Map<String, Double>): Map<String, Double> {
    val push      = groupReadiness["Push"]
    val pull      = groupReadiness["Pull"]
    val shoulders = groupReadiness["Shoulders"]
    val arms      = groupReadiness["Arms"]
    val core      = groupReadiness["Core"]
    val legs      = groupReadiness["Legs"]
    return mapOf(
        "chest"      to (push ?: 1.0),
        "shoulders"  to (shoulders ?: 1.0),
        "rearDelts"  to (shoulders ?: pull ?: 1.0),
        "arms"       to (arms ?: 1.0),
        "core"       to (core ?: 1.0),
        "quads"      to (legs ?: 1.0),
        "hamstrings" to (legs ?: 1.0),
        "calves"     to (legs ?: 1.0),
        "back"       to (pull ?: 1.0),
    )
}

// ── Asset loader ─────────────────────────────────────────────────────────────

internal fun loadBodyMapDataset(context: Context): BodyMapDataset? = runCatching {
    val raw = context.assets.open("ironlog/body_map_paths.json").bufferedReader().use { it.readText() }
    val root = JSONObject(raw)
    val viewBoxes = root.getJSONObject("VIEW_BOXES").getJSONObject("male")
    val frontVb   = viewBoxes.getJSONObject("front")
    val backVb    = viewBoxes.getJSONObject("back")
    val muscleMap = root.getJSONObject("MUSCLE_MAP")
    val front     = root.getJSONObject("MALE_FRONT_PATHS")
    val back      = root.getJSONObject("MALE_BACK_PATHS")

    val microToMacro = mutableMapOf<String, String>()
    val groups = muscleMap.keys()
    while (groups.hasNext()) {
        val macro = groups.next()
        val arr = muscleMap.getJSONArray(macro)
        for (i in 0 until arr.length()) microToMacro[arr.getString(i)] = macro
    }

    fun parseSide(sideObj: JSONObject, vbObj: JSONObject): BodySide {
        val pieces = mutableListOf<BodyPathPiece>()
        val keys = sideObj.keys()
        while (keys.hasNext()) {
            val slug   = keys.next()
            // Use empty string for unmapped slugs (body outline / silhouette paths).
            // These get value == null in BodyHalfCanvas and are rendered as a subtle
            // silhouette outline only — not filled with a recovery colour.
            val region = microToMacro[slug] ?: ""
            val node   = sideObj.getJSONObject(slug)
            val left   = node.optJSONArray("left")
            val right  = node.optJSONArray("right")
            if (left != null)  for (i in 0 until left.length())  { val d = left.optString(i).trim();  if (d.isNotBlank()) pieces += BodyPathPiece(region, d.toNormalizedPath()) }
            if (right != null) for (i in 0 until right.length()) { val d = right.optString(i).trim(); if (d.isNotBlank()) pieces += BodyPathPiece(region, d.toNormalizedPath()) }
        }
        // Use the tight bounding box of the actual path content instead of the raw
        // JSON viewBox. The JSON viewBox includes dead space at the top/bottom that
        // differs between front and back, causing visual misalignment between the two halves.
        val rawVb = ViewBox(
            x      = vbObj.optDouble("x",      0.0).toFloat(),
            y      = vbObj.optDouble("y",      0.0).toFloat(),
            width  = vbObj.optDouble("width",  1.0).toFloat(),
            height = vbObj.optDouble("height", 1.0).toFloat(),
        )
        val tightVb = computeTightViewBox(pieces) ?: rawVb
        val filledVb = computeTightViewBox(pieces.filter { it.region.isNotBlank() }) ?: tightVb
        return BodySide(viewBox = tightVb, pieces = pieces, alignmentBox = filledVb)
    }

    BodyMapDataset(front = parseSide(front, frontVb), back = parseSide(back, backVb))
}.getOrNull()

// ── Render-only canvas (no tap handling) ─────────────────────────────────────

/**
 * Draws one side of the body map (front or back) into whatever space it is
 * given.  Uses uniform (aspect-preserving) scaling centred in the canvas so
 * figures are never stretched. The canvas itself is not clipped; a few stroked pixels
 * may safely overflow the invisible half-canvas edge instead of cutting off fingers or feet.
 *
 * No tap detection — wrap in a Box with pointerInput if you need it.
 */
@Composable
internal fun BodyHalfCanvas(
    pieces: List<BodyPathPiece>,
    viewBox: ViewBox,
    readiness: Map<String, Double>,
    modifier: Modifier = Modifier,
    fitWidth: Float = viewBox.width,
    fitHeight: Float = viewBox.height,
    horizontalShiftFraction: Float = 0f,
    painFlags: Set<String> = emptySet(),   // GAP-20
    verticalAnchor: VerticalAnchor = VerticalAnchor.TOP,
) {
    val c = useTheme()
    // Pre-cache Android path conversions so asAndroidPath() is not called on every draw frame.
    // The map is keyed on `pieces` identity — recomputed only when the piece list changes.
    val androidPathCache = remember(pieces) {
        pieces.associate { it to it.path.asAndroidPath() }
    }
    // Do NOT add fillMaxSize() here — the modifier already fixes the size via
    // fillMaxWidth().aspectRatio(...).  Adding fillMaxSize() re-expands to the
    // parent's unconstrained max height, which shifts the body figure down-right.
    Canvas(modifier) {
        val t = computeBodyTransform(
            canvasW = size.width,
            canvasH = size.height,
            viewBox = viewBox,
            fitWidth = fitWidth,
            fitHeight = fitHeight,
            verticalAnchor = verticalAnchor,
        )
        pieces.forEach { piece ->
            val value = readiness[piece.region]
            val isPainFlagged = piece.region in painFlags
            withTransform({
                // Correct SVG-viewBox → canvas mapping (see comment at top of file)
                val horizontalShiftPx = viewBox.width * t.scale * horizontalShiftFraction
                translate(
                    left = t.offsetX - viewBox.x * t.scale + horizontalShiftPx,
                    top  = t.offsetY - viewBox.y * t.scale,
                )
                scale(scaleX = t.scale, scaleY = t.scale, pivot = Offset.Zero)
            }) {
                if (isPainFlagged) {
                    // GAP-20: Pain-flagged muscle — red fill + diagonal hatch overlay
                    val painColor = c.danger
                    drawPath(path = piece.path, color = painColor.copy(alpha = 0.55f))
                    drawPath(path = piece.path, color = painColor, style = Stroke(width = 2f))
                    // Diagonal hatch lines approximated with low-alpha strokes through the region.
                    // Use the pre-cached Android path to avoid per-frame allocation.
                    val androidPath = androidPathCache[piece] ?: piece.path.asAndroidPath()
                    val bounds = android.graphics.RectF()
                    androidPath.computeBounds(bounds, true)
                    val step = 8f
                    var y = bounds.top - bounds.width()
                    while (y < bounds.bottom + bounds.width()) {
                        val linePath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(bounds.left, y)
                            lineTo(bounds.right, y + bounds.width())
                        }
                        drawPath(linePath, painColor.copy(alpha = 0.25f), style = Stroke(1.2f))
                        y += step
                    }
                } else if (value == null) {
                    // Unmapped path (body silhouette / outline) — draw as a subtle
                    // stroke only so the body shape is visible without overriding
                    // the coloured muscle regions.
                    drawPath(path = piece.path, color = c.faint.copy(alpha = 0.50f), style = Stroke(width = 1.5f))
                } else {
                    val fill = when {
                        value > 0.9  -> c.success
                        value > 0.72 -> c.warning
                        else         -> c.danger
                    }
                    drawPath(path = piece.path, color = fill.copy(alpha = 0.68f))
                    drawPath(path = piece.path, color = c.cardBorder.copy(alpha = 0.40f), style = Stroke(width = 1.5f))
                }
            }
        }
    }
}

