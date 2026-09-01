package com.ironlog.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironlog.app.data.repository.ImportExportRepository
import com.ironlog.app.data.repository.ImportPreview
import com.ironlog.app.data.repository.RestoreImpact
import com.ironlog.app.data.repository.canConfirmRestore
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.Text
import kotlinx.coroutines.launch

/** Every full-backup entry point shares the same consent and awaited transaction. */
@Composable
fun BackupRestoreDialog(
    payload: String,
    repository: ImportExportRepository,
    onDismiss: () -> Unit,
    trustedRecoveryFile: java.io.File? = null,
    onComplete: (ImportPreview) -> Unit,
) {
    val colors = useTheme()
    val scope = rememberCoroutineScope()
    var mode by remember(payload) { mutableStateOf("merge") }
    var impact by remember(payload) { mutableStateOf<RestoreImpact?>(null) }
    var error by remember(payload) { mutableStateOf<String?>(null) }
    var confirmation by remember(payload) { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    fun selectMode(value: String) {
        if (working || value == mode) return
        impact = null
        confirmation = ""
        mode = value
    }
    LaunchedEffect(payload, mode) {
        impact = null
        error = null
        runCatching { repository.previewRestore(payload, mode) }
            .onSuccess { impact = it }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                error = it.message ?: "Could not read backup"
            }
    }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        containerColor = colors.card,
        title = { Text("Restore backup", color = colors.text) },
        text = {
            Column(
                Modifier.selectableGroup().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf("merge" to "Merge · keep other saved data", "replace" to "Replace · remove data absent from backup").forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = mode == value,
                                enabled = !working,
                                role = Role.RadioButton,
                                onClick = { selectMode(value) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == value, onClick = null, enabled = !working)
                        Text(label, color = colors.text, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Text(if (mode == "merge") "Matching stable IDs are updated. Other saved rows stay."
                    else "Plans, workouts, measurements, photo records and settings absent from this backup will be removed. A private recovery snapshot is saved before replacement.", color = colors.subtext)
                if (trustedRecoveryFile != null) Text("Private recovery snapshot: its saved local image copies will also be restored.", color = colors.text)
                val preview = impact?.preview
                if (preview == null && error == null) Text("Checking backup…", color = colors.subtext)
                preview?.let {
                    Text("Ready: ${it.workouts} workouts · ${it.plans} plans · ${it.sets} sets · ${it.bodyMeasurements} measurements", color = colors.text)
                    it.errors.forEach { message -> Text(message, color = colors.danger) }
                    it.warnings.forEach { message -> Text(message, color = colors.subtext) }
                    if (mode == "replace") {
                        Text(impact!!.removed.entries.joinToString("\n", prefix = "Rows removed:\n") { (domain, count) ->
                            "${domain.replace('_', ' ')}: $count"
                        }.takeIf { impact!!.removed.isNotEmpty() } ?: "No unique rows will be removed.", color = colors.text)
                        if (!it.replacementSafe) Text("Replacement is blocked for this backup. Review warnings or use Merge.", color = colors.danger)
                        OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Type REPLACE") }, singleLine = true, enabled = !working, modifier = Modifier.fillMaxWidth())
                    }
                }
                error?.let {
                    Text(it, color = colors.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working && canConfirmRestore(impact, mode, confirmation),
                onClick = {
                    val selected = impact ?: return@TextButton
                    if (working || !canConfirmRestore(selected, mode, confirmation)) return@TextButton
                    working = true
                    scope.launch {
                        runCatching { repository.runConfirmedImport(payload, selected.mode, selected.databaseFingerprint, trustedRecoveryFile) }
                            .onSuccess(onComplete)
                            .onFailure { error = it.message ?: "Restore failed. Your data was not replaced." }
                        working = false
                    }
                },
            ) { Text(if (working) "Restoring…" else if (mode == "merge") "Merge backup" else "Replace data") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") } },
    )
}
