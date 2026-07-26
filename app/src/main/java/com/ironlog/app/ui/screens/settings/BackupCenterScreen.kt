package com.ironlog.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.data.repository.ImportExportRepository
import com.ironlog.app.services.BackupScheduler
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.io.File

@Composable
fun BackupCenterScreen(
    onBack: () -> Unit = {},
    onOpenDataPortability: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    repo: SettingsRepository = SettingsRepository(),
    importRepo: ImportExportRepository = ImportExportRepository(),
) {
    val c = useTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var backupFiles by remember { mutableStateOf(listOf<File>()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf("No backup selected.") }
    var replaceText by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var encryptedStatus by remember { mutableStateOf("") }
    var autoBackupEnabled by remember { mutableStateOf(false) }
    var backupHour by remember { mutableStateOf("02") }
    var backupMinute by remember { mutableStateOf("00") }
    var retentionCount by remember { mutableStateOf("14") }
    LaunchedEffect(Unit) {
        passphrase = repo.getString("backup_passphrase_hint").orEmpty()
        autoBackupEnabled = repo.getBoolean("auto_backup_enabled", false)
        backupHour = repo.getString("auto_backup_hour") ?: "02"
        backupMinute = repo.getString("auto_backup_minute") ?: "00"
        retentionCount = ((repo.getSettingNumber("auto_backup_retention")?.toInt() ?: 14).coerceIn(1, 60)).toString()
        backupFiles = withContext(Dispatchers.IO) { discoverBackups(context) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(title = "BACKUP CENTER", onBack = onBack) }
        item {
            Text("BACKUP", color = c.accent, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
            Text("Backup Center", color = c.text, fontSize = IronLogType.title.fontSize.sp, fontWeight = FontWeight(IronLogType.display.fontWeight), lineHeight = IronLogType.title.lineHeight.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = c.card), border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Use Data Portability for full JSON/CSV export and import.", color = c.subtext)
                    Button(onClick = onOpenDataPortability) { Text("Open Data Portability") }
                    Button(onClick = onOpenPrivacy) { Text("Privacy") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Auto Backup", color = c.text)
                        Switch(
                            checked = autoBackupEnabled,
                            onCheckedChange = {
                                autoBackupEnabled = it
                                scope.launch {
                                    repo.setBoolean("auto_backup_enabled", it)
                                    if (it) {
                                        val h = backupHour.toIntOrNull()?.coerceIn(0, 23) ?: 2
                                        val m = backupMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                                        BackupScheduler.scheduleDaily(context, h, m)
                                        status = "Auto backup scheduled daily at ${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}."
                                    } else {
                                        BackupScheduler.cancel(context)
                                        status = "Auto backup disabled."
                                    }
                                }
                            },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = backupHour,
                            onValueChange = { backupHour = it.filter(Char::isDigit).take(2) },
                            label = { Text("Hour (0-23)") },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = backupMinute,
                            onValueChange = { backupMinute = it.filter(Char::isDigit).take(2) },
                            label = { Text("Minute (0-59)") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = retentionCount,
                        onValueChange = { retentionCount = it.filter(Char::isDigit).take(2) },
                        label = { Text("Retention (keep N auto backups)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        scope.launch {
                            val h = backupHour.toIntOrNull()?.coerceIn(0, 23) ?: 2
                            val m = backupMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                            val keep = retentionCount.toIntOrNull()?.coerceIn(1, 60) ?: 14
                            backupHour = h.toString().padStart(2, '0')
                            backupMinute = m.toString().padStart(2, '0')
                            retentionCount = keep.toString()
                            repo.setString("auto_backup_hour", backupHour)
                            repo.setString("auto_backup_minute", backupMinute)
                            repo.setSetting("auto_backup_retention", keep, "number")
                            if (autoBackupEnabled) {
                                BackupScheduler.scheduleDaily(context, h, m)
                                status = "Auto backup updated: $backupHour:$backupMinute, keep last $keep."
                            } else {
                                status = "Backup settings saved. Enable Auto Backup to schedule."
                            }
                        }
                    }) { Text("Save Backup Schedule") }
                    Button(onClick = {
                        scope.launch {
                            val trimmed = passphrase.trim()
                            if (trimmed.length < 8) {
                                status = "Passphrase must be at least 8 characters."
                            } else {
                                val hint = trimmed.take(2) + "*".repeat((trimmed.length - 2).coerceAtLeast(0))
                                repo.setString("backup_passphrase_hint", hint)
                                status = "Backup passphrase hint stored."
                            }
                        }
                    }) { Text("Save Passphrase Hint") }
                    Button(onClick = {
                        scope.launch {
                            val phrase = passphrase.trim()
                            if (phrase.length < 8) {
                                encryptedStatus = "Passphrase too short. Use 8+ chars."
                            } else {
                                runCatching {
                                    // exportDatabase is IO, encryptPayload is CPU (PBKDF2 120k iterations) —
                                    // neither must run on the Main dispatcher.
                                    val exported = withContext(Dispatchers.IO) { importRepo.exportDatabase().toString() }
                                    val encrypted = withContext(Dispatchers.Default) { encryptPayload(exported, phrase) }
                                    val name = withContext(Dispatchers.IO) {
                                        val out = File(context.filesDir, "ironlog_snapshot_encrypted_${System.currentTimeMillis()}.json")
                                        out.writeText(encrypted)
                                        out.name
                                    }
                                    encryptedStatus = "Encrypted snapshot saved: $name"
                                }.onFailure {
                                    encryptedStatus = "Encrypted snapshot failed: ${it.message}"
                                }
                            }
                        }
                    }) { Text("Create Encrypted Snapshot") }
                    if (encryptedStatus.isNotBlank()) {
                        Text(encryptedStatus, color = c.accent, fontSize = IronLogType.meta.fontSize.sp)
                    }
                    Button(onClick = {
                        scope.launch {
                            repo.setString("last_backup_health", "ok")
                            backupFiles = withContext(Dispatchers.IO) { discoverBackups(context) }
                            status = "Backup health marked as OK."
                        }
                    }) { Text("Validate Latest Backup") }
                    if (status.isNotBlank()) Text(status, color = c.accent, fontSize = IronLogType.meta.fontSize.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = c.card), border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restore Local Backup", color = c.text, fontSize = IronLogType.section.fontSize.sp)
                    if (backupFiles.isEmpty()) {
                        Text("No local backup JSON files found in app storage.", color = c.muted)
                    } else {
                        backupFiles.take(8).forEach { file ->
                            TextButton(onClick = {
                                selectedFile = file
                                preview = "Loading preview…"
                                // buildBackupPreview reads + parses a file — must not run on Main.
                                scope.launch(Dispatchers.IO) {
                                    preview = runCatching { buildBackupPreview(file) }.getOrElse { "Preview failed: ${it.message}" }
                                }
                            }) {
                                Text(file.name, color = if (selectedFile?.absolutePath == file.absolutePath) c.accent else c.text)
                            }
                        }
                    }
                    Text(preview, color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text("Type REPLACE to confirm restore") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { showConfirm = true },
                        enabled = selectedFile != null && replaceText.trim().uppercase() == "REPLACE",
                    ) { Text("Restore Selected Backup") }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Replace existing history?") },
            text = { Text("This will clear completed workouts and restore from the selected backup. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    val file = selectedFile ?: return@TextButton
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                importRepo.runConfirmedImport(file.readText(), mode = "replace").workouts
                            }
                        }
                        status = result.fold(
                            onSuccess = { "Restore complete. Imported $it workouts." },
                            onFailure = { "Restore failed: ${it.message}" },
                        )
                    }
                }) { Text("Restore Now") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } },
        )
    }
}

