package com.ironlog.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.*
import com.ironlog.app.ui.theme.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypographySettingsCard() {
    val context = LocalContext.current
    val store = remember(context.applicationContext) { TypographyRuntime.store(context) }
    val selection by store.selection.collectAsStateWithLifecycle()
    val c = useTheme()
    var showFamilies by rememberSaveable { mutableStateOf(false) }
    var licenseId by rememberSaveable { mutableStateOf<String?>(null) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    fun saveTypography(change: (TypographySelection) -> TypographySelection) {
        AppearancePersistence.enqueue {
            saveError = null
            try {
                // Resolve the changed field against the latest persisted selection, inside the queue.
                withContext(Dispatchers.IO) { store.update(change) }
            } catch (error: java.util.concurrent.CancellationException) {
                throw error
            } catch (error: Exception) {
                saveError = "Typography could not be saved. Please try again."
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Typography", color = c.text, style = MaterialTheme.typography.titleMedium)
        Text("Choose your reading style. Changes apply across the app and save automatically.", color = c.subtext, style = MaterialTheme.typography.bodySmall)
        saveError?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = { showFamilies = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Font: ${selection.font.name} · Change", color = c.text)
        }
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TypographyPreset.entries.forEach { preset ->
                val selected = selection.preset == preset
                Surface(color = if (selected) c.accent else c.surface, shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier.heightIn(min = 48.dp)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { saveTypography { it.copy(preset = preset) } })
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(preset.label, color = if (selected) c.textOnAccent else c.text, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        TypographyPreview()
        Text("Light softens body text and headings. Original keeps the existing weight hierarchy.", color = c.subtext, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { saveTypography { TypographySelection() } }) { Text("Reset typography") }
            TextButton(onClick = { licenseId = selection.font.id }) { Text("Font license") }
        }
    }

    if (showFamilies) {
        Dialog(onDismissRequest = { showFamilies = false }) {
            Surface(color = c.card, contentColor = c.text, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose a font", style = MaterialTheme.typography.titleLarge)
                    saveError?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall) }
                    // The sample scrolls with the list, leaving every control reachable in landscape and at large text sizes.
                    LazyColumn(Modifier.weight(1f).selectableGroup()) {
                        item {
                            Text("21 bundled fonts · works offline", color = c.subtext, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            TypographyPreview()
                            Spacer(Modifier.height(12.dp))
                        }
                        items(TypographyFonts.all, key = { it.id }) { font ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .selectable(selected = selection.font.id == font.id, role = Role.RadioButton, onClick = { saveTypography { it.copy(fontId = font.id) } })
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selection.font.id == font.id, onClick = null)
                                Text(
                                    font.name,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    fontFamily = TypographySelection(font.id, selection.preset).fontFamily(),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                    TextButton(onClick = { showFamilies = false }, modifier = Modifier.align(Alignment.End)) { Text("Done") }
                }
            }
        }
    }

    licenseId?.let { id ->
        val license by produceState("Loading license…", id) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.assets.open("font_licenses/$id.txt").bufferedReader().use { it.readText() } }
                    .getOrDefault("The bundled license could not be opened.")
            }
        }
        AlertDialog(
            onDismissRequest = { licenseId = null },
            containerColor = c.card,
            title = { Text("${TypographyFonts.resolve(id).name} license") },
            text = { Text(license, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { licenseId = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun TypographyPreview() {
    val c = useTheme()
    Surface(color = c.surface, contentColor = c.text, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Built for your next rep.", style = MaterialTheme.typography.titleMedium)
            Text("Track your training. See your progress.", style = MaterialTheme.typography.bodyMedium)
            Text("120 kg  ·  3 × 8  ·  01:30", style = MaterialTheme.typography.labelLarge)
        }
    }
}
