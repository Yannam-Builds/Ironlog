package com.ironlog.app.ui.screens.onboarding.steps

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.ParticleField
import kotlinx.coroutines.delay

@Composable
fun Step1Awakening(onAdvance: () -> Unit) {
    var logoVisible by remember { mutableStateOf(false) }
    val logoAlpha by animateFloatAsState(
        targetValue   = if (logoVisible) 1f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label         = "logoAlpha",
    )

    LaunchedEffect(Unit) {
        logoVisible = true
        delay(OnboardingConfig.SCREEN1_AUTO_ADVANCE_MS)
        onAdvance()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark),
    ) {
        ParticleField()

        Column(
            modifier            = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text          = "IRONLOG",
                color         = OnboardingConfig.accentBlue,
                fontSize      = 36.sp,
                fontWeight    = FontWeight.Black,
                letterSpacing = 8.sp,
                modifier      = Modifier.alpha(logoAlpha),
            )

            Spacer(Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.onb_awakening_line1),
                color = OnboardingConfig.textMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                color = OnboardingConfig.accentBlue,
                trackColor = OnboardingConfig.cardBorder,
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(4.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.onb_awakening_line2),
                color = OnboardingConfig.textMuted.copy(alpha = 0.78f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}
