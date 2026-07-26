package com.ironlog.app.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.core.content.FileProvider
import com.ironlog.app.data.model.CompletedExerciseInput
import com.ironlog.app.data.model.CreateCompletedWorkoutInput
import com.ironlog.app.data.model.SetInput
import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import com.ironlog.app.data.repository.ImportExportRepository
import com.ironlog.app.data.repository.WorkoutRepository
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.util.FuzzyExerciseMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val SOURCE_FORMATS = listOf(
    "ironlog_json"   to "IronLog Backup",
    "strong_csv"     to "Strong CSV",
    "hevy_csv"       to "Hevy CSV",
    "openweight_json" to "OpenWeight JSON",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataPortabilityScreen(
    sourceHint: String = "ironlog_json",
    onBack: () -> Unit = {},
    onRestoreComplete: () -> Unit = {},
    repo: WorkoutRepository = WorkoutRepository(),
    importExportRepo: ImportExportRepository = ImportExportRepository(),
) {
    val c = useTheme()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedSource by remember { mutableStateOf(sourceHint.ifBlank { "ironlog_json" }) }
    var pickedFileText by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<CsvPreview?>(null) }
    var status by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    val importFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val text = runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            }.getOrElse { e ->
                withContext(Dispatchers.Main) { status = "Could not read file: ${e.message}" }
                return@launch
            }
            val pv = buildImportPreview(text, selectedSource)
            withContext(Dispatchers.Main) { pickedFileText = text; preview = pv; status = "" }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(title = "DATA PORTABILITY", onBack = onBack) }

        // ── EXPORT ────────────────────────────────────────────────────────────
        item {
            Text("EXPORT", color = c.accent, fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = c.card), border = BorderStroke(1.dp, c.cardBorder)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Share your data as a file. JSON is the full backup; CSV is compatible with spreadsheets.",
                        color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                isWorking = true
                                scope.launch(Dispatchers.IO) {
                                    val res = runCatching { buildJsonExportFile(ctx, importExportRepo) }
                                    withContext(Dispatchers.Main) {
                                        isWorking = false
                                        res.onSuccess { f ->
                                            runCatching { shareFile(ctx, f, "application/json") }
                                                .onFailure { e -> status = "Share failed: ${e.message}" }
                                        }.onFailure { status = "Export failed: ${it.message}" }
                                    }
                                }
                            },
                            enabled = !isWorking,
                        ) { Text("Share JSON") }
                        Button(
                            onClick = {
                                isWorking = true
                                scope.launch(Dispatchers.IO) {
                                    val res = runCatching { buildCsvExportFile(ctx, repo) }
                                    withContext(Dispatchers.Main) {
                                        isWorking = false
                                        res.onSuccess { f ->
                                            runCatching { shareFile(ctx, f, "text/csv") }
                                                .onFailure { e -> status = "Share failed: ${e.message}" }
                                        }.onFailure { status = "Export failed: ${it.message}" }
                                    }
                                }
                            },
                            enabled = !isWorking,
                        ) { Text("Share CSV") }
                    }
                    Text(
                        "ℹ Progress photos are not included — re-link them manually if restoring to a new device.",
                        color = c.muted,
                        fontSize = IronLogType.meta.fontSize.sp,
                    )
                }
            }
        }

        // ── IMPORT ────────────────────────────────────────────────────────────
        item {
            Text("IMPORT", color = c.accent, fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
        }
        item {
            Text("Choose your source format, then pick the file. A preview appears before anything is written.",
                color = c.subtext, fontSize = IronLogType.body.fontSize.sp)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SOURCE_FORMATS.forEach { (id, label) ->
                    val sel = selectedSource == id
                    Text(
                        label,
                        color = if (sel) c.accent else c.muted,
                        fontSize = IronLogType.meta.fontSize.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (sel) c.accentSoft else c.card)
                            .border(1.dp, if (sel) c.accentBorder else c.cardBorder, RoundedCornerShape(50.dp))
                            .clickable { selectedSource = id; preview = null; pickedFileText = "" }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    val mime = if (selectedSource.endsWith("_json"))
                        arrayOf("application/json", "text/plain", "*/*")
                    else
                        arrayOf("text/comma-separated-values", "text/csv", "text/plain", "*/*")
                    importFilePicker.launch(mime)
                },
                enabled = !isWorking,
            ) { Text("Pick File") }
        }

        // Preview card
        preview?.let { pv ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = c.card), border = BorderStroke(1.dp, c.cardBorder)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Preview", color = c.text, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.Bold)
                        Text("${pv.validRows} rows ready", color = c.subtext)
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
                    enabled = pv.canImport && !isWorking,
                ) { Text(if (selectedSource == "ironlog_json") "Restore ${pv.validRows} rows" else "Import ${pv.validRows} rows") }
            }
        }

        if (status.isNotBlank()) item { Text(status, color = c.accent) }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(if (selectedSource == "ironlog_json") "Restore backup?" else "Import data?") },
            text = {
                Text(
                    if (selectedSource == "ironlog_json") {
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
                    val text = pickedFileText
                    val src = selectedSource
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
                            isWorking = false; preview = null; pickedFileText = ""
                        }
                    }
                }) { Text(if (selectedSource == "ironlog_json") "Restore" else "Import") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } },
        )
    }
}

