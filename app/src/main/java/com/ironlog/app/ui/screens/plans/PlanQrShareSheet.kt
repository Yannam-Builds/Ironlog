package com.ironlog.app.ui.screens.plans

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.domain.sharing.PlanQrCodec
import com.ironlog.app.ui.model.UiPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share sheet for a [UiPlan].
 *
 * Primary action: Android share intent (works for any plan size).
 * Secondary action: copy deep-link payload to clipboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanQrShareSheet(
    plan: UiPlan,
    onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onShareText: (payload: String) -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val codec = remember { PlanQrCodec() }
    val scope = rememberCoroutineScope()

    var payload by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(plan.id) {
        isLoading = true
        withContext(Dispatchers.Default) {
            payload = runCatching { codec.encodeToPayload(plan) }.getOrDefault("")
        }
        isLoading = false
    }

    val deepLink = "ironlog://import?plan=$payload"

    fun shareViaAndroid() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "IronLog Plan: ${plan.name}")
            putExtra(Intent.EXTRA_TEXT, deepLink)
        }
        context.startActivity(Intent.createChooser(intent, "Share Plan"))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Share Plan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                plan.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            } else {
                // ── Primary: share via any app ────────────────────────────
                Button(
                    onClick = { shareViaAndroid() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share Plan", fontWeight = FontWeight.Bold)
                }

                // ── Secondary: copy link to clipboard ─────────────────────
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(deepLink))
                        copied = true
                        scope.launch {
                            kotlinx.coroutines.delay(2000)
                            copied = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (copied) "Copied!" else "Copy Link")
                }

                Text(
                    "The recipient opens the link in IronLog to import your plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

