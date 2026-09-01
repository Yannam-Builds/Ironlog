package com.ironlog.app.ui.screens.settings

import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
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
import com.ironlog.app.ui.theme.Text
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
import com.ironlog.app.services.AutoBackupMutationCoordinator
import com.ironlog.app.services.BackupScheduler
import com.ironlog.app.services.WorkoutNotificationBridge
import com.ironlog.app.services.commitBackupArtifactWithCleanup
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.components.IronLogSwitch
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
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
    var passphraseConfirmation by remember { mutableStateOf("") }
    var encrypting by remember { mutableStateOf(false) }
    var backupFiles by remember { mutableStateOf(listOf<File>()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf("No backup selected.") }
    var replaceText by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var restorePayload by remember { mutableStateOf<String?>(null) }
    var encryptedStatus by remember { mutableStateOf("") }
    var autoBackupEnabled by remember { mutableStateOf(false) }
    var backupHour by remember { mutableStateOf("02") }
    var backupMinute by remember { mutableStateOf("00") }
    var retentionCount by remember { mutableStateOf("14") }

    suspend fun restoreAutoBackupUiState(requestToken: Long): Boolean {
        if (!AutoBackupMutationCoordinator.isCurrent(requestToken)) return false
        var restored = false
        val readApplied = withContext(Dispatchers.IO) {
            AutoBackupMutationCoordinator.runIfCurrent(requestToken) {
                restored = repo.getBoolean("auto_backup_enabled", false)
            }
        }
        if (!readApplied || !AutoBackupMutationCoordinator.isCurrent(requestToken)) return false
        autoBackupEnabled = restored
        return true
    }

    fun submitAutoBackupMutation(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        retention: Int,
        successMessage: String,
    ) {
        // This function is invoked directly from the UI event. Issue the token and enter the
        // durable context before returning so disposal cannot invalidate the older writer while
        // also preventing the newest request from ever starting.
        val requestToken = AutoBackupMutationCoordinator.issueToken()
        val normalizedHour = hour.coerceIn(0, 23)
        val normalizedMinute = minute.coerceIn(0, 59)
        val normalizedRetention = retention.coerceIn(1, 60)
        val normalizedHourText = normalizedHour.toString().padStart(2, '0')
        val normalizedMinuteText = normalizedMinute.toString().padStart(2, '0')
        backupHour = normalizedHourText
        backupMinute = normalizedMinuteText
        retentionCount = normalizedRetention.toString()
        autoBackupEnabled = enabled
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val applied = withContext(Dispatchers.IO + NonCancellable) {
                    AutoBackupMutationCoordinator.runIfCurrent(requestToken) {
                        repo.setString("auto_backup_hour", normalizedHourText)
                        repo.setString("auto_backup_minute", normalizedMinuteText)
                        repo.setSetting("auto_backup_retention", normalizedRetention, "number")
                        repo.setBoolean("auto_backup_enabled", enabled)
                        if (enabled) {
                            BackupScheduler.scheduleDaily(
                                context,
                                normalizedHour,
                                normalizedMinute,
                            )
                        } else {
                            BackupScheduler.cancel(context)
                            try {
                                WorkoutNotificationBridge.cancelReminderAfterDataMutation(context)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Timber.w(error, "Could not dismiss a stale backup reminder")
                            }
                        }
                    }
                }
                if (!applied ||
                    !AutoBackupMutationCoordinator.isCurrent(requestToken)
                ) return@launch
                val restored = restoreAutoBackupUiState(requestToken)
                if (restored && AutoBackupMutationCoordinator.isCurrent(requestToken)) {
                    status = successMessage
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!AutoBackupMutationCoordinator.isCurrent(requestToken)) return@launch
                val restored = try {
                    restoreAutoBackupUiState(requestToken)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (restoreError: Exception) {
                    Timber.w(restoreError, "Could not restore auto-backup UI")
                    false
                }
                if (restored && AutoBackupMutationCoordinator.isCurrent(requestToken)) {
                    status = "Could not update auto backup: ${error.message}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val loadToken = AutoBackupMutationCoordinator.currentToken()
        val restored = restoreAutoBackupUiState(loadToken)
        val loadedSchedule = withContext(Dispatchers.IO) {
            Triple(
                repo.getString("auto_backup_hour") ?: "02",
                repo.getString("auto_backup_minute") ?: "00",
                (repo.getSettingNumber("auto_backup_retention")?.toInt() ?: 14)
                    .coerceIn(1, 60),
            )
        }
        if (restored && AutoBackupMutationCoordinator.isCurrent(loadToken)) {
            backupHour = loadedSchedule.first
            backupMinute = loadedSchedule.second
            retentionCount = loadedSchedule.third.toString()
        }
        backupFiles = withContext(Dispatchers.IO) { discoverBackups(context) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(12.dp),
    ) {
        item { ScreenHeader(title = "BACKUP CENTER", onBack = onBack) }
        item {
            Text("BACKUP", color = c.accent, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
            Text("Backup Center", color = c.text, fontSize = IronLogType.title.fontSize.sp, fontWeight = FontWeight(IronLogType.display.fontWeight), lineHeight = IronLogType.title.lineHeight.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = c.card), border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = appSpacedBy(8.dp)) {
                    Text("Use Data Portability for full JSON/CSV export and import.", color = c.subtext)
                    Button(onClick = onOpenDataPortability) { Text("Open Data Portability") }
                    Button(onClick = onOpenPrivacy) { Text("Privacy") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Auto Backup", color = c.text)
                        IronLogSwitch(
                            checked = autoBackupEnabled,
                            onCheckedChange = { enabled ->
                                val h = backupHour.toIntOrNull()?.coerceIn(0, 23) ?: 2
                                val m = backupMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                                val keep = retentionCount.toIntOrNull()?.coerceIn(1, 60) ?: 14
                                submitAutoBackupMutation(
                                    enabled = enabled,
                                    hour = h,
                                    minute = m,
                                    retention = keep,
                                    successMessage = if (enabled) {
                                        "Auto backup scheduled daily at ${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}."
                                    } else {
                                        "Auto backup disabled."
                                    },
                                )
                            },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = appSpacedBy(8.dp)) {
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
                        val h = backupHour.toIntOrNull()?.coerceIn(0, 23) ?: 2
                        val m = backupMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        val keep = retentionCount.toIntOrNull()?.coerceIn(1, 60) ?: 14
                        submitAutoBackupMutation(
                            enabled = autoBackupEnabled,
                            hour = h,
                            minute = m,
                            retention = keep,
                            successMessage = if (autoBackupEnabled) {
                                "Auto backup updated: ${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}, keep last $keep."
                            } else {
                                "Backup settings saved. Enable Auto Backup to schedule."
                            },
                        )
                    }) { Text("Save Backup Schedule") }
                    Text("Encrypted snapshot", color = c.text)
                    Text("Remember this passphrase. It is not saved, and cannot be recovered by IronLog. Images are not included in JSON exports.", color = c.subtext)
                    OutlinedTextField(passphrase, { passphrase = it }, label = { Text("New backup passphrase") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), enabled = !encrypting,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(passphraseConfirmation, { passphraseConfirmation = it }, label = { Text("Confirm backup passphrase") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), enabled = !encrypting,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    Button(enabled = !encrypting && passphrase.length >= 8 && passphrase == passphraseConfirmation, onClick = {
                        if (encrypting || passphrase.length < 8 || passphrase != passphraseConfirmation) return@Button
                        val phrase = passphrase
                        val confirmed = passphraseConfirmation
                        encrypting = true
                        scope.launch {
                            try {
                                // Export is IO and PBKDF2 is CPU work. Both remain cancellable until
                                // the encrypted artifact is ready to enter its durable commit boundary.
                                val exported = withContext(Dispatchers.IO) {
                                    importRepo.exportDatabase().toString()
                                }
                                val encrypted = withContext(Dispatchers.Default) {
                                    com.ironlog.app.data.repository.EncryptedBackupCodec.encrypt(
                                        exported,
                                        phrase,
                                        confirmed,
                                    )
                                }
                                val name = commitBackupArtifactWithCleanup(
                                    commit = {
                                        val out = File(
                                            context.filesDir,
                                            "ironlog_snapshot_encrypted_${System.currentTimeMillis()}.json",
                                        )
                                        out.writeText(encrypted)
                                        repo.setString(
                                            "last_successful_backup_ms",
                                            System.currentTimeMillis().toString(),
                                        )
                                        out.name
                                    },
                                    cleanup = {
                                        WorkoutNotificationBridge
                                            .cancelReminderAfterDataMutation(context)
                                    },
                                    onCleanupFailure = { error ->
                                        Timber.w(
                                            error,
                                            "Could not dismiss a stale reminder after backup commit",
                                        )
                                    },
                                )
                                encryptedStatus = "Encrypted snapshot saved: $name"
                                passphrase = ""
                                passphraseConfirmation = ""
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                encryptedStatus = "Encrypted snapshot failed: ${error.message}"
                            } finally {
                                encrypting = false
                            }
                        }
                    }) { Text(if (encrypting) "Encrypting…" else "Create Encrypted Snapshot") }
                    if (encryptedStatus.isNotBlank()) {
                        Text(encryptedStatus, color = c.accent, fontSize = IronLogType.meta.fontSize.sp)
                    }
                    Button(onClick = {
                        scope.launch {
                            backupFiles = withContext(Dispatchers.IO) { discoverBackups(context) }
                            val latest = backupFiles.firstOrNull()
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    require(latest != null) { "No local backup found." }
                                    val checked = importRepo.previewImportPayload(latest.readText())
                                    require(checked.valid) { checked.reason ?: "Invalid backup" }
                                    "Verified ${latest.name}: ${checked.workouts} workouts, ${checked.sets} sets." +
                                        if (!checked.replacementSafe) " Merge only; orphan rows excluded." else ""
                                }
                            }
                            repo.setString("last_backup_health", if (result.isSuccess) "verified ${latest?.name}" else "failed")
                            status = result.getOrElse { "Validation failed: ${it.message}" }
                        }
                    }) { Text("Validate latest JSON backup") }
                    if (status.isNotBlank()) Text(status, color = c.accent, fontSize = IronLogType.meta.fontSize.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = c.card), border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)) {
                Column(Modifier.fillMaxWidth().appPadding(12.dp), verticalArrangement = appSpacedBy(8.dp)) {
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
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { selectedFile!!.readText() } }
                                    .onSuccess { restorePayload = it; showConfirm = true }
                                    .onFailure { status = "Could not read backup: ${it.message}" }
                            }
                        },
                        enabled = selectedFile != null,
                    ) { Text("Restore Selected Backup") }
                }
            }
        }
    }

    if (showConfirm && restorePayload != null) {
        BackupRestoreDialog(
            restorePayload!!, importRepo, onDismiss = { showConfirm = false },
            trustedRecoveryFile = selectedFile?.takeIf { it.parentFile == File(context.filesDir, "restore-recovery") },
        ) { result ->
            showConfirm = false
            restorePayload = null
            status = "Restored ${result.workouts} workouts." + if (result.recoverySnapshot != null) " Pre-restore recovery snapshot saved." else ""
            scope.launch { backupFiles = withContext(Dispatchers.IO) { discoverBackups(context) } }
        }
    }
}


private fun buildBackupPreview(file: File): String {
    val text = file.readText()
    val checked = ImportExportRepository().previewImportPayload(text)
    return if (checked.valid) "${file.name}\n${checked.workouts} workouts · ${checked.plans} plans · ${checked.sets} sets\n${checked.warnings.joinToString("\n")}" else "Invalid backup: ${checked.reason}"
}


private fun discoverBackups(context: android.content.Context): List<File> {
    val root = context.filesDir
    val autoDir = File(root, "backups")
    val recoveryDir = File(root, "restore-recovery")
    val exportDir = File(root, "exports")
    val files = (root.listFiles().orEmpty().toList() + autoDir.listFiles().orEmpty().toList() + recoveryDir.listFiles().orEmpty().toList() + exportDir.listFiles().orEmpty().toList())
        .filter { it.isFile && it.name.startsWith("ironlog_backup_") && it.extension.equals("json", ignoreCase = true) }
    return files.sortedByDescending { it.lastModified() }
}