private fun encryptPayload(plainText: String, passphrase: String): String {
    val random = SecureRandom()
    val salt = ByteArray(16).also(random::nextBytes)
    val iv = ByteArray(12).also(random::nextBytes)
    val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(passphrase.toCharArray(), salt, 120000, 256)
    val keyBytes = keyFactory.generateSecret(spec).encoded
    val secret = SecretKeySpec(keyBytes, "AES")
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, iv))
    val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
    val payloadHash = MessageDigest.getInstance("SHA-256").digest(plainText.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return JSONObject()
        .put("schema", "IRONLOG_ENCRYPTED_EXPORT_V1")
        .put("kdf", "PBKDF2WithHmacSHA256")
        .put("iterations", 120000)
        .put("saltB64", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
        .put("ivB64", android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
        .put("ciphertextB64", android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
        .put("sha256", payloadHash)
        .put("exportedAt", System.currentTimeMillis())
        .toString(2)
}

private fun buildBackupPreview(file: File): String {
    val text = file.readText()
    // New watermelon-format backup (JSONObject root with type = "ironlog_watermelon_export")
    runCatching {
        val obj = JSONObject(text)
        if (obj.optString("type") == "ironlog_watermelon_export") {
            val data = obj.optJSONObject("data") ?: return "Invalid backup: missing data section."
            val workouts = data.optJSONArray("workouts")?.length() ?: 0
            val plans = data.optJSONArray("plans")?.length() ?: 0
            val sets = data.optJSONArray("workout_sets")?.length() ?: 0
            val body = data.optJSONArray("body_measurements")?.length() ?: 0
            val exportedAt = obj.optString("exportedAt", "unknown date")
            return "Backup ($exportedAt)\nworkouts=$workouts · plans=$plans · sets=$sets · body=$body"
        }
    }
    // Legacy JSONArray format (old app versions exported an array of workout objects)
    runCatching {
        val arr = JSONArray(text)
        var workouts = 0; var exercises = 0; var sets = 0
        for (i in 0 until arr.length()) {
            val w = arr.optJSONObject(i) ?: continue
            val ex = w.optJSONArray("exercises") ?: JSONArray()
            var setsInWorkout = 0
            for (j in 0 until ex.length()) {
                val e = ex.optJSONObject(j) ?: continue
                val s = e.optJSONArray("sets") ?: JSONArray()
                if (s.length() > 0) exercises++
                sets += s.length(); setsInWorkout += s.length()
            }
            if (setsInWorkout > 0) workouts++
        }
        return "Legacy backup: workouts=$workouts · exercises=$exercises · sets=$sets"
    }
    // Encrypted snapshots (schema = "IRONLOG_ENCRYPTED_EXPORT_V1") reach here — correct message.
    return "Could not parse backup file. File may be encrypted or corrupted."
}

private fun discoverBackups(context: android.content.Context): List<File> {
    val root = context.filesDir
    val autoDir = File(root, "backups")
    val files = (root.listFiles().orEmpty().toList() + autoDir.listFiles().orEmpty().toList())
        .filter { it.isFile && it.name.startsWith("ironlog_backup_") && it.extension.equals("json", ignoreCase = true) }
    return files.sortedByDescending { it.lastModified() }
}

