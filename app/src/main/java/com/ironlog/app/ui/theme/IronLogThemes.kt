package com.ironlog.app.ui.theme

import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// FIXED: 4 — Elevation contract (keep consistent across all screens):
// c.bg       = page background (LazyColumn/screen fill)
// c.surface  = subtle fill for inputs, chips, tags, secondary containers
// c.card     = primary lifted card containers
// c.card + c.cardBorder border = standard bordered card

/** Translation of themes.js. PlatformColor/Monet tokens map to dynamic color integration points. */
@Immutable
data class IronLogThemeTokens(
    val name: String,
    val bg: Color,
    val card: Color,
    val cardBorder: Color,
    val surface: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentBorder: Color,
    val text: Color,
    val subtext: Color,
    val muted: Color,
    val faint: Color,
    val tabBg: Color,
    val gold: Color,
    val textOnAccent: Color,
    val onCard: Color,
    val ghostText: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val onDanger: Color,
    val chartPrimary: Color,
    val chartSecondary: Color,
    val chartTertiary: Color,
)

object IronLogThemes {
    const val DEFAULT_THEME = "dark"

    val BurntTerracotta = IronLogThemeTokens(
        name = "BURNT_TERRACOTTA", bg = Color(0xFF161311), card = Color(0xFF221F1D), cardBorder = Color(0xFF383432), surface = Color(0xFF221F1D),
        accent = Color(0xFFFFB77D), accentSoft = Color(0x33FFB77D), accentBorder = Color(0x66FFB77D), text = Color(0xFFE9E1DD), subtext = Color(0xFFD2C3BA),
        muted = Color(0xFFA38C7C), faint = Color(0xFF383432), tabBg = Color(0xFF161311), gold = Color(0xFFEAC33E), textOnAccent = Color(0xFF4D2600),
        onCard = Color(0xFFE9E1DD), ghostText = Color(0xFF7A665A), success = Color(0xFF8FCA88), warning = Color(0xFFEAC33E), danger = Color(0xFFFFB4AB),
        info = Color(0xFF90CDFF), onDanger = Color(0xFF690005), chartPrimary = Color(0xFFFFB77D), chartSecondary = Color(0xFF90CDFF), chartTertiary = Color(0xFFEAC33E),
    )

    val CrimsonSteel = IronLogThemeTokens(
        name = "CRIMSON_STEEL", bg = Color(0xFF141313), card = Color(0xFF211F1F), cardBorder = Color(0xFF363434), surface = Color(0xFF211F1F),
        accent = Color(0xFFFFB4AB), accentSoft = Color(0x33FFB4AB), accentBorder = Color(0x66FFB4AB), text = Color(0xFFE6E1E1), subtext = Color(0xFFCFC8C8),
        muted = Color(0xFFAC8884), faint = Color(0xFF363434), tabBg = Color(0xFF141313), gold = Color(0xFFCFB26B), textOnAccent = Color(0xFF690005),
        onCard = Color(0xFFE6E1E1), ghostText = Color(0xFF7E6B69), success = Color(0xFF9ACB9A), warning = Color(0xFFE1BC68), danger = Color(0xFFFFB4AB),
        info = Color(0xFF90CDFF), onDanger = Color(0xFF690005), chartPrimary = Color(0xFFFFB4AB), chartSecondary = Color(0xFF90CDFF), chartTertiary = Color(0xFFC6C5CF),
    )

    val DeepForest = IronLogThemeTokens(
        name = "DEEP_FOREST", bg = Color(0xFF121412), card = Color(0xFF1E201E), cardBorder = Color(0xFF333533), surface = Color(0xFF1E201E),
        accent = Color(0xFFB0CFAD), accentSoft = Color(0x33B0CFAD), accentBorder = Color(0x66B0CFAD), text = Color(0xFFE2E3DF), subtext = Color(0xFFC3C8BF),
        muted = Color(0xFF8D928A), faint = Color(0xFF333533), tabBg = Color(0xFF121412), gold = Color(0xFFD6BC6A), textOnAccent = Color(0xFF1D361E),
        onCard = Color(0xFFE2E3DF), ghostText = Color(0xFF6F766D), success = Color(0xFFB0CFAD), warning = Color(0xFFD6BC6A), danger = Color(0xFFFFB4AB),
        info = Color(0xFF8EC9BE), onDanger = Color(0xFF690005), chartPrimary = Color(0xFFB0CFAD), chartSecondary = Color(0xFF8EC9BE), chartTertiary = Color(0xFFBECABC),
    )

