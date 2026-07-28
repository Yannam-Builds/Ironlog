package com.ironlog.app.ui.screens.onboarding.steps

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.ironlog.app.R
import com.ironlog.app.data.health.HealthConnectRepository
import com.ironlog.app.ui.screens.onboarding.GlowButton
import com.ironlog.app.ui.screens.onboarding.OnboardingConfig
import com.ironlog.app.ui.screens.onboarding.OnboardingPageHeader
import com.ironlog.app.ui.screens.onboarding.SetupReward

@Composable
fun Step7Permissions(
    cameraGranted: Boolean,
    healthConnectGranted: Boolean,
    notificationsGranted: Boolean,
    onCameraGranted: (Boolean) -> Unit,
    onHealthConnectGranted: (Boolean) -> Unit,
    onNotificationsGranted: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val healthRepo = remember(context) { HealthConnectRepository(context.applicationContext) }
    var healthAvailable by remember { mutableStateOf(false) }
    var healthStatus by remember { mutableStateOf<String?>(null) }
    var healthSdkStatus by remember { mutableStateOf(HealthConnectClient.SDK_UNAVAILABLE) }

    fun openHealthConnectSettings() {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            "android.health.connect.action.HEALTH_HOME_SETTINGS"
        } else {
            "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
        }
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openHealthConnectInstallOrUpdate() {
        val hcPackage = "com.google.android.apps.healthdata"
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$hcPackage"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$hcPackage"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(marketIntent) }
            .onFailure { context.startActivity(webIntent) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onCameraGranted(granted) }

    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val ok = granted.containsAll(healthRepo.requiredPermissions)
        onHealthConnectGranted(ok)
        healthStatus = if (ok) "Health Connect is connected." else "Health Connect was not granted. You can enable it later in Settings."
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onNotificationsGranted(granted) }

    LaunchedEffect(Unit) {
        healthSdkStatus = HealthConnectClient.getSdkStatus(context.applicationContext)
        healthAvailable = healthRepo.isAvailable()
        if (!healthAvailable) {
            healthStatus = when (healthSdkStatus) {
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    "Health Connect needs install/update. Tap Allow to open Play Store."
                else ->
                    "Health Connect is not available on this device."
            }
        } else if (healthRepo.hasAllPermissions()) {
            onHealthConnectGranted(true)
            healthStatus = "Health Connect is already connected."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingConfig.bgDark)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OnboardingPageHeader(
            step = "Optional connections",
            title = "Enable benefits when you need them.",
            body = "Nothing here blocks training. IronLog asks the system only after you tap Allow, and every connection can be changed later.",
        )
        Spacer(Modifier.height(22.dp))

        PermissionCard(
            title = stringResource(R.string.onb_perm_camera_title),
            description = stringResource(R.string.onb_perm_camera_desc),
            granted = cameraGranted,
            onGrant = { cameraLauncher.launch(Manifest.permission.CAMERA) },
        )
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            title = stringResource(R.string.onb_perm_health_title),
            description = stringResource(R.string.onb_perm_health_desc),
            granted = healthConnectGranted,
            status = healthStatus,
            onGrant = {
                if (!healthAvailable) {
                    healthStatus = if (healthSdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                        "Opening Health Connect in Play Store..."
                    } else {
                        "Opening Health Connect settings..."
                    }
                    if (healthSdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                        openHealthConnectInstallOrUpdate()
                    } else {
                        openHealthConnectSettings()
                    }
                } else {
                    hcLauncher.launch(healthRepo.requiredPermissions)
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            title = stringResource(R.string.onb_perm_notif_title),
            description = stringResource(R.string.onb_perm_notif_desc),
            granted = notificationsGranted,
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onNotificationsGranted(true)
                }
            },
        )

        Spacer(Modifier.height(20.dp))
        SetupReward("Health data improves recovery estimates; notifications protect planned-session streaks", Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        GlowButton(text = stringResource(R.string.onb_perm_cta), onClick = onNext)
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    status: String? = null,
    onGrant: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (granted) OnboardingConfig.grantedColor else OnboardingConfig.cardBorder,
        animationSpec = tween(300),
        label = "permBorder",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(OnboardingConfig.surfaceDark)
            .border(if (granted) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(description, color = OnboardingConfig.textMuted, fontSize = 13.sp, lineHeight = 18.sp)
            if (!status.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(status, color = if (granted) OnboardingConfig.grantedColor else OnboardingConfig.accentGold, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(
            onClick = onGrant,
            enabled = !granted,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (granted) OnboardingConfig.grantedColor.copy(alpha = 0.18f) else Color.Transparent,
                contentColor = if (granted) Color.White else OnboardingConfig.accentBlue,
            ),
            border = BorderStroke(1.dp, if (granted) OnboardingConfig.grantedColor else OnboardingConfig.accentBlue),
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                text = if (granted) stringResource(R.string.onb_perm_granted) else stringResource(R.string.onb_perm_grant),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