private fun shareFile(context: android.content.Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, file.name))
}

private suspend fun buildJsonExportFile(context: android.content.Context, importExportRepo: ImportExportRepository): File {
    val out = importExportRepo.exportDatabase()
    val fn = "ironlog_backup_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.json"
    val dir = File(context.filesDir, "exports").also { it.mkdirs() }
    return File(dir, fn).also { it.writeText(out.toString(2)) }
}

private suspend fun buildCsvExportFile(context: android.content.Context, repo: WorkoutRepository): File {
    val all = repo.getCompletedWorkoutsFlow().firstValue()
    val sb = StringBuilder("Date,Workout Name,Exercise Name,Set Order,Weight,Reps,RPE,Distance,Seconds,Notes,Workout Notes\n")
    val exNameByUid = com.ironlog.app.data.objectbox.ObjectBox.store
        .boxFor(com.ironlog.app.data.objectbox.ExerciseEntity::class.java)
        .all.associate { it.uid to it.name }
    all.forEach { w ->
        val detail = repo.getWorkoutDetailSnapshot(w.uid)
        val date = runCatching {
            java.time.Instant.ofEpochMilli(w.startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        }.getOrElse { "" }
        detail.exercises.forEach { we ->
            detail.sets.filter { it.workoutExerciseUid == we.uid }.forEach { s ->
                sb.append(listOf(date, w.name.csvSafe(), (exNameByUid[we.exerciseUid].orEmpty()).csvSafe(),
                    s.setIndex.toString(), s.weight.toString(), s.reps.toString(),
                    (s.rpe ?: "").toString(), "", "", "", w.notes.csvSafe()).joinToString(",")).append("\n")
            }
        }
    }
    val fn = "ironlog_export_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.csv"
    val dir = File(context.filesDir, "exports").also { it.mkdirs() }
    return File(dir, fn).also { it.writeText(sb.toString()) }
}

internal data class CsvPreview(
    val validRows: Int,
    val samples: List<String>,
    val error: String?,
    val domainCounts: Map<String, Int> = emptyMap(),
    val warnings: List<String> = emptyList(),
)

internal fun buildImportPreview(text: String, sourceHint: String): CsvPreview {
    return when (sourceHint.lowercase()) {
        "ironlog_json" -> buildIronlogBackupPreview(text)
        "openweight_json" -> buildOpenWeightPreview(text)
        "hevy_csv" -> buildHevyPreview(text)
        else -> buildStrongPreview(text)
    }
}

private fun buildIronlogBackupPreview(text: String): CsvPreview {
    val repo = ImportExportRepository()
    val p = repo.previewImportPayload(text)
    if (!p.valid) return CsvPreview(0, emptyList(), p.reason ?: "Invalid backup")
    val totalRows = p.counts.exercises +
        p.counts.exerciseMuscles +
        p.counts.plans +
        p.counts.planDays +
        p.counts.planExercises +
        p.counts.workouts +
        p.counts.workoutExercises +
        p.counts.sets +
        p.counts.bodyMeasurements +
        p.counts.progressPhotos +
        p.counts.settings +
        p.counts.athleteCalibrations +
        p.counts.gamificationProfiles +
        p.counts.ironLedgerEvents
    
    return CsvPreview(
        validRows = totalRows,
        samples = listOf("plans: ${p.plans}", "workouts: ${p.workouts}", "sets: ${p.sets}"),
        error = null,
        domainCounts = mapOf(
            "plans" to p.counts.plans,
            "workouts" to p.counts.workouts,
            "sets" to p.counts.sets,
            "body" to p.counts.bodyMeasurements,
            "custom_exercises" to p.customExercises.size,
            "progress_photos" to p.counts.progressPhotos,
            "settings" to p.counts.settings,
            "exercise_muscles" to p.counts.exerciseMuscles,
            "athlete" to p.counts.athleteCalibrations,
            "ledger" to p.counts.gamificationProfiles + p.counts.ironLedgerEvents,
        ),
        warnings = p.warnings,
    )
}

private fun buildStrongPreview(text: String): CsvPreview {
    val rows = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (rows.isEmpty()) return CsvPreview(0, emptyList(), null)
    val header = rows.first().lowercase().replace(" ", "")
    val expectedCurrent = listOf("date", "workout", "exerciseuid", "weight", "reps")
    val expectedRn = listOf("date", "workoutname", "exercisename", "weight", "reps")
    if (!expectedCurrent.all { header.contains(it) } && !expectedRn.all { header.contains(it) }) {
        return CsvPreview(0, emptyList(), "Header must contain: date, workout, exercise, weight, reps")
    }
    val data = rows.drop(1)
    var valid = 0
    val workoutKeys = linkedSetOf<String>()
    val exerciseKeys = linkedSetOf<String>()
    val samples = mutableListOf<String>()
    data.forEach { line ->
        val p = parseCsvLine(line)
        val dateOk = parseDateToMillis(p.getOrNull(0).orEmpty()) != null
        val weight = p.getOrNull(4)?.toDoubleOrNull() ?: p.getOrNull(3)?.toDoubleOrNull()
        val reps = p.getOrNull(5)?.toDoubleOrNull() ?: p.getOrNull(4)?.toDoubleOrNull()
        if (p.size >= 5 && dateOk && weight != null && reps != null) {
            valid++
            val workoutName = p.getOrNull(1).orEmpty()
            val exercise = p.getOrNull(2).orEmpty()
            workoutKeys += "${p.getOrNull(0)}|$workoutName"
            exerciseKeys += "$workoutName|$exercise"
            if (samples.size < 3) samples += "$exercise - $weight x $reps"
        }
    }
    return CsvPreview(
        validRows = valid,
        samples = samples,
        error = if (valid == 0) "No valid rows found." else null,
        domainCounts = mapOf("workouts" to workoutKeys.size, "exercises" to exerciseKeys.size, "sets" to valid),
    )
}

private fun buildHevyPreview(text: String): CsvPreview {
    val rows = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (rows.isEmpty()) return CsvPreview(0, emptyList(), null)
    val header = rows.first().lowercase().replace(" ", "")
    val expected = listOf("date", "exercise", "weight", "reps")
    if (!expected.all { header.contains(it) }) return CsvPreview(0, emptyList(), "Hevy CSV header must contain: date, exercise, weight, reps")
    var valid = 0
    val workoutKeys = linkedSetOf<String>()
    val exerciseKeys = linkedSetOf<String>()
    val samples = mutableListOf<String>()
    rows.drop(1).forEach { line ->
        val p = parseCsvLine(line)
        if (p.size >= 4 && p[0].isNotBlank() && p[2].toDoubleOrNull() != null && p[3].toDoubleOrNull() != null) {
            valid++
            workoutKeys += p[0]
            exerciseKeys += "${p[0]}|${p[1]}"
            if (samples.size < 3) samples += "${p[1]} - ${p[2]} x ${p[3]}"
        }
    }
    return CsvPreview(
        validRows = valid,
        samples = samples,
        error = if (valid == 0) "No valid Hevy rows found." else null,
        domainCounts = mapOf("workouts" to workoutKeys.size, "exercises" to exerciseKeys.size, "sets" to valid),
    )
}

private fun buildOpenWeightPreview(text: String): CsvPreview {
    val payload = runCatching { JSONObject(text) }.getOrNull()
        ?: return CsvPreview(0, emptyList(), "OpenWeight payload must be valid JSON object")
    val workouts = payload.optJSONArray("workouts")
        ?: return CsvPreview(0, emptyList(), "OpenWeight JSON must contain 'workouts' array")

    var validWorkouts = 0
    var validExercises = 0
    var validSets = 0
    val samples = mutableListOf<String>()
    for (i in 0 until workouts.length()) {
        val w = workouts.optJSONObject(i) ?: continue
        val ex = w.optJSONArray("exercises") ?: continue
        var setsInWorkout = 0
        for (j in 0 until ex.length()) {
            val exObj = ex.optJSONObject(j) ?: continue
            val sets = exObj.optJSONArray("sets") ?: JSONArray()
            if (sets.length() > 0) {
                validExercises++
                setsInWorkout += sets.length()
            }
        }
        if (setsInWorkout > 0) {
            validWorkouts++
            validSets += setsInWorkout
            if (samples.size < 3) samples += "${w.optString("name", "Workout")} - exercises: ${ex.length()} - sets: $setsInWorkout"
        }
    }
    return CsvPreview(
        validRows = validWorkouts,
        samples = samples,
        error = if (validWorkouts == 0) "No valid workouts found in OpenWeight payload." else null,
        domainCounts = mapOf("workouts" to validWorkouts, "exercises" to validExercises, "sets" to validSets),
    )
}

internal suspend fun importText(text: String, repo: WorkoutRepository, importExportRepo: ImportExportRepository, preview: CsvPreview, sourceHint: String, replaceMode: Boolean): Int {
    if (replaceMode && sourceHint.lowercase() != "ironlog_json") repo.clearCompletedWorkouts()
    val imported = when (sourceHint.lowercase()) {
        "ironlog_json" -> importIronlogBackup(text, importExportRepo, replaceMode)
        "openweight_json" -> importOpenWeight(text, repo, preview)
        "hevy_csv" -> importHevy(text, repo, preview)
        else -> importStrong(text, repo, preview)
    }
    if (imported > 0) markLedgerImportedHistory()
    return imported
}

private fun markLedgerImportedHistory() {
    ObjectBox.store.boxFor(AppSettingEntity::class.java).put(AppSettingEntity().apply {
        key = "ledger_imported_history"
        value = "true"
        valueType = "boolean"
        updatedAt = System.currentTimeMillis()
    })
}

private suspend fun importIronlogBackup(text: String, importExportRepo: ImportExportRepository, replaceMode: Boolean): Int {
    val result = importExportRepo.runConfirmedImport(text, mode = if (replaceMode) "replace" else "append")
    return result.counts.exercises +
        result.counts.exerciseMuscles +
        result.counts.plans +
        result.counts.planDays +
        result.counts.planExercises +
        result.counts.workouts +
        result.counts.workoutExercises +
        result.counts.sets +
        result.counts.bodyMeasurements +
        result.counts.progressPhotos +
        result.counts.settings +
        result.counts.athleteCalibrations +
        result.counts.gamificationProfiles +
        result.counts.ironLedgerEvents
}

internal val CsvPreview.canImport: Boolean
    get() = error == null && validRows > 0

private suspend fun importStrong(text: String, repo: WorkoutRepository, preview: CsvPreview): Int {
    require(preview.error == null) { preview.error ?: "Invalid CSV" }
    val rows = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (rows.size <= 1) return 0
    val headerCols = parseCsvLine(rows.first()).map { it.trim().lowercase().replace(" ", "") }
    fun idx(vararg names: String): Int = headerCols.indexOfFirst { col -> names.any { col.contains(it) } }
    val dateIdx = idx("date", "start_time", "start")
    val workoutIdx = idx("workoutname", "workout")
    val exerciseIdx = idx("exercisename", "exerciseuid", "exercise")
    val weightIdx = idx("weight")
    val repsIdx = idx("reps", "rep")
    val rpeIdx = idx("rpe")
    val notesIdx = idx("notes")
    val workoutNotesIdx = idx("workoutnotes")
    val allExercises = com.ironlog.app.data.objectbox.ObjectBox.store.boxFor(com.ironlog.app.data.objectbox.ExerciseEntity::class.java).all
    val mapper = FuzzyExerciseMapper(allExercises)

    data class StrongRow(
        val dateMillis: Long,
        val workoutName: String,
        val exUid: String,
        val weight: Double,
        val reps: Double,
        val restSeconds: Int,
        val rpe: Double?,
        val note: String?,
        val workoutNote: String?,
    )
    val parsed = rows.drop(1).mapNotNull { line ->
        val p = parseCsvLine(line)
        if (p.size < 5) return@mapNotNull null
        val dateMillis = parseDateToMillis(p.getOrNull(dateIdx).orEmpty()) ?: return@mapNotNull null
        val workoutName = p.getOrNull(workoutIdx).orEmpty().ifBlank { "Imported Workout" }
        val exUid = p.getOrNull(exerciseIdx).orEmpty().ifBlank { return@mapNotNull null }
        val weight = p.getOrNull(weightIdx).orEmpty().toDoubleOrNull() ?: return@mapNotNull null
        val reps = p.getOrNull(repsIdx).orEmpty().toDoubleOrNull() ?: return@mapNotNull null
        val restSeconds = 0
        val rpe = if (rpeIdx >= 0) p.getOrNull(rpeIdx)?.toDoubleOrNull() else null
        val note = if (notesIdx >= 0) p.getOrNull(notesIdx)?.takeIf { it.isNotBlank() } else null
        val workoutNote = if (workoutNotesIdx >= 0) p.getOrNull(workoutNotesIdx)?.takeIf { it.isNotBlank() } else null
        StrongRow(dateMillis, workoutName, exUid, weight, reps, restSeconds, rpe, note, workoutNote)
    }
    val grouped = parsed.groupBy { "${it.dateMillis}|${it.workoutName}" }
    var imported = 0
    grouped.values.forEach { group ->
        val first = group.firstOrNull() ?: return@forEach
        val exercises = group
            .groupBy { it.exUid }
            .map { (exUid, sets) ->
                val looksLikeUid = exUid.contains("-") && exUid.length >= 12
                val matchedId = mapper.match(exUid)
                CompletedExerciseInput(
                    exerciseId = matchedId ?: if (looksLikeUid) exUid else null,
                    name = if (matchedId != null || looksLikeUid) null else exUid,
                    note = sets.firstNotNullOfOrNull { it.note },
                    sets = sets.map { SetInput(weight = it.weight, reps = it.reps, restSeconds = it.restSeconds, rpe = it.rpe) },
                )
            }
        if (exercises.isEmpty()) return@forEach
        repo.createCompletedWorkout(
            CreateCompletedWorkoutInput(
                name = first.workoutName,
                startedAt = first.dateMillis,
                durationSeconds = 1800,
                notes = first.workoutNote,
                exerciseData = exercises,
            ),
        )
        imported++
    }
    return imported
}

private suspend fun importHevy(text: String, repo: WorkoutRepository, preview: CsvPreview): Int {
    require(preview.error == null) { preview.error ?: "Invalid Hevy CSV" }
    val rows = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (rows.size <= 1) return 0
    val headerCols = parseCsvLine(rows.first()).map { it.trim().lowercase() }
    fun idx(vararg names: String): Int = headerCols.indexOfFirst { col -> names.any { col.contains(it) } }
    val dateIdx = idx("date", "start_time", "start")
    val nameIdx = idx("exercise", "exercise_name", "name")
    val weightIdx = idx("weight", "kg", "lbs")
    val repsIdx = idx("reps", "rep")
    if (nameIdx < 0 || weightIdx < 0 || repsIdx < 0) return 0
    val allExercises = com.ironlog.app.data.objectbox.ObjectBox.store.boxFor(com.ironlog.app.data.objectbox.ExerciseEntity::class.java).all
    val mapper = FuzzyExerciseMapper(allExercises)

    data class HevyRow(val dayKey: String, val exerciseName: String, val weight: Double, val reps: Double)
    val parsed = rows.drop(1).mapNotNull { line ->
        val p = parseCsvLine(line)
        val ex = p.getOrNull(nameIdx)?.ifBlank { null } ?: return@mapNotNull null
        val weight = p.getOrNull(weightIdx)?.toDoubleOrNull() ?: return@mapNotNull null
        val reps = p.getOrNull(repsIdx)?.toDoubleOrNull() ?: return@mapNotNull null
        val dayRaw = if (dateIdx >= 0) p.getOrNull(dateIdx).orEmpty() else ""
        val dayKey = if (dayRaw.isNotBlank()) dayRaw.take(10) else java.time.LocalDate.now().toString()
        HevyRow(dayKey, ex, weight, reps)
    }
    val grouped = parsed.groupBy { it.dayKey }
    var imported = 0
    grouped.values.forEach { group ->
        val exercises = group.groupBy { it.exerciseName }.map { (name, sets) ->
            val matchedId = mapper.match(name)
            CompletedExerciseInput(
                exerciseId = matchedId,
                name = if (matchedId != null) null else name,
                sets = sets.map { SetInput(weight = it.weight, reps = it.reps, restSeconds = 90) },
            )
        }
        if (exercises.isEmpty()) return@forEach
        val startedAt = runCatching { java.time.LocalDate.parse(group.first().dayKey).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
            .getOrElse { System.currentTimeMillis() }
        repo.createCompletedWorkout(
            CreateCompletedWorkoutInput(
                name = "Hevy Import",
                startedAt = startedAt,
                durationSeconds = 1800,
                exerciseData = exercises,
            ),
        )
        imported++
    }
    return imported
}

private suspend fun importOpenWeight(text: String, repo: WorkoutRepository, preview: CsvPreview): Int {
    require(preview.error == null) { preview.error ?: "Invalid OpenWeight JSON" }
    val payload = JSONObject(text)
    val workouts = payload.optJSONArray("workouts") ?: return 0
    var imported = 0
    val allExercises = com.ironlog.app.data.objectbox.ObjectBox.store.boxFor(com.ironlog.app.data.objectbox.ExerciseEntity::class.java).all
    val mapper = FuzzyExerciseMapper(allExercises)
    for (i in 0 until workouts.length()) {
        val w = workouts.optJSONObject(i) ?: continue
        val exArr = w.optJSONArray("exercises") ?: continue
        val completed = mutableListOf<CompletedExerciseInput>()
        for (j in 0 until exArr.length()) {
            val ex = exArr.optJSONObject(j) ?: continue
            val name = ex.optString("name", "Exercise")
            val setsJson = ex.optJSONArray("sets") ?: JSONArray()
            val sets = mutableListOf<SetInput>()
            for (k in 0 until setsJson.length()) {
                val s = setsJson.optJSONObject(k) ?: continue
                val wt = s.optDouble("weight", Double.NaN)
                val rp = s.optDouble("reps", Double.NaN)
                if (wt.isNaN() || rp.isNaN()) continue
                sets += SetInput(weight = wt, reps = rp, restSeconds = s.optInt("restSeconds", 90))
            }
            if (sets.isNotEmpty()) {
                val matchedId = mapper.match(name)
                completed += CompletedExerciseInput(
                    exerciseId = matchedId,
                    name = if (matchedId != null) null else name,
                    sets = sets
                )
            }
        }
        if (completed.isEmpty()) continue
        repo.createCompletedWorkout(
            CreateCompletedWorkoutInput(
                name = w.optString("name", "OpenWeight Import"),
                startedAt = w.optLong("startedAt", System.currentTimeMillis()),
                durationSeconds = w.optInt("durationSeconds", 1800),
                exerciseData = completed,
            )
        )
        imported++
    }
    return imported
}

private suspend fun Flow<List<WorkoutEntity>>.firstValue(): List<WorkoutEntity> =
    withContext(Dispatchers.IO) { this@firstValue.first() }

private fun String.csvSafe(): String {
    val escaped = replace("\"", "\"\"")
    return "\"$escaped\""
}

private fun parseDateToMillis(raw: String): Long? {
    val value = raw.trim()
    if (value.isBlank()) return null
    value.toLongOrNull()?.let { return it }
    runCatching { return java.time.Instant.parse(value).toEpochMilli() }
    runCatching {
        val d = java.time.LocalDate.parse(value)
        return d.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    runCatching {
        val dt = java.time.LocalDateTime.parse(value)
        return dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    return null
}

private fun parseCsvLine(line: String): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            }
            ch == ',' && !inQuotes -> {
                out += sb.toString().trim()
                sb.clear()
            }
            else -> sb.append(ch)
        }
        i++
    }
    out += sb.toString().trim()
    return out
}

private fun JSONArray.collectIds(field: String): Set<String> {
    val out = mutableSetOf<String>()
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        val id = o.optString(field)
        if (id.isNotBlank()) out += id
    }
    return out
}

private fun JSONArray.countMissingRef(refField: String, validIds: Set<String>): Int {
    var count = 0
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        val ref = o.optString(refField)
        if (ref.isNotBlank() && ref !in validIds) count++
    }
    return count
}

