package com.ironlog.app.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ironlog.app.ui.theme.IronLogThemeTokens
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Separate backdrop layer: the navigation's text and icons are never blurred or refracted. */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun BoxScope.LiquidGlassBackground(
    hazeState: HazeState,
    colors: IronLogThemeTokens,
    position: Float,
    target: Int,
    tabCount: Int,
    press: Float,
    refractionFactory: () -> GlassRefraction? = ::createLiquidGlassRefraction,
) {
    val light = colors.tabBg.luminance() > .5f
    val refraction = remember { refractionFactory() }
    var effectFailed by remember(refraction) { mutableStateOf(false) }
    val optical = refraction != null && !effectFailed
    val shape = RoundedCornerShape(999.dp)
    val style = remember(colors) {
        HazeStyle(backgroundColor = colors.tabBg.copy(alpha = 1f),
            tint = HazeTint(colors.tabBg.copy(alpha = if (light) .36f else .28f)),
            blurRadius = 2.dp, noiseFactor = 0f,
            fallbackTint = HazeTint(colors.tabBg.copy(alpha = 1f)))
    }
    Box(Modifier.matchParentSize().testTag("liquid-glass-background").clip(shape)) {
        if (!optical) {
            // A device may support backdrop blur but not runtime refraction.
            // Never leave the low-tint optical material without its contrast shader.
            Box(Modifier.matchParentSize().background(colors.tabBg.copy(alpha = 1f)))
        } else Box(Modifier.matchParentSize()
            // Keep the measured/hit-test rectangle unchanged. Only the backdrop texture
            // is larger: a curved edge needs pixels from *outside* the capsule to bend.
            // Clipping before capture would stretch the last interior texel into a rim.
            .layout { measurable, constraints ->
                val margin = GLASS_SAMPLE_MARGIN_DP.dp.roundToPx()
                val layer = measurable.measure(Constraints.fixed(
                    constraints.maxWidth + margin * 2, constraints.maxHeight + margin * 2))
                layout(constraints.maxWidth, constraints.maxHeight) { layer.place(-margin, -margin) }
            }
            .graphicsLayer {
                val margin = GLASS_SAMPLE_MARGIN_DP.dp.roundToPx().toFloat()
                val width = size.width - margin * 2
                val height = size.height - margin * 2
                if (width > 0 && height > 0) {
                    val lens = liquidGlassLens(width, height, tabCount, position, target, density)
                    renderEffect = refraction?.effect(size.width, size.height, lens, density, margin, light, press)
                    // Switch once; do not retry an unsupported GPU on every frame.
                    if (renderEffect == null) effectFailed = true
                }
            }
            .hazeEffect(hazeState, style) { inputScale = HazeInputScale.None })
    }

    if (!optical) Canvas(Modifier.matchParentSize()) {
        // Supported devices shade the curved rim in the optical shader. Only the
        // compatibility material needs a drawn edge; no white outline over the lens.
        run {
            val stroke = .75.dp.toPx()
            drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .24f),
                Color.White.copy(alpha = .02f), Color.White.copy(alpha = .12f)),
                start = Offset.Zero, end = Offset(size.width, size.height)),
                topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(size.height / 2), style = Stroke(stroke))
        }

        val lens = liquidGlassLens(size.width, size.height, tabCount, position, target, density)
        val lensTop = Offset(lens.left, lens.top)
        val lensSize = Size(lens.width, lens.height)
        val lensRadius = CornerRadius(lens.height / 2)
        drawRoundRect(Brush.verticalGradient(
            listOf(colors.tabBg.copy(alpha = if (light) .09f else .16f),
                colors.tabBg.copy(alpha = if (light) .16f else .25f),
                Color.White.copy(alpha = (if (light) .24f else .12f) + .06f * press)),
            startY = lens.top, endY = lens.bottom), lensTop, lensSize, lensRadius)
        run {
            drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .30f + .10f * press),
                Color.White.copy(alpha = .03f), Color.White.copy(alpha = .14f)),
                start = lensTop, end = Offset(lens.right, lens.bottom)),
                lensTop, lensSize, lensRadius, style = Stroke(.75.dp.toPx()))
        }
    }
}

internal interface GlassRefraction {
    fun effect(width: Float, height: Float, lens: GlassLens, density: Float,
        sampleMargin: Float = 0f, light: Boolean = false, press: Float = 0f): RenderEffect?
}

// Covers outer bevel + moving lens sampling at every supported density. This
// small off-screen texture does not change the visible bar's 64dp height.
private const val GLASS_SAMPLE_MARGIN_DP = 20f

internal fun createLiquidGlassRefraction(): GlassRefraction? =
    if (Build.VERSION.SDK_INT >= 33) runCatching { Api33GlassRefraction() }.getOrNull() else null

@RequiresApi(33)
private class Api33GlassRefraction : GlassRefraction {
    private val shader = android.graphics.RuntimeShader(GLASS_SHADER)
    private var failed = false

