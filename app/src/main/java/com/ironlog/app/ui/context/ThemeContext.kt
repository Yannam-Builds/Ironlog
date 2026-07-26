package com.ironlog.app.ui.context

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.ironlog.app.ui.theme.IronLogThemeTokens
import com.ironlog.app.ui.theme.IronLogThemes

/** Translation of ThemeContext.js. Monet wallpaper epoch should be triggered by platform integration. */
val LocalIronLogTheme = staticCompositionLocalOf<IronLogThemeTokens> { IronLogThemes.ObsidianSilver }

@Composable
fun ThemeProvider(themeName: String = IronLogThemes.DEFAULT_THEME, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val resolved = if (themeName == "monet" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val cs = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        IronLogThemeTokens(
            name = "MONET_DYNAMIC",
            bg = cs.background,
            card = cs.surface,
            cardBorder = cs.outlineVariant,
            surface = cs.surfaceContainerHigh,
            accent = cs.primary,
            accentSoft = cs.primary.copy(alpha = 0.2f),
            accentBorder = cs.primary.copy(alpha = 0.45f),
            text = cs.onSurface,
            subtext = cs.onSurfaceVariant,
            muted = cs.onSurfaceVariant.copy(alpha = 0.85f),
            faint = cs.surfaceContainerHighest,
            tabBg = cs.surfaceContainer,
            gold = cs.tertiary,
            textOnAccent = cs.onPrimary,
            onCard = cs.onSurface,
            ghostText = cs.onSurfaceVariant.copy(alpha = 0.65f),
            success = cs.tertiary,
            warning = cs.secondary,
            danger = cs.error,
            info = cs.secondary,
            onDanger = cs.onError,
            chartPrimary = cs.primary,
            chartSecondary = cs.secondary,
            chartTertiary = cs.tertiary,
        )
    } else {
        IronLogThemes.byName(themeName)
    }
    CompositionLocalProvider(LocalIronLogTheme provides resolved) {
        content()
    }
}

@Composable
fun useTheme(): IronLogThemeTokens = LocalIronLogTheme.current
