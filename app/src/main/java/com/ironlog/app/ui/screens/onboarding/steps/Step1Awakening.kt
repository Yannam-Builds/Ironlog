package com.ironlog.app.ui.screens.onboarding.steps

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.ironlog.app.ui.theme.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.R
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.ParticleField
import com.ironlog.app.ui.screens.onboarding.SetupReward
import com.ironlog.app.ui.screens.onboarding.WelcomeHeroLayoutSpec
import com.ironlog.app.ui.screens.onboarding.onboardingContentLayoutSpec
import com.ironlog.app.ui.screens.onboarding.welcomeHeroLayoutSpec
import kotlin.math.roundToInt

@Composable
fun Step1Awakening(
    onAdvance: () -> Unit,
    onSkip: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark),
    ) {
        ParticleField()

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 8 }),
            modifier = Modifier.fillMaxSize(),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val heroLayout = welcomeHeroLayoutSpec(
                    availableHeightDp = maxHeight.value.roundToInt(),
                    fontScale = LocalDensity.current.fontScale,
                )
                val pageLayout = onboardingContentLayoutSpec(
                    widthDp = maxWidth.value.roundToInt(),
                    fontScale = LocalDensity.current.fontScale,
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .appPadding(
                            start = pageLayout.horizontalPaddingDp.dp,
                            top = pageLayout.topPaddingDp.dp,
                            end = pageLayout.horizontalPaddingDp.dp,
                            bottom = pageLayout.bottomPaddingDp.dp,
                        ),
                    verticalArrangement = appSpacedBy(if (heroLayout.isCompact) 20.dp else 28.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "IRON",
                                color = OnboardingConfig.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                            )
                            Text(
                                "LOG",
                                color = OnboardingConfig.accentBlue,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                            )
                        }
                        Spacer(Modifier.height(appGapDp(28.dp)))
                        Text(
                            "Train with evidence.\nProgress like a game.",
                            color = OnboardingConfig.textPrimary,
                            fontSize = if (heroLayout.isCompact) 34.sp else 39.sp,
                            lineHeight = if (heroLayout.isCompact) 37.sp else 41.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1.1).sp,
                        )
                        Spacer(Modifier.height(appGapDp(14.dp)))
                        Text(
                            "IronLog turns verified training into adaptive programming, recovery guidance and a progression ledger that cannot be faked.",
                            color = OnboardingConfig.textMuted,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        )
                    }

                    WelcomeMascotViewport(layout = heroLayout)

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = appSpacedBy(8.dp),
                        ) {
                            WelcomeSignal("PRIVATE", "Local first", Modifier.weight(1f))
                            WelcomeSignal("ADAPTIVE", "Recovery aware", Modifier.weight(1f))
                            WelcomeSignal("VERIFIED", "Earned XP", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(appGapDp(18.dp)))
                        GlowButton(text = "Build my training system", onClick = onAdvance)
                        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                            Text("Explore with sensible defaults", color = OnboardingConfig.textMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeMascotViewport(layout: WelcomeHeroLayoutSpec) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.mascotViewportHeightDp.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.forgefox_17_flexing),
                contentDescription = "Forge Fox flexing",
                modifier = Modifier.size(layout.mascotSizeDp.dp),
            )
        }
        Spacer(Modifier.height(layout.bannerGapDp.dp))
        SetupReward(
            text = "Your first verified workout starts the ledger",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WelcomeSignal(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(OnboardingConfig.surfaceDark.copy(alpha = 0.88f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Text(label, color = OnboardingConfig.accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(appGapDp(4.dp)))
        Text(value, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