    override fun effect(width: Float, height: Float, lens: GlassLens, density: Float,
        sampleMargin: Float, light: Boolean, press: Float): RenderEffect? {
        if (failed) return null
        return runCatching {
            shader.setFloatUniform("resolution", width, height)
            shader.setFloatUniform("lens", lens.left + sampleMargin, lens.top + sampleMargin,
                lens.right + sampleMargin, lens.bottom + sampleMargin)
            shader.setFloatUniform("density", density)
            shader.setFloatUniform("sampleMargin", sampleMargin)
            shader.setFloatUniform("lightMaterial", if (light) 1f else 0f)
            shader.setFloatUniform("darkMaxLuminance", GLASS_DARK_MAX_LUMINANCE)
            shader.setFloatUniform("lightMinLuminance", GLASS_LIGHT_MIN_LUMINANCE)
            shader.setFloatUniform("press", press.coerceIn(0f, 1f))
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }.getOrElse { failed = true; null }
    }
}

// Original AGSL approximation, not Apple's proprietary renderer. A rounded
// cross-section pulls surrounding pixels into the bevel and returns to a clear
// center; lighting uses that same surface normal rather than a painted outline.
private const val GLASS_SHADER = """
uniform shader content;
uniform float2 resolution;
uniform float4 lens;
uniform float density;
uniform float sampleMargin;
uniform float lightMaterial;
uniform float darkMaxLuminance;
uniform float lightMinLuminance;
uniform float press;

float capsuleDistance(float2 p, float2 halfSize) {
    float radius = min(halfSize.x, halfSize.y);
    float2 q = abs(p) - (halfSize - radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float2 capsuleNormal(float2 p, float2 halfSize) {
    float radius = min(halfSize.x, halfSize.y);
    float2 nearest = clamp(p, -(halfSize - radius), halfSize - radius);
    float2 direction = p - nearest;
    return direction / max(length(direction), 0.001);
}
half4 main(float2 p) {
    float2 center = resolution * 0.5;
    float2 halfSize = center - float2(sampleMargin);
    float2 local = p - center;
    float edge = capsuleDistance(local, halfSize);
    float2 normal = capsuleNormal(local, halfSize);
    float band = 1.0 - smoothstep(0.0, 14.0 * density, -edge);
    // Outside-to-inside sampling makes text and image contours curl around the
    // perimeter. A full-resolution overscan prevents clamping at the visible rim.
    float2 warped = p + normal * band * band * 10.0 * density;

    float2 lensCenter = (lens.xy + lens.zw) * 0.5;
    float2 lensHalf = (lens.zw - lens.xy) * 0.5;
    float2 lensLocal = p - lensCenter;
    float lensEdge = capsuleDistance(lensLocal, lensHalf);
    float lensInside = 1.0 - smoothstep(-0.5 * density, 0.5 * density, lensEdge);
    float lensBand = (1.0 - smoothstep(0.0, 9.0 * density, -lensEdge)) * lensInside;
    float2 lensNormal = capsuleNormal(lensLocal, lensHalf);
    warped += lensNormal * lensBand * lensBand * (5.0 + press) * density;
    warped -= lensLocal * 0.018 * (1.0 - smoothstep(-4.0 * density, 0.0, lensEdge));
    half4 color = content.eval(clamp(warped, float2(0.5), resolution - float2(0.5)));

    // Give the moving lens thickness without painting a second translucent
    // layer over the contrast-protected output. Foreground controls stay outside.
    float lensY = clamp((p.y - lens.y) / max(lens.w - lens.y, 1.0), 0.0, 1.0);
    color.rgb *= half(1.0 - lensInside * mix(0.08, 0.04, lightMaterial));
    color.rgb = mix(color.rgb, half3(1.0), half(lensInside *
        smoothstep(0.45, 1.0, lensY) * (0.06 + press * 0.035)));

    float2 lightDirection = normalize(float2(-0.45, -0.89));
    float rim = exp(-pow((-edge - 0.85 * density) / (1.55 * density), 2.0));
    float curvedShade = (1.0 - smoothstep(0.0, 6.0 * density, -edge));
    float facingLight = max(dot(normal, lightDirection), 0.0);
    float facingDown = max(dot(normal, -lightDirection), 0.0);
    color.rgb *= half(1.0 - curvedShade * facingDown * 0.16);
    float highlight = rim * (pow(facingLight, 3.0) * 0.43 + pow(facingDown, 5.0) * 0.13);
    color.rgb = mix(color.rgb, half3(1.0), half(highlight * (1.0 + press * 0.15)));

    float lensRim = exp(-pow((-lensEdge - 0.65 * density) / (1.25 * density), 2.0)) * lensInside;
    float lensLight = pow(max(dot(lensNormal, lightDirection), 0.0), 3.0);
    color.rgb = mix(color.rgb, half3(1.0), half(lensRim * lensLight * (0.30 + press * 0.08)));
    color.rgb *= half(1.0 - lensRim * max(lensNormal.y, 0.0) * mix(0.10, 0.05, lightMaterial));

    // Contrast must be independent of the photo/text underneath and measured
    // in linear sRGB, not gamma-encoded channel averages. These same luminance
    // bounds drive liquidGlassTabInk for every theme and animated color.
    float3 linear = toLinearSrgb(float3(color.rgb) / max(float(color.a), 0.001));
    float luminance = dot(linear, float3(0.2126, 0.7152, 0.0722));
    if (lightMaterial > 0.5) {
        float lift = max(0.0, lightMinLuminance - luminance) / max(1.0 - luminance, 0.001);
        linear = mix(linear, float3(1.0), lift);
    } else {
        linear *= min(1.0, darkMaxLuminance / max(luminance, 0.001));
    }
    return half4(fromLinearSrgb(linear), 1.0);
}
"""
