package com.ironlog.app.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState

/**
 * Backward-compatible receiver class name; user-facing content is now Forge Fox.
 */
class WarRoomWidget : GlanceAppWidget() {
    override val stateDefinition = WidgetStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(180.dp, 180.dp),
            DpSize(220.dp, 320.dp),
            DpSize(320.dp, 160.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            ForgeFoxWidgetContent(
                state = currentState(),
                preferredLayout = ForgeWidgetLayoutClass.TALL,
            )
        }
    }
}

class WarRoomWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WarRoomWidget()
}
