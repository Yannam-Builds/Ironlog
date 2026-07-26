package com.ironlog.app.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.ironlog.app.data.health.BiometricSnapshot
import com.ironlog.app.data.health.HealthConnectRepository
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HealthConnectScreen(
    onBack: () -> Unit,
) {
    val c = useTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { HealthConnectRepository(context) }

    var isAvailable by remember { mutableStateOf(repo.isAvailable()) }
    var grantedCount by remember { mutableStateOf(0) }
    var hasAllPermissions by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf(BiometricSnapshot()) }
    var statusText by remember { mutableStateOf("Checking Health Connect...") }
    var loading by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            loading = true
            isAvailable = repo.isAvailable()
            if (!isAvailable) {
                grantedCount = 0
                hasAllPermissions = false
                snapshot = BiometricSnapshot()
                statusText = "Health Connect is not available on this device yet."
                loading = false
                return@launch
            }
            val granted = withContext(Dispatchers.IO) { repo.grantedPermissions() }
            grantedCount = granted.size
            hasAllPermissions = granted.containsAll(repo.requiredPermissions)
            val canReadRecovery = granted.containsAll(repo.readPermissions)
            snapshot = if (canReadRecovery) {
                withContext(Dispatchers.IO) { repo.readBiometricSnapshot() }
            } else {
                BiometricSnapshot()
            }
            statusText = when {
                hasAllPermissions -> "Connected. Ironlog can enrich recovery and sync workouts and body weight."
                canReadRecovery -> "Recovery signals are connected. Grant write access to sync workouts and body weight."
                else -> "Permissions needed before Ironlog can read recovery signals."
            }
            loading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        grantedCount = granted.size
        hasAllPermissions = granted.containsAll(repo.requiredPermissions)
        refresh()
    }

    LaunchedEffect(Unit) { refresh() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.text)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Health Connect",
                        color = c.text,
                        fontWeight = FontWeight(IronLogType.title.fontWeight),
                        fontSize = IronLogType.title.fontSize.sp,
                    )
                    Text(
                        "Recovery signals, workout sync, and body-weight writes.",
                        color = c.subtext,
                        fontSize = IronLogType.meta.fontSize.sp,
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = BorderStroke(1.dp, if (hasAllPermissions) c.success.copy(alpha = 0.55f) else c.cardBorder),
                shape = RoundedCornerShape(IronLogRadius.xl.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(if (hasAllPermissions) c.success.copy(alpha = 0.16f) else c.accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.HealthAndSafety,
                                contentDescription = null,
                                tint = if (hasAllPermissions) c.success else c.accent,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (hasAllPermissions) "Connected" else if (isAvailable) "Ready to connect" else "Unavailable",
                                color = c.text,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = IronLogType.section.fontSize.sp,
                            )
                            Text(statusText, color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                        }
                    }

                    Text(
                        "$grantedCount / ${repo.requiredPermissions.size} permissions granted",
                        color = c.muted,
                        fontSize = IronLogType.meta.fontSize.sp,
                    )

                    Button(
                        onClick = { permissionLauncher.launch(repo.requiredPermissions) },
                        enabled = isAvailable && !loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (hasAllPermissions) "Review Permissions" else "Connect Health Connect")
                    }

                    OutlinedButton(
                        onClick = { refresh() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Refresh Signals")
                    }
                }
            }
        }

        item {
            Text(
                "LATEST SIGNALS",
                color = c.muted,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SignalRow(
                    label = "Sleep",
                    value = snapshot.sleepHours?.let { String.format(java.util.Locale.US, "%.1fh", it) } ?: "--",
                    helper = "Last 36 hours",
                    icon = Icons.Outlined.Hotel,
                )
                SignalRow(
                    label = "Resting HR",
                    value = snapshot.restingHrBpm?.let { "$it bpm" } ?: "--",
                    helper = "Lower is usually better recovered",
                    icon = Icons.Outlined.Favorite,
                )
                SignalRow(
                    label = "HRV",
                    value = snapshot.hrvRmssd?.let { String.format(java.util.Locale.US, "%.0f ms", it) } ?: "--",
                    helper = "Used as a recovery confidence signal",
                    icon = Icons.Outlined.MonitorHeart,
                )
            }
        }

        item {
            SettingsOpenRow(
                label = "Open Android Health Connect settings",
                onClick = {
                    val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        "android.health.connect.action.HEALTH_HOME_SETTINGS"
                    } else {
                        "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
                    }
                    val intent = Intent(action)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            context.startActivity(
                                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                },
            )
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SignalRow(
    label: String,
    value: String,
    helper: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val c = useTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp)
            Text(helper, color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
        }
        Text(value, color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = IronLogType.section.fontSize.sp)
    }
}

@Composable
private fun SettingsOpenRow(label: String, onClick: () -> Unit) {
    val c = useTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = c.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = c.muted)
    }
}

