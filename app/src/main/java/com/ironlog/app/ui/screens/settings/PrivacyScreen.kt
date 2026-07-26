package com.ironlog.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType

private val PRIVACY_POINTS = listOf(
    "Your workout data stays local on this device unless you explicitly export or import a backup file.",
    "Plain JSON exports include structured app data. Progress photos and custom media stay local in this release.",
    "Advanced encrypted snapshots are protected by your passphrase before they leave the app.",
    "Google Drive sync is disabled in this build, so cloud storage is only used when you manually share an export there.",
    "IronLog never silently restores over live data. Every restore shows a preview first and creates a rollback snapshot when possible.",
)

@Composable
fun PrivacyScreen(onBack: () -> Unit = {}) {
    val colors = useTheme()
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenHeader(title = "PRIVACY", onBack = onBack)
        Column(Modifier.background(colors.card, RoundedCornerShape(14.dp)).border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("LOCAL-FIRST BY DEFAULT", color = colors.text, fontSize = IronLogType.title.fontSize.sp)
            Text("IronLog stores your training history locally first. Export and restore are explicit actions, so your data never moves unless you choose it.", color = colors.subtext, fontSize = IronLogType.body.fontSize.sp, lineHeight = 20.sp)
        }
        PRIVACY_POINTS.forEach { point -> Text(point, color = colors.text, fontSize = IronLogType.body.fontSize.sp, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(10.dp)).border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp)).padding(16.dp)) }
    }
}


