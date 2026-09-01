package com.ironlog.app.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogThemeTokens

@Immutable
internal data class IronLogSwitchPalette(
    val checkedThumb: Color,
    val checkedTrack: Color,
    val checkedBorder: Color,
    val uncheckedThumb: Color,
    val uncheckedTrack: Color,
    val uncheckedBorder: Color,
    val disabledCheckedThumb: Color,
    val disabledCheckedTrack: Color,
    val disabledCheckedBorder: Color,
    val disabledUncheckedThumb: Color,
    val disabledUncheckedTrack: Color,
    val disabledUncheckedBorder: Color,
)

internal fun ironLogSwitchPalette(theme: IronLogThemeTokens) = IronLogSwitchPalette(
    checkedThumb = theme.accent,
    checkedTrack = theme.accentSoft,
    checkedBorder = theme.accentBorder,
    uncheckedThumb = theme.subtext,
    uncheckedTrack = theme.surface,
    uncheckedBorder = theme.muted,
    disabledCheckedThumb = theme.subtext,
    disabledCheckedTrack = theme.accentSoft,
    disabledCheckedBorder = theme.accentBorder,
    disabledUncheckedThumb = theme.muted,
    disabledUncheckedTrack = theme.bg,
    disabledUncheckedBorder = theme.cardBorder,
)

/**
 * App-wide switch visual contract. Inactive switches retain a clear handle,
 * track, and outline in every IronLog theme instead of inheriting Material
 * roles that are intentionally identical in several palettes.
 */
@Composable
fun IronLogSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = ironLogSwitchPalette(useTheme())
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = palette.checkedThumb,
            checkedTrackColor = palette.checkedTrack,
            checkedBorderColor = palette.checkedBorder,
            uncheckedThumbColor = palette.uncheckedThumb,
            uncheckedTrackColor = palette.uncheckedTrack,
            uncheckedBorderColor = palette.uncheckedBorder,
            disabledCheckedThumbColor = palette.disabledCheckedThumb,
            disabledCheckedTrackColor = palette.disabledCheckedTrack,
            disabledCheckedBorderColor = palette.disabledCheckedBorder,
            disabledUncheckedThumbColor = palette.disabledUncheckedThumb,
            disabledUncheckedTrackColor = palette.disabledUncheckedTrack,
            disabledUncheckedBorderColor = palette.disabledUncheckedBorder,
        ),
    )
}