    val ElectricLemon = IronLogThemeTokens(
        name = "ELECTRIC_LEMON", bg = Color(0xFF17130A), card = Color(0xFF241F15), cardBorder = Color(0xFF3A3429), surface = Color(0xFF241F15),
        accent = Color(0xFFFFD165), accentSoft = Color(0x33FFD165), accentBorder = Color(0x66FFD165), text = Color(0xFFECE1D1), subtext = Color(0xFFD9C9B0),
        muted = Color(0xFF9B8F79), faint = Color(0xFF3A3429), tabBg = Color(0xFF17130A), gold = Color(0xFFFFD165), textOnAccent = Color(0xFF3F2E00),
        onCard = Color(0xFFECE1D1), ghostText = Color(0xFF7A705E), success = Color(0xFFC0D58D), warning = Color(0xFFFFD165), danger = Color(0xFFFFB4AB),
        info = Color(0xFFADDDFF), onDanger = Color(0xFF690005), chartPrimary = Color(0xFFFFD165), chartSecondary = Color(0xFFADDDFF), chartTertiary = Color(0xFFC6C6CF),
    )

    val MidnightTeal = IronLogThemeTokens(
        name = "MIDNIGHT_TEAL", bg = Color(0xFF0B1326), card = Color(0xFF171F33), cardBorder = Color(0xFF2D3449), surface = Color(0xFF171F33),
        accent = Color(0xFF6BD8CB), accentSoft = Color(0x336BD8CB), accentBorder = Color(0x666BD8CB), text = Color(0xFFDAE2FD), subtext = Color(0xFFBFCBEA),
        muted = Color(0xFF879391), faint = Color(0xFF2D3449), tabBg = Color(0xFF0B1326), gold = Color(0xFFD2C37D), textOnAccent = Color(0xFF003732),
        onCard = Color(0xFFDAE2FD), ghostText = Color(0xFF6C7895), success = Color(0xFF6BD8CB), warning = Color(0xFFD2C37D), danger = Color(0xFFFFB2B7),
        info = Color(0xFF7BD0FF), onDanger = Color(0xFF690005), chartPrimary = Color(0xFF6BD8CB), chartSecondary = Color(0xFF7BD0FF), chartTertiary = Color(0xFFFFB2B7),
    )

    val ObsidianSilver = IronLogThemeTokens(
        name = "OBSIDIAN_SILVER", bg = Color(0xFF131313), card = Color(0xFF252525), cardBorder = Color(0xFF3D3D3D), surface = Color(0xFF252525),
        accent = Color(0xFFFDFDFC), accentSoft = Color(0x33FDFDFC), accentBorder = Color(0x66C6C6C6), text = Color(0xFFE2E2E2), subtext = Color(0xFFC4C7C8),
        muted = Color(0xFF8E9192), faint = Color(0xFF353535), tabBg = Color(0xFF131313), gold = Color(0xFFCACACA), textOnAccent = Color(0xFF2F3131),
        onCard = Color(0xFFE2E2E2), ghostText = Color(0xFF727575), success = Color(0xFFBBD7BB), warning = Color(0xFFE5D39C), danger = Color(0xFFFFB4AB),
        info = Color(0xFFAACEEE), onDanger = Color(0xFF690005), chartPrimary = Color(0xFFFDFDFC), chartSecondary = Color(0xFFC8C6C5), chartTertiary = Color(0xFFF7FEFF),
    )

