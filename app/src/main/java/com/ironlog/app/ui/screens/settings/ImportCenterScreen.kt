package com.ironlog.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.data.repository.ImportExportRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val IMPORT_SOURCES = listOf(
    Triple("ironlog_json",    "IronLog Backup",  arrayOf("application/json", "text/plain", "*/*")),
    Triple("strong_csv",      "Strong CSV",      arrayOf("text/comma-separated-values", "text/csv", "text/plain", "*/*")),
    Triple("hevy_csv",        "Hevy CSV",        arrayOf("text/comma-separated-values", "text/csv", "text/plain", "*/*")),
    Triple("openweight_json", "OpenWeight JSON", arrayOf("application/json", "text/plain", "*/*")),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportCenterScreen(
    onBack: () -> Unit = {},
    onOpenDataPortability: (String) -> Unit = {},
    onRestoreComplete: () -> Unit = {},
) {
    val c = useTheme()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf("ironlog_json") }
    var pickedText by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<CsvPreview?>(null) }
    var status by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    val repo = remember { WorkoutRepository() }
    val importExportRepo = remember { ImportExportRepository() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val text = runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            }.getOrElse { e ->
                withContext(Dispatchers.Main) { status = "Could not read file: ${e.message}" }
                return@launch
            }
            val pv = buildImportPreview(text, selected)
            withContext(Dispatchers.Main) { pickedText = text; preview = pv; status = "" }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(title = "IMPORT", onBack = onBack) }

        item {
            Text("Choose your source format, then pick the file from your device. A preview appears before anything is written.",
                color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
        }

        // Source format selector
        item {
            Text("SOURCE FORMAT", color = c.accent, fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IMPORT_SOURCES.forEach { (id, label, _) ->
                    val sel = selected == id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) c.accentSoft else c.card)
                            .border(1.dp, if (sel) c.accentBorder else c.cardBorder, RoundedCornerShape(10.dp))
                            .clickable { selected = id; preview = null; pickedText = "" }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(label, color = if (sel) c.accent else c.text, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = IronLogType.body.fontSize.sp)
                    }
                }
            }
        }

        // Pick file button
        item {
            Button(
                onClick = {
                    val mime = IMPORT_SOURCES.firstOrNull { it.first == selected }?.third
                        ?: arrayOf("*/*")
                    filePicker.launch(mime)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isWorking,
            ) { Text("Pick File to Import") }
        }

        // Preview card
        preview?.let { pv ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = c.card), border = BorderStroke(1.dp, c.cardBorder)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Preview", color = c.text, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.Bold)
                        Text("${pv.validRows} rows ready to import", color = c.subtext)
                        if (pv.domainCounts.isNotEmpty())
                            Text(pv.domainCounts.entries.joinToString("  ·  ") { "${it.key}: ${it.value}" },
                                color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                        pv.warnings.take(3).forEach { Text("⚠ $it", color = c.warning, fontSize = IronLogType.meta.fontSize.sp) }
                        pv.samples.forEach { Text(it, color = c.muted, fontSize = IronLogType.meta.fontSize.sp) }
                        if (pv.error != null) Text("Error: ${pv.error}", color = c.danger, fontSize = IronLogType.meta.fontSize.sp)
                    }
                }
            }
            item {
                Button(
                    onClick = { showConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = pv.canImport && !isWorking,
                ) { Text(if (selected == "ironlog_json") "Restore ${pv.validRows} rows" else "Import ${pv.validRows} rows") }
            }
        }

        if (status.isNotBlank()) item { Text(status, color = c.accent) }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(if (selected == "ironlog_json") "Restore backup?" else "Import data?") },
            text = {
                Text(
                    if (selected == "ironlog_json") {
                        "${preview?.validRows ?: 0} backup rows will be restored. Matching rows are updated by stable ID, so seeded exercises and repeated restores will not create duplicates."
                    } else {
                        "${preview?.validRows ?: 0} rows will be added. Importing the same file twice may create duplicates."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    isWorking = true
                    val pv = preview ?: return@TextButton
                    val text = pickedText
                    val src = selected
                    scope.launch(Dispatchers.IO) {
                        val replaceMode = src == "ironlog_json"
                        val res = runCatching { importText(text, repo, importExportRepo, pv, src, replaceMode) }
                        withContext(Dispatchers.Main) {
                            status = res.fold(
                                {
                                    if (src == "ironlog_json") onRestoreComplete()
                                    if (src == "ironlog_json") "Restored $it rows." else "Imported $it rows."
                                },
                                { "Failed: ${it.message}" },
                            )
                            isWorking = false; preview = null; pickedText = ""
                        }
                    }
                }) { Text(if (selected == "ironlog_json") "Restore" else "Import") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } },
        )
    }
}

