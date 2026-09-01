package com.ironlog.app.ui.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.ui.components.CloudSummaryHost
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.Text
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy

@Composable
internal fun WorkoutCloudDebrief(
    intelligenceMode: String,
    configured: Boolean,
    requestKey: Any?,
    load: suspend () -> String,
) {
    if (intelligenceMode != "cloud_ai" || !configured) return
    val c = useTheme()
    val summary = CloudSummaryHost(true, requestKey, load)
    Surface(modifier = Modifier.fillMaxWidth(), color = c.surface, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, c.cardBorder)) {
        Column(Modifier.appPadding(12.dp), verticalArrangement = appSpacedBy(6.dp)) {
            Text("AI SESSION DEBRIEF", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(when {
                summary.loading -> "Preparing your debrief…"
                summary.text != null -> summary.text
                else -> "No debrief available. You can still save this workout."
            }, color = c.text, fontSize = 14.sp)
            if (!summary.loading && summary.text == null) {
                TextButton(onClick = summary.regenerate) { Text("Retry") }
            }
        }
    }
}