    val RoyalAmethyst = IronLogThemeTokens(
        name = "ROYAL_AMETHYST", bg = Color(0xFF0B1326), card = Color(0xFF171F33), cardBorder = Color(0xFF2D3449), surface = Color(0xFF171F33),
        accent = Color(0xFFD2BBFF), accentSoft = Color(0x33D2BBFF), accentBorder = Color(0x66D2BBFF), text = Color(0xFFDAE2FD), subtext = Color(0xFFC9C4E6),
        muted = Color(0xFF958DA1), faint = Color(0xFF2D3449), tabBg = Color(0xFF0B1326), gold = Color(0xFFD9BF74), textOnAccent = Color(0xFF3F008E),
        onCard = Color(0xFFDAE2FD), ghostText = Color(0xFF726A88), success = Color(0xFFBFCBFF), warning = Color(0xFFD9BF74), danger = Color(0xFFFFB4AB),
        info = Color(0xFFC4C1FB), onDanger = Color(0xFF690005), chartPrimary = Color(0xFFD2BBFF), chartSecondary = Color(0xFFCEBDFF), chartTertiary = Color(0xFFC4C1FB),
    )

    val TitaniumBlue = IronLogThemeTokens(
        name = "TITANIUM_BLUE", bg = Color(0xFF11131C), card = Color(0xFF1D1F29), cardBorder = Color(0xFF33343E), surface = Color(0xFF1D1F29),
        accent = Color(0xFFB8C3FF), accentSoft = Color(0x33B8C3FF), accentBorder = Color(0x66B8C3FF), text = Color(0xFFE2E1EF), subtext = Color(0xFFC4C5D9),
        muted = Color(0xFF8E90A2), faint = Color(0xFF33343E), tabBg = Color(0xFF11131C), gold = Color(0xFFD1C58B), textOnAccent = Color(0xFF002388),
        onCard = Color(0xFFE2E1EF), ghostText = Color(0xFF717487), success = Color(0xFF9BC0FF), warning = Color(0xFFD1C58B), danger = Color(0xFFFFB4AB),
        info = Color(0xFF90CDFF), onDanger = Color(0xFF690005), chartPrimary = Color(0xFFB8C3FF), chartSecondary = Color(0xFFB7C8E1), chartTertiary = Color(0xFFBEC6E0),
    )

    val Amoled = IronLogThemeTokens(
        name = "AMOLED", bg = Color(0xFF000000), card = Color(0xFF080808), cardBorder = Color(0xFF111111),
        surface = Color(0xFF0D0D0D), accent = Color(0xFFFF4500), accentSoft = Color(0x22FF4500), accentBorder = Color(0x44FF4500),
        text = Color.White, subtext = Color(0xFFAAAAAA), muted = Color(0xFF777777), faint = Color(0xFF1A1A1A), tabBg = Color.Black,
        gold = Color(0xFFFFD700), textOnAccent = Color.White, onCard = Color.White, ghostText = Color(0xFF4D4D4D),
        success = Color(0xFF00C170), warning = Color(0xFFFFB020), danger = Color(0xFFFF453A), info = Color(0xFF3B8FFF), onDanger = Color.White,
        chartPrimary = Color(0xFFFF4500), chartSecondary = Color(0xFF3B8FFF), chartTertiary = Color(0xFF00C170),
    )

    val Dark = IronLogThemeTokens(
        name = "DARK", bg = Color(0xFF121212), card = Color(0xFF1C1C1E), cardBorder = Color(0xFF2C2C2E),
        surface = Color(0xFF252527), accent = Color(0xFFFF4500), accentSoft = Color(0x22FF4500), accentBorder = Color(0x55FF4500),
        text = Color.White, subtext = Color(0xFFBBBBBB), muted = Color(0xFF909090), faint = Color(0xFF2C2C2E), tabBg = Color(0xFF1C1C1E),
        gold = Color(0xFFFFD700), textOnAccent = Color.White, onCard = Color.White, ghostText = Color(0xFF555555),
        success = Color(0xFF00C170), warning = Color(0xFFFFB020), danger = Color(0xFFFF453A), info = Color(0xFF3B8FFF), onDanger = Color.White,
        chartPrimary = Color(0xFFFF4500), chartSecondary = Color(0xFF3B8FFF), chartTertiary = Color(0xFF00C170),
    )

