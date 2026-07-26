package com.ironlog.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType

/**
 * Standard empty state used across all screens.
 * Shows an icon, headline, body text, and an optional CTA button.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    headline: String,
    body: String,
    modifier: Modifier = Modifier,
    ctaLabel: String? = null,
    onCta: (() -> Unit)? = null,
) {
    val c = useTheme()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.size(44.dp),
            )
            Text(
                headline,
                color = c.text,
                fontWeight = FontWeight(IronLogType.section.fontWeight),
                fontSize = IronLogType.section.fontSize.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                body,
                color = c.muted,
                fontSize = IronLogType.body.fontSize.sp,
                textAlign = TextAlign.Center,
            )
            if (ctaLabel != null && onCta != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onCta,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                ) {
                    Text(
                        ctaLabel,
                        fontWeight = FontWeight(IronLogType.button.fontWeight),
                        fontSize = IronLogType.button.fontSize.sp,
                        letterSpacing = IronLogType.button.letterSpacing.sp,
                    )
                }
            }
        }
    }
}
