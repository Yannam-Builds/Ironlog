package com.ironlog.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpacingSettingsCard() {
    val context = LocalContext.current
    val stores = remember(context.applicationContext) {
        SpacingRole.entries.associateWith { SpacingRuntime.store(context, it) }
    }
    val cards by stores.getValue(SpacingRole.CARDS).scale.collectAsStateWithLifecycle()
    val padding by stores.getValue(SpacingRole.PADDING).scale.collectAsStateWithLifecycle()
    val content by stores.getValue(SpacingRole.CONTENT).scale.collectAsStateWithLifecycle()
    val c = useTheme()
    var saveError by remember { mutableStateOf<String?>(null) }

    fun save(store: SpacingStore) {
        val requested = store.prepareSave()
        AppearancePersistence.enqueue {
            try {
                withContext(Dispatchers.IO) { store.persistPreview(requested) }
            } catch (error: java.util.concurrent.CancellationException) {
                throw error
            } catch (error: Exception) {
                saveError = "Spacing could not be saved. Please try again."
            }
        }
    }
    fun preset(value: Float) {
        saveError = null
        stores.values.forEach { it.preview(value); save(it) }
    }

    // Settings controls keep a steady rhythm while the live preview and app respond.
    Column(Modifier.fillMaxWidth().padding(top = 20.dp).semantics { contentDescription = "UI spacing" },
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Spacing", color = c.text, style = MaterialTheme.typography.titleMedium)
        Text("Tune your layout. Text size, diagrams and touch targets stay unchanged.",
            color = c.subtext, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Compact" to .85f, "Balanced" to 1f, "Roomy" to 1.25f).forEach { (name, value) ->
                FilterChip(selected = cards == value && padding == value && content == value,
                    onClick = { preset(value) }, label = { Text(name) })
            }
        }
        listOf(
            Triple(SpacingRole.CARDS, "Between cards", cards),
            Triple(SpacingRole.PADDING, "Content padding", padding),
            Triple(SpacingRole.CONTENT, "Text & controls", content),
        ).forEach { (role, label, scale) ->
            val percent = (scale * 100f).roundToInt()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$label · $percent%", color = c.text, style = MaterialTheme.typography.labelLarge)
                Slider(value = scale,
                    onValueChange = { saveError = null; stores.getValue(role).preview(it) },
                    onValueChangeFinished = { save(stores.getValue(role)) },
                    valueRange = SpacingSettings.MIN..SpacingSettings.MAX, steps = 7,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                        contentDescription = label
                        stateDescription = "$percent percent spacing"
                    })
            }
        }
        saveError?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall) }
        CompositionLocalProvider(
            LocalCardSpacingScale provides cards,
            LocalPaddingSpacingScale provides padding,
            LocalContentSpacingScale provides content,
        ) {
            Column(verticalArrangement = appCardSpacedBy(12.dp)) {
                listOf("Your workout" to "3 exercises · 12 sets", "Recovery" to "Your next session, at a glance").forEach { (title, subtitle) ->
                    Surface(color = c.surface, contentColor = c.text, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().appPadding(16.dp), verticalArrangement = appSpacedBy(6.dp)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.subtext)
                        }
                    }
                }
            }
        }
        TextButton(onClick = { preset(1f) }, enabled = cards != 1f || padding != 1f || content != 1f) {
            Text("Reset all spacing")
        }
    }
}
