package com.ironlog.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ironlog.app.ui.context.useTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Natural pixel dimensions of the source assets (same as RN reference)
private const val IRON_NAT_W = 330f
private const val LOG_NAT_W  = 265f
private const val LOGO_NAT_H = 85f
private const val TOTAL_NAT_W = IRON_NAT_W + LOG_NAT_W  // 595

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    val c = useTheme()
    val sw = LocalConfiguration.current.screenWidthDp.toFloat()

    // Match RN sizing: total logo = 52 % of screen width
    val scale  = (sw * 0.52f) / TOTAL_NAT_W
    val ironW  = (IRON_NAT_W * scale).dp
    val logW   = (LOG_NAT_W  * scale).dp
    val logoH  = (LOGO_NAT_H * scale).dp

    val ironX  = remember { Animatable(-sw) }
    val logX   = remember { Animatable(sw) }
    val shakeX = remember { Animatable(0f) }
    val scope  = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(250)
        scope.launch { ironX.animateTo(0f, tween(700, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))) }
        scope.launch { logX.animateTo(0f,  tween(700, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))) }
        delay(750)
        listOf(-5f to 50, 5f to 60, -3f to 50, 2f to 50, 0f to 40).forEach { (x, ms) ->
            shakeX.animateTo(x, tween(ms))
        }
        delay(300)
        onFinish()
    }

    Box(
        Modifier.fillMaxSize().background(c.bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Logo row
            Row(
                Modifier.graphicsLayer { translationX = shakeX.value },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = com.ironlog.app.R.drawable.logo_iron),
                    contentDescription = "IRON",
                    modifier = Modifier
                        .graphicsLayer { translationX = ironX.value }
                        .width(ironW)
                        .height(logoH),
                    contentScale = ContentScale.Fit,
                )
                Image(
                    painter = painterResource(id = com.ironlog.app.R.drawable.logo_log),
                    contentDescription = "LOG",
                    modifier = Modifier
                        .graphicsLayer { translationX = logX.value }
                        .width(logW)
                        .height(logoH),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(c.accent),
                )
            }

            // Loading dots
            LoadingDots()
        }
    }
}

@Composable
private fun LoadingDots() {
    val c = useTheme()
    val infiniteTransition = rememberInfiniteTransition(label = "splashDots")

    // Three dots staggered by 160ms each
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, delayMillis = 0, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "dot1",
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, delayMillis = 160, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "dot2",
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, delayMillis = 320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "dot3",
    )

    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(dot1, dot2, dot3).forEach { t ->
            Box(
                Modifier
                    .size(6.dp)
                    .background(
                        color = c.accent.copy(alpha = 0.30f + 0.70f * t),
                        shape = CircleShape,
                    ),
            )
        }
    }
}