    val MonetFallback = IronLogThemeTokens(
        name = "MONET", bg = Color(0xFF1A1A2E), card = Color(0xFF252540), cardBorder = Color(0xFF3E3E5E), surface = Color(0xFF1E1E38),
        accent = Color(0xFFD0BCFF), accentSoft = Color(0xFF3D2D6B), accentBorder = Color(0xFF7B5DB8),
        text = Color(0xFFF0EEFF), subtext = Color(0xFFCCC8E8), muted = Color(0xFF8884A8), faint = Color(0xFF3E3E5E), tabBg = Color(0xFF1A1A2E),
        gold = Color(0xFFF7D060), textOnAccent = Color(0xFF1A0B3B), onCard = Color(0xFFE8E4FF), ghostText = Color(0xFF55527A),
        success = Color(0xFF79C98D), warning = Color(0xFFF7D060), danger = Color(0xFFFF8E8E), info = Color(0xFFA0C4FF), onDanger = Color(0xFF120C1F),
        chartPrimary = Color(0xFFD0BCFF), chartSecondary = Color(0xFFA0C4FF), chartTertiary = Color(0xFF79C98D),
    )

    val Light = IronLogThemeTokens(
        name = "LIGHT", bg = Color(0xFFF2F2F7), card = Color.White, cardBorder = Color(0xFFC8C8CC), surface = Color(0xFFF2F2F7),
        accent = Color(0xFFD42010), accentSoft = Color(0x15D42010), accentBorder = Color(0x44D42010),
        text = Color(0xFF111111), subtext = Color(0xFF333333), muted = Color(0xFF555555), faint = Color(0xFFE0E0E4), tabBg = Color.White,
        gold = Color(0xFFB8720A), textOnAccent = Color.White, onCard = Color(0xFF111111), ghostText = Color(0xFF999999),
        success = Color(0xFF1B8F4B), warning = Color(0xFFA15E00), danger = Color(0xFFC11E16), info = Color(0xFF0A63C9), onDanger = Color.White,
        chartPrimary = Color(0xFFD42010), chartSecondary = Color(0xFF0A63C9), chartTertiary = Color(0xFF1B8F4B),
    )

    fun byName(name: String?): IronLogThemeTokens = when (name) {
        "burnt_terracotta" -> BurntTerracotta
        "crimson_steel" -> CrimsonSteel
        "deep_forest" -> DeepForest
        "electric_lemon" -> ElectricLemon
        "midnight_teal" -> MidnightTeal
        "obsidian_silver" -> ObsidianSilver
        "royal_amethyst" -> RoyalAmethyst
        "titanium_blue" -> TitaniumBlue
        "dark" -> Dark
        "light" -> Light
        "monet" -> MonetFallback // Replace with Material You dynamic color tokens on Android 12+ during integration.
        "amoled" -> Amoled
        null -> Dark
        else -> Dark
    }
}

object IronLogRadius { const val xs = 6; const val sm = 10; const val md = 14; const val lg = 18; const val xl = 24; const val full = 999 }
// Elevation contract:
// c.bg       = page background (LazyColumn fill)
// c.surface  = subtle fill for inputs, chips, tags, secondary containers
// c.card     = primary lifted card containers
// c.card + c.cardBorder border = standard bordered card

object IronLogType {
    data class Token(val fontSize: Int, val fontWeight: Int, val letterSpacing: Double, val lineHeight: Int)
    val display = Token(32, 900, -0.5, 36)
    val metric  = Token(28, 900, -1.0, 32)   // large numeric displays (body weight, stat values)
    val title   = Token(20, 900,  0.3, 26)
    val section = Token(16, 800,  0.2, 22)
    val body    = Token(14, 500,  0.0, 20)
    val meta    = Token(12, 500,  0.1, 16)
    val eyebrow = Token(10, 800,  2.5, 14)
    val button  = Token(12, 800,  1.5, 16)
    val micro   = Token( 9, 800,  1.2, 12)
}
