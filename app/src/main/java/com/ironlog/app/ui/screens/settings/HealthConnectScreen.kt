package com.ironlog.app.ui.screens.settings

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.ironlog.app.ui.theme.Text
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
import com.ironlog.app.data.health.canReadAnyHealthContext
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
            grantedCount = granted.intersect(repo.requiredPermissions).size
            hasAllPermissions = granted.containsAll(repo.requiredPermissions)
            val canReadContext = canReadAnyHealthContext(granted, repo.readPermissions)
            snapshot = if (canReadContext) {
                withContext(Dispatchers.IO) { repo.readBiometricSnapshot() }
            } else {
                BiometricSnapshot()
            }
            statusText = when {
                hasAllPermissions -> "Connected for read-only context."
                canReadContext -> "Partially connected. Available health context is shown; grant the remaining permissions to fill missing fields."
                else -> "Permission is needed before IronLog can display recent health context."
            }
            loading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        grantedCount = granted.intersect(repo.requiredPermissions).size
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
        verticalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(16.dp),
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
                        "Read-only health context",
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
                    modifier = Modifier.appPadding(18.dp),
                    verticalArrangement = appSpacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = appSpacedBy(12.dp)) {
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
                        Text(if (hasAllPermissions) "Review Permissions" else "Connect read-only access")
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
                "These values are shown for context only and do not change your readiness score. " +
                    "IronLog does not write workouts or body weight to Health Connect in this build.",
                color = c.subtext,
                fontSize = IronLogType.meta.fontSize.sp,
                modifier = Modifier.appPadding(horizontal = 4.dp),
            )
        }

        item {
            Text(
                "LATEST SIGNALS",
                color = c.muted,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.appPadding(horizontal = 4.dp),
            )
        }

        item {
            Column(verticalArrangement = appSpacedBy(10.dp)) {
                SignalRow(
                    label = "Sleep",
                    value = snapshot.sleepHours?.let { String.format(java.util.Locale.US, "%.1fh", it) } ?: "--",
                    helper = "Most recent session in the last 36 hours",
                    icon = Icons.Outlined.Hotel,
                )
                SignalRow(
                    label = "Resting HR",
                    value = snapshot.restingHrBpm?.let { "$it bpm" } ?: "--",
                    helper = "Raw value; interpret against your own trend",
                    icon = Icons.Outlined.Favorite,
                )
                SignalRow(
                    label = "HRV",
                    value = snapshot.hrvRmssd?.let { String.format(java.util.Locale.US, "%.0f ms", it) } ?: "--",
                    helper = "Raw value; no population cutoff is applied",
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

        item { Spacer(Modifier.height(appGapDp(12.dp))) }
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
        horizontalArrangement = appSpacedBy(12.dp),
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

