package com.ironlog.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.data.repository.ImportExportRepository
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Composable
fun RestoreDataScreen(
    onBack: () -> Unit = {},
    onOpenImportCenter: () -> Unit = {},
    onOpenBackupCenter: () -> Unit = {},
    onOpenDataPortability: (String) -> Unit = {},
    settingsRepository: SettingsRepository = SettingsRepository(),
    importExportRepository: ImportExportRepository = ImportExportRepository(),
) {
    val c = useTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupHealth by remember { mutableStateOf("unknown") }
    var selectedSource by remember { mutableStateOf("strong_csv") }
    var step by remember { mutableStateOf(1) }
    var encryptedBackups by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedEncrypted by remember { mutableStateOf<File?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var decryptedPayload by remember { mutableStateOf<String?>(null) }
    var previewText by remember { mutableStateOf("") }
    var restoreStatus by remember { mutableStateOf("") }
    var replaceConfirm by remember { mutableStateOf("") }
    var isDecrypting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        backupHealth = settingsRepository.getString("last_backup_health").orEmpty().ifBlank { "unknown" }
        val root = context.filesDir
        encryptedBackups = withContext(Dispatchers.IO) {
            root.listFiles().orEmpty()
                .filter { it.isFile && it.name.startsWith("ironlog_snapshot_encrypted_") && it.extension.equals("json", true) }
                .sortedByDescending { it.lastModified() }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(title = "RESTORE DATA", onBack = onBack) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = c.card), border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Encrypted Restore Wizard", color = c.text, fontSize = IronLogType.section.fontSize.sp)
                    Text("Step $step / 4", color = c.accent, fontSize = IronLogType.meta.fontSize.sp)
                    when (step) {
                        1 -> {
                            Text("Select encrypted snapshot file", color = c.subtext)
                            if (encryptedBackups.isEmpty()) {
                                Text("No encrypted snapshots found.", color = c.muted)
                            } else {
                                encryptedBackups.forEach { file ->
                                    AssistChip(
                                        onClick = { selectedEncrypted = file },
                                        label = { Text(file.name, color = if (selectedEncrypted?.absolutePath == file.absolutePath) c.accent else c.text) },
                                    )
                                }
                            }
                            Button(onClick = { if (selectedEncrypted != null) step = 2 }) { Text("Next: Decrypt") }
                        }
                        2 -> {
                            OutlinedTextField(
                                value = passphrase,
                                onValueChange = { passphrase = it },
                                label = { Text("Passphrase") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                enabled = !isDecrypting,
                                onClick = {
                                    val file = selectedEncrypted
                                    if (file != null && passphrase.length >= 8 && !isDecrypting) {
                                        isDecrypting = true
                                        scope.launch(Dispatchers.IO) {
                                            runCatching {
                                                val raw = file.readText()
                                                val payload = decryptSnapshot(raw, passphrase)
                                                val preview = importExportRepository.previewImportPayload(payload)
                                                Triple(payload, "Preview: workouts=${preview.workouts}, plans=${preview.plans}, sets=${preview.sets}, body=${preview.bodyMeasurements}, warnings=${preview.warnings.size}", null as String?)
                                            }.fold(
                                                onSuccess = { (payload, preview, _) ->
                                                    decryptedPayload = payload
                                                    previewText = preview
                                                    restoreStatus = ""
                                                    step = 3
                                                },
                                                onFailure = {
                                                    restoreStatus = "Decrypt failed: ${it.message}"
                                                },
                                            )
                                            isDecrypting = false
                                        }
                                    }
                                },
                            ) { Text(if (isDecrypting) "Decrypting…" else "Decrypt & Preview") }
                        }
                        3 -> {
                            Text(previewText, color = c.subtext)
                            OutlinedTextField(
                                value = replaceConfirm,
                                onValueChange = { replaceConfirm = it },
                                label = { Text("Type REPLACE to confirm") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { if (replaceConfirm.trim().uppercase() == "REPLACE") step = 4 },
                                enabled = replaceConfirm.trim().uppercase() == "REPLACE",
                            ) { Text("Next: Confirm Restore") }
                        }
                        else -> {
                            Button(onClick = {
                                val payload = decryptedPayload
                                if (payload != null) {
                                    scope.launch(Dispatchers.IO) {
                                        runCatching {
                                            importExportRepository.runConfirmedImport(payload, mode = "replace")
                                        }.onSuccess {
                                            restoreStatus = "Encrypted restore completed."
                                        }.onFailure {
                                            restoreStatus = "Restore failed: ${it.message}"
                                        }
                                    }
                                }
                            }) { Text("Run Restore Now") }
                            Button(onClick = {
                                step = 1
                                selectedEncrypted = null
                                passphrase = ""
                                decryptedPayload = null
                                replaceConfirm = ""
                            }) { Text("Reset Wizard") }
                        }
                    }
                    if (restoreStatus.isNotBlank()) {
                        Text(restoreStatus, color = c.accent, fontSize = IronLogType.meta.fontSize.sp)
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = c.card), border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restore from local backup or import normalized files.", color = c.subtext)
                    Text("Backup health: $backupHealth", color = c.accent, fontSize = IronLogType.meta.fontSize.sp)
                    Button(onClick = onOpenBackupCenter) { Text("Restore from Backup Center") }
                    Button(onClick = onOpenImportCenter) { Text("Open Import Center") }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = c.card), border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Direct Source Import", color = c.text, fontSize = IronLogType.section.fontSize.sp)
                    AssistChip(onClick = { selectedSource = "strong_csv" }, label = { Text("Strong CSV", color = if (selectedSource == "strong_csv") c.accent else c.text) })
                    AssistChip(onClick = { selectedSource = "hevy_csv" }, label = { Text("Hevy CSV", color = if (selectedSource == "hevy_csv") c.accent else c.text) })
                    AssistChip(onClick = { selectedSource = "openweight_json" }, label = { Text("OpenWeight JSON", color = if (selectedSource == "openweight_json") c.accent else c.text) })
                    AssistChip(onClick = { selectedSource = "ironlog_json" }, label = { Text("Ironlog Backup JSON", color = if (selectedSource == "ironlog_json") c.accent else c.text) })
                    Button(onClick = { onOpenDataPortability(selectedSource) }) { Text("Open Validated Import Preview") }
                    Text("Path: Restore Data -> Data Portability ($selectedSource)", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                }
            }
        }
    }
}

private fun decryptSnapshot(raw: String, passphrase: String): String {
    val obj = JSONObject(raw)
    require(obj.optString("schema") == "IRONLOG_ENCRYPTED_EXPORT_V1") { "Unsupported encrypted schema" }
    val salt = android.util.Base64.decode(obj.getString("saltB64"), android.util.Base64.DEFAULT)
    val iv = android.util.Base64.decode(obj.getString("ivB64"), android.util.Base64.DEFAULT)
    val cipherBytes = android.util.Base64.decode(obj.getString("ciphertextB64"), android.util.Base64.DEFAULT)
    val iterations = obj.optInt("iterations", 120000)
    val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
    val secret = SecretKeySpec(keyFactory.generateSecret(spec).encoded, "AES")
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
    return String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
}

