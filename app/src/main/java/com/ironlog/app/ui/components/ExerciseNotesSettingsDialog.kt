package com.ironlog.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.ui.theme.Text
import com.ironlog.app.ui.theme.appSpacedBy

/** Shared, explicit plan-instruction controls used from both editing and live logging. */
@Composable
fun ExerciseNotesSettingsDialog(
    title: String,
    deleteLabel: String,
    deleteWarning: String,
    notesVisible: Boolean,
    isDeleting: Boolean,
    error: String?,
    onNotesVisibleChange: (Boolean) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = useTheme()
    var confirmingDelete by remember(title) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        containerColor = c.card,
        title = { Text(if (confirmingDelete) "Delete notes?" else title, color = c.text) },
        text = {
            if (confirmingDelete) {
                Column(verticalArrangement = appSpacedBy(10.dp)) {
                    Text(deleteWarning, color = c.text, fontSize = IronLogType.body.fontSize.sp)
                    Text(
                        "This cannot be undone. Set notes, session notes, history, and next-session reminders are not affected.",
                        color = c.muted,
                        fontSize = IronLogType.meta.fontSize.sp,
                    )
                    error?.let { Text(it, color = c.danger, fontSize = IronLogType.meta.fontSize.sp) }
                }
            } else {
                Column(verticalArrangement = appSpacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = appSpacedBy(2.dp)) {
                            Text("Show exercise notes", color = c.text, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (notesVisible) "Plan instructions are visible." else "Hidden only — saved notes are kept.",
                                color = c.muted,
                                fontSize = IronLogType.meta.fontSize.sp,
                            )
                        }
                        IronLogSwitch(
                            checked = notesVisible,
                            onCheckedChange = onNotesVisibleChange,
                            modifier = Modifier.semantics {
                                contentDescription = "Show exercise notes"
                            },
                        )
                    }
                    TextButton(
                        onClick = { confirmingDelete = true },
                        enabled = !isDeleting,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(deleteLabel, color = c.danger, fontWeight = FontWeight.Bold)
                    }
                    error?.let { Text(it, color = c.danger, fontSize = IronLogType.meta.fontSize.sp) }
                }
            }
        },
        confirmButton = {
            if (confirmingDelete) {
                Button(
                    onClick = onDeleteConfirmed,
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = c.danger),
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = c.onDanger,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("DELETE", color = c.onDanger, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("DONE", color = c.accent) }
            }
        },
        dismissButton = {
            if (confirmingDelete) {
                TextButton(onClick = { confirmingDelete = false }, enabled = !isDeleting) {
                    Text("CANCEL", color = c.muted)
                }
            }
        },
    )
}
