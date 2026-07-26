package com.ironlog.app.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ParticleField(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") count: Int = OnboardingConfig.PARTICLE_COUNT_DRIFT,
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        OnboardingConfig.bgDark,
                        OnboardingConfig.bgDark.copy(alpha = 0.96f),
                        OnboardingConfig.surfaceDark.copy(alpha = 0.42f),
                    ),
                ),
            ),
    ) {
        drawCircle(
            color = OnboardingConfig.accentBlue.copy(alpha = 0.10f),
            radius = size.minDimension * 0.55f,
            center = Offset(size.width * 0.15f, size.height * 0.12f),
        )
        drawCircle(
            color = OnboardingConfig.accentGold.copy(alpha = 0.07f),
            radius = size.minDimension * 0.48f,
            center = Offset(size.width * 0.92f, size.height * 0.88f),
        )
    }
}

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = OnboardingConfig.textMuted,
    fontSize: Int = 14,
    delayMs: Long = OnboardingConfig.TYPEWRITER_DELAY_MS,
    onComplete: () -> Unit = {},
) {
    var displayed by remember(text) { mutableStateOf("") }
    LaunchedEffect(text) {
        displayed = ""
        text.forEach { char ->
            delay(delayMs)
            displayed += char
        }
        onComplete()
    }
    Text(
        text = displayed,
        color = color,
        fontSize = fontSize.sp,
        modifier = modifier,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun SetupBadge(
    code: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .border(1.dp, accent.copy(alpha = 0.64f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = code,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = OnboardingConfig.accentBlue,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.44f, label = "btnAlpha")
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = alpha)),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            letterSpacing = 0.8.sp,
            color = Color(0xFF17131E).copy(alpha = alpha),
        )
    }
}

@Composable
fun GlowCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = OnboardingConfig.accentBlue,
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) glowColor.copy(alpha = 0.78f) else OnboardingConfig.cardBorder,
        animationSpec = tween(240),
        label = "cardBorder",
    )
    val bgAlpha by animateFloatAsState(if (selected) 0.16f else 0.02f, label = "cardBg")
    Column(
        modifier = modifier
            .background(glowColor.copy(alpha = bgAlpha), RoundedCornerShape(24.dp))
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(18.dp),
        content = content,
    )
}
