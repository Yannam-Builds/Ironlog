package com.ironlog.app.ui.components

// FIXED: 6 — ScreenHeader updated with subtitle + action slot; also used as a tab-screen page header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType

/**
 * Standard back-navigation header used on all push-navigated screens.
 * Renders: [← back arrow] [eyebrow / screen title]
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val c = useTheme()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Use IconButton instead of raw Icon + clickable to get the 48dp minimum touch target.
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = c.accent,
                modifier = Modifier.size(24.dp),
            )
        }
        Column {
            Text(
                text = title,
                color = c.text,
                fontSize = IronLogType.section.fontSize.sp,
                fontWeight = FontWeight(IronLogType.section.fontWeight),
                letterSpacing = IronLogType.section.letterSpacing.sp,
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = c.muted,
                    fontSize = IronLogType.meta.fontSize.sp,
                )
            }
        }
    }
}

/**
 * Page-level header for tab screens (no back button).
 * Renders: eyebrow label + large title + optional subtitle + optional trailing action.
 */
@Composable
fun PageHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val c = useTheme()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                eyebrow,
                color = c.muted,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
            )
            Text(
                title,
                color = c.text,
                fontSize = IronLogType.title.fontSize.sp,
                fontWeight = FontWeight(IronLogType.display.fontWeight),
                lineHeight = IronLogType.title.lineHeight.sp,
                letterSpacing = (-0.3).sp,
            )
            subtitle?.let {
                Text(
                    it,
                    color = c.muted,
                    fontSize = IronLogType.meta.fontSize.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        action?.invoke()
    }
}
