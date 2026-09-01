package com.ironlog.app.ui.screens.settings

import com.ironlog.app.ui.theme.appSpacedBy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.ironlog.app.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType

private val PRIVACY_POINTS = listOf(
    "Most training data stays local on this device. It leaves IronLog only when you export or share it, or when you enable and use a Cloud AI feature.",
    "Cloud AI sends a request and selected training context to the provider you configure. Depending on the feature, that can include profile goals, schedule and equipment, plan or exercise names, workout dates, set counts, volume, bodyweight, and readiness or recovery summaries. Some configured Cloud AI insights refresh when you open their screen. Your provider's terms and privacy policy apply.",
    "Your Cloud AI API key is encrypted on this device and sent to the provider endpoint you configure to authorize each request. It is not included in IronLog backup exports.",
    "Built-in program intelligence stays on device. Progress-photo image files and Health Connect data are not included in Cloud AI requests in this build.",
    "Plain JSON exports include structured app data and progress-photo metadata, but not the image files themselves.",
    "Advanced encrypted snapshots are protected by your passphrase before they leave the app.",
    "Google Drive sync is disabled in this build, so cloud storage is only used when you manually share an export there.",
    "IronLog never silently restores over live data. Every restore shows a preview first and creates a rollback snapshot when possible.",
    "Recovery, readiness, progression, and training suggestions are informational estimates. They are not medical advice and do not diagnose, treat, or prevent any condition.",
)

@Composable
fun PrivacyScreen(onBack: () -> Unit = {}) {
    val colors = useTheme()
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(), verticalArrangement = appSpacedBy(16.dp)) {
        ScreenHeader(title = "PRIVACY", onBack = onBack)
        Column(Modifier.background(colors.card, RoundedCornerShape(14.dp)).border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp)).padding(20.dp), verticalArrangement = appSpacedBy(10.dp)) {
            Text("LOCAL-FIRST BY DEFAULT", color = colors.text, fontSize = IronLogType.title.fontSize.sp)
            Text("IronLog stores your training history locally first. Export, sharing, and optional Cloud AI are the clearly described exceptions below.", color = colors.subtext, fontSize = IronLogType.body.fontSize.sp, lineHeight = 20.sp)
        }
        PRIVACY_POINTS.forEach { point -> Text(point, color = colors.text, fontSize = IronLogType.body.fontSize.sp, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(10.dp)).border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp)).padding(16.dp)) }
    }
}


