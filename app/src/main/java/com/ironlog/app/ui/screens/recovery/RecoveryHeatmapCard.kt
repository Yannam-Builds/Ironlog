package com.ironlog.app.ui.screens.recovery

import com.ironlog.app.ui.screens.body.BodyHalfCanvas
import com.ironlog.app.ui.screens.body.BodyMapDataset
import com.ironlog.app.ui.screens.body.ViewBox
import com.ironlog.app.ui.screens.body.BodyPathPiece
import com.ironlog.app.ui.screens.body.loadBodyMapDataset
import com.ironlog.app.ui.screens.body.buildDisplayReadiness
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.domain.intelligence.ManualRecoveryInput
import com.ironlog.app.domain.intelligence.RecoveryReadinessEngine
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType

private val LEGEND = listOf(
    "< 72%"   to "danger",
    "72–90%"  to "warning",
    "> 90%"   to "success",
    "Unknown" to "faint",
)

/**
 * Compact muscle-map card shown on the HomeScreen.
 * Mirrors the RN RecoveryHeatmap component.
 *
 * @param groupReadiness  Output of [RecoveryReadinessEngine.readinessByRegion] —
 *                        keyed by group name (Push, Pull, Legs, …).
 */
@Composable
fun RecoveryHeatmapCard(
    groupReadiness: Map<String, Double>,
    manualRecoveryInput: ManualRecoveryInput? = null,
    onTapExpand: () -> Unit,
    onOpenVolumeAnalytics: () -> Unit,
) {
    val c = useTheme()
    val context = LocalContext.current
    val bodyMapDataset   = remember { loadBodyMapDataset(context) }
    val displayReadiness = remember(groupReadiness) { buildDisplayReadiness(groupReadiness) }
    val recoveryScore = remember(groupReadiness) { RecoveryReadinessEngine.score(groupReadiness) }

    // Fallback viewBox dimensions (actual values from the asset)
    val frontVb = bodyMapDataset?.front?.viewBox ?: ViewBox(-20f, 95f, 740f, 1300f)
    val backVb  = bodyMapDataset?.back?.viewBox  ?: ViewBox(740f, 95f, 680f, 1320f)
    val frontAlignVb = bodyMapDataset?.front?.alignmentBox ?: frontVb
    val backAlignVb = bodyMapDataset?.back?.alignmentBox ?: backVb
    val fitWidth = maxOf(frontAlignVb.width, backAlignVb.width)
    val fitHeight = maxOf(frontAlignVb.height, backAlignVb.height)
    val sharedAspect = fitWidth / fitHeight
    val cardMapHeight = 280.dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.cardBorder),
        shape  = RoundedCornerShape(14.dp),
    ) {
        // ── Tappable body (header + maps + legend) ────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onTapExpand)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Header row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    "MUSCLE RECOVERY",
                    color       = c.muted,
                    fontSize = IronLogType.micro.fontSize.sp,
                    fontWeight  = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                Text(
                    "TAP TO EXPAND  →",
                    color     = c.muted,
                    fontSize = IronLogType.micro.fontSize.sp,
                    letterSpacing = 0.5.sp,
                )
            }

            Spacer(Modifier.height(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${recoveryScore.score}",
                    color = when {
                        recoveryScore.score >= 78 -> c.success
                        recoveryScore.score >= 55 -> c.warning
                        else -> c.danger
                    },
                    fontSize = IronLogType.display.fontSize.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    recoveryScore.state.replaceFirstChar { it.titlecase() },
                    color = c.muted,
                    fontSize = IronLogType.meta.fontSize.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(8.dp))

            // Maps + legend row
            Row(
                verticalAlignment     = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Front
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("FRONT", color = c.muted, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                    Spacer(Modifier.height(2.dp))
                    BodyMapFixedSlot(
                        pieces = bodyMapDataset?.front?.pieces.orEmpty(),
                        alignmentBox = frontAlignVb,
                        readiness = displayReadiness,
                        fitWidth = fitWidth,
                        fitHeight = fitHeight,
                        aspect = sharedAspect,
                        modifier = Modifier.fillMaxWidth().height(cardMapHeight),
                    )
                }
                // Back
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("BACK", color = c.muted, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                    Spacer(Modifier.height(2.dp))
                    BodyMapFixedSlot(
                        pieces = bodyMapDataset?.back?.pieces.orEmpty(),
                        alignmentBox = backAlignVb,
                        readiness = displayReadiness,
                        fitWidth = fitWidth,
                        fitHeight = fitHeight,
                        aspect = sharedAspect,
                        modifier = Modifier.fillMaxWidth().height(cardMapHeight),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            RecoveryLegendFlow()
        }

        // ── Analytics button — styled like RN (accent border + tinted bg) ─────
        HorizontalDivider(color = c.cardBorder)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.accent.copy(alpha = 0.10f))
                .clickable(onClick = onOpenVolumeAnalytics)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "OPEN VOLUME ANALYTICS",
                color         = c.accent,
                fontSize = IronLogType.eyebrow.fontSize.sp,
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = 1.3.sp,
            )
        }
    }
}

@Composable
private fun BodyMapFixedSlot(
    pieces: List<BodyPathPiece>,
    alignmentBox: ViewBox,
    readiness: Map<String, Double>,
    fitWidth: Float,
    fitHeight: Float,
    aspect: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        val canvasH = minOf(maxHeight, maxWidth / aspect)
        val canvasW = canvasH * aspect
        BodyHalfCanvas(
            pieces = pieces,
            viewBox = alignmentBox,
            readiness = readiness,
            modifier = Modifier.size(canvasW, canvasH),
            fitWidth = fitWidth,
            fitHeight = fitHeight,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecoveryLegendFlow() {
    val c = useTheme()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LEGEND.forEach { (label, key) ->
            val dotColor = when (key) {
                "danger" -> c.danger
                "warning" -> c.warning
                "success" -> c.success
                else -> c.faint
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Text(
                    label,
                    color = c.subtext,
                    fontSize = IronLogType.meta.fontSize.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

