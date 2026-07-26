package com.ironlog.app.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ironlog.app.MainActivity

internal enum class ForgeWidgetLayoutClass {
    SMALL,
    MEDIUM,
    TALL,
    WIDE,
}

@Composable
internal fun ForgeFoxWidgetContent(
    state: WidgetState,
    preferredLayout: ForgeWidgetLayoutClass,
) {
    val context = LocalContext.current
    val layout = rememberForgeWidgetLayoutClass(preferredLayout)
    val presentation = forgePresentationFor(state.visualState, state.minutesUntilWorkout)
    val route = state.primaryActionRoute.ifBlank { "Home" }
    val modifier = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(presentation.background))
        .cornerRadius(if (layout == ForgeWidgetLayoutClass.SMALL) 18.dp else 22.dp)
        .clickable(
            actionStartActivity(
                Intent(context, MainActivity::class.java)
                    .putExtra("ironlog_route", route)
            )
        )
        .padding(if (layout == ForgeWidgetLayoutClass.SMALL) 10.dp else 14.dp)

    when (layout) {
        ForgeWidgetLayoutClass.SMALL -> SmallForgeWidget(state, presentation, modifier)
        ForgeWidgetLayoutClass.MEDIUM -> MediumForgeWidget(state, presentation, modifier)
        ForgeWidgetLayoutClass.TALL -> TallForgeWidget(state, presentation, modifier)
        ForgeWidgetLayoutClass.WIDE -> WideForgeWidget(state, presentation, modifier)
    }
}

@Composable
private fun rememberForgeWidgetLayoutClass(preferredLayout: ForgeWidgetLayoutClass): ForgeWidgetLayoutClass {
    val size = LocalSize.current
    return resolveForgeWidgetLayoutClass(size.width.value, size.height.value, preferredLayout)
}

@Composable
private fun SmallForgeWidget(
    state: WidgetState,
    presentation: ForgeWidgetPresentation,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        StreakLockup(state.dailyStreakDays, iconSize = 30.dp, numberSize = 34)
        Text(
            text = presentation.title,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(2.dp))
        Image(
            provider = ImageProvider(ForgeFoxWidgetAssets.mascotFor(state.visualState)),
            contentDescription = presentation.title,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun MediumForgeWidget(
    state: WidgetState,
    presentation: ForgeWidgetPresentation,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        StreakLockup(state.dailyStreakDays, iconSize = 38.dp, numberSize = 44)
        Spacer(GlanceModifier.height(4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().height(112.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.width(88.dp)) {
                Text(
                    text = presentation.title,
                    maxLines = 3,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = presentation.message,
                    maxLines = 2,
                    style = TextStyle(
                        color = ColorProvider(presentation.accent),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Image(
                provider = ImageProvider(ForgeFoxWidgetAssets.mascotFor(state.visualState)),
                contentDescription = presentation.title,
                modifier = GlanceModifier.fillMaxWidth().fillMaxHeight(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun TallForgeWidget(
    state: WidgetState,
    presentation: ForgeWidgetPresentation,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        StreakLockup(state.dailyStreakDays, iconSize = 44.dp, numberSize = 52)
        Text(
            text = presentation.title,
            maxLines = 2,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = presentation.message,
            maxLines = 2,
            style = TextStyle(
                color = ColorProvider(presentation.accent),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(4.dp))
        Image(
            provider = ImageProvider(ForgeFoxWidgetAssets.mascotFor(state.visualState)),
            contentDescription = presentation.title,
            modifier = GlanceModifier.fillMaxWidth().height(142.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(GlanceModifier.height(6.dp))
        WeeklyProgressBand(state, presentation, compact = false)
    }
}

@Composable
private fun WideForgeWidget(
    state: WidgetState,
    presentation: ForgeWidgetPresentation,
    modifier: GlanceModifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.width(164.dp)) {
            StreakLockup(state.dailyStreakDays, iconSize = 38.dp, numberSize = 46)
            Text(
                text = presentation.title,
                maxLines = 2,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = presentation.message,
                maxLines = 1,
                style = TextStyle(
                color = ColorProvider(presentation.accent),
                    fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(GlanceModifier.width(12.dp))
        Image(
            provider = ImageProvider(ForgeFoxWidgetAssets.mascotFor(state.visualState)),
            contentDescription = presentation.title,
            modifier = GlanceModifier.fillMaxWidth().fillMaxHeight(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun StreakLockup(streakDays: Int, iconSize: Dp, numberSize: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(ForgeFoxWidgetAssets.streakIcon),
            contentDescription = "Streak",
            modifier = GlanceModifier.width(iconSize).height(iconSize),
            contentScale = ContentScale.Fit,
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = streakDays.coerceAtLeast(0).toString(),
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = numberSize.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun WeeklyProgressBand(
    state: WidgetState,
    presentation: ForgeWidgetPresentation,
    compact: Boolean,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(14.dp)
            .background(ColorProvider(Color(0x22000000)))
            .padding(
                vertical = if (compact) 4.dp else 6.dp,
                horizontal = if (compact) 4.dp else 6.dp,
            )
    ) {
        WeeklyProgressRow(state, presentation, compact)
    }
}

@Composable
private fun WeeklyProgressRow(
    state: WidgetState,
    presentation: ForgeWidgetPresentation,
    compact: Boolean,
) {
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val completion = if (state.weeklyCompletion.size >= 7) state.weeklyCompletion.take(7)
    else List(7) { index -> index < state.weekSessionsCount.coerceIn(0, 7) }
    val dotSize = if (compact) 10.dp else 16.dp
    val daySize = if (compact) 8 else 10

    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        days.forEachIndexed { index, day ->
            Column(
                modifier = GlanceModifier.width(if (compact) 17.dp else 27.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = day,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Color(0xCCFFFFFF)),
                        fontSize = daySize.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Box(
                    modifier = GlanceModifier
                        .width(dotSize)
                        .height(dotSize)
                        .cornerRadius(99.dp)
                        .background(
                            ColorProvider(
                                if (completion[index]) presentation.accent else Color(0x33FFFFFF)
                            )
                        )
                ) {}
            }
        }
    }
}
