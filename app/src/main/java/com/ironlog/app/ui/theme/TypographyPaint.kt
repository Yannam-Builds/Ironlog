package com.ironlog.app.ui.theme

import android.content.Context
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

fun Paint.applyTypography(context: Context, selection: TypographySelection, originalWeight: Int = 400) {
    typeface = context.resources.getFont(selection.font.resourceId())
    fontVariationSettings = "'wght' ${selection.weightFor(originalWeight)}"
    isFakeBoldText = false
}

@Composable
fun rememberTypographyPaint(originalWeight: Int = 400): Paint {
    val context = LocalContext.current
    val selection = LocalTypographySelection.current
    return remember(context, selection, originalWeight) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { applyTypography(context, selection, originalWeight) }
    }
}
