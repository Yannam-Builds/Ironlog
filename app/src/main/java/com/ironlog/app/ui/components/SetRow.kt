package com.ironlog.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.state.LoggedSet
import com.ironlog.app.ui.state.WorkoutAction
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.util.formatDurationShort
import com.ironlog.app.util.formatWeightFromKg

private val SetTypes = listOf("normal", "warmup", "drop", "failure", "amrap")
private val TypeLabel = mapOf(
    "normal"  to "W",
    "warmup"  to "WU",
    "drop"    to "DS",
    "failure" to "F",
    "amrap"   to "AMRAP",
)

@Composable
fun SetRow(
    set: LoggedSet,
    setIndex: Int,
    exIndex: Int,
    dispatch: (WorkoutAction) -> Unit,
    effortTracking: String,
    hapticFeedback: Boolean,
    weightUnit: String = "kg",
    trackingType: String = "weight_reps",
) {
    val c = useTheme()
    var noteOpen by remember(set.id) { mutableStateOf(false) }
    var editOpen by remember(set.id) { mutableStateOf(false) }
    var editWeight by remember(set.id, set.weight) { mutableStateOf(set.weight.toString()) }
    var editReps by remember(set.id, set.reps) { mutableStateOf(set.reps.toString()) }

    val typeKey = set.type.ifBlank { "normal" }
    val typeColor: Color = when (typeKey) {
        "warmup"  -> c.info
        "drop"    -> c.accent
        "failure" -> c.danger
        "amrap"   -> c.gold
        else      -> c.muted
    }
    val isNormal = typeKey == "normal"
    val isTimeBased = trackingType.startsWith("duration")
    val durationSec = set.durationSec ?: set.reps
    val displayValue = if (isTimeBased) {
        "${formatDurationShort(durationSec)}${if (set.weight > 0) " · ${set.weight}" else ""}"
    } else {
        "${if (set.weight > 0) formatWeightFromKg(set.weight, weightUnit) else "BW"} × ${set.reps.toCleanString()}"
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // "SET N" label
            Text(
                "SET ${setIndex + 1}",
                color = c.muted,
                fontWeight = FontWeight.Bold,
                fontSize = IronLogType.micro.fontSize.sp,
                letterSpacing = IronLogType.micro.letterSpacing.sp,
                modifier = Modifier.width(44.dp),
            )

            // Type badge — muted tap target for normal, colored for special types
            Box(
                Modifier
                    .background(
                        if (isNormal) c.faint else typeColor.copy(alpha = 0.10f),
                        RoundedCornerShape(IronLogRadius.sm.dp),
                    )
                    .border(
                        1.dp,
                        if (isNormal) c.cardBorder else typeColor.copy(alpha = 0.40f),
                        RoundedCornerShape(IronLogRadius.sm.dp),
                    )
                    .clickable {
                        val next = SetTypes[(SetTypes.indexOf(typeKey).coerceAtLeast(0) + 1) % SetTypes.size]
                        dispatch(WorkoutAction.SetType(exIndex, setIndex, next))
                    }
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    TypeLabel[typeKey] ?: typeKey.uppercase(),
                    color = if (isNormal) c.muted else typeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = IronLogType.micro.fontSize.sp,
                    letterSpacing = 0.3.sp,
                )
            }

            // Weight × Reps + 1RM on one compact line
            Column(Modifier.weight(1f)) {
                Text(
                    displayValue,
                    color = c.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = IronLogType.body.fontSize.sp,
                    maxLines = 1,
                )
                if (set.orm > 0 && !isTimeBased) {
                    Text(
                        "~${formatWeightFromKg(set.orm, weightUnit)} 1RM",
                        color = c.muted,
                        fontSize = IronLogType.micro.fontSize.sp,
                    )
                }
            }

            // Effort tracking
            if (effortTracking == "rpe" || effortTracking == "both") {
                SmallEffortField("RPE", set.rpe?.toString().orEmpty()) { text ->
                    dispatch(WorkoutAction.SetRpe(exIndex, setIndex, text.toDoubleOrNull()))
                }
            }
            if (effortTracking == "rir" || effortTracking == "both") {
                SmallEffortField("RIR", set.rir?.toString().orEmpty()) { text ->
                    dispatch(WorkoutAction.SetRir(exIndex, setIndex, text.toIntOrNull()))
                }
            }

            // Action icons — SVG, not emoji
            IconButton(
                onClick = { editOpen = false; noteOpen = !noteOpen },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Note",
                    tint = if (noteOpen || !set.note.isNullOrBlank()) c.accent else c.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = {
                    editWeight = set.weight.toString()
                    editReps = set.reps.toString()
                    noteOpen = false
                    editOpen = !editOpen
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Edit",
                    tint = if (editOpen) c.accent else c.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = { dispatch(WorkoutAction.DeleteSet(exIndex, setIndex)) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = c.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Edit row
        AnimatedVisibility(editOpen) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    editWeight,
                    { editWeight = it },
                    label = { Text(weightUnit.uppercase()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    editReps,
                    { editReps = it },
                    label = { Text(if (isTimeBased) "SECONDS" else "REPS") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        dispatch(
                            WorkoutAction.UpdateSet(
                                exIndex,
                                setIndex,
                                editWeight.toDoubleOrNull() ?: 0.0,
                                editReps.toDoubleOrNull() ?: 0.0,
                            ),
                        )
                        editOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                ) {
                    Text("SAVE", color = c.textOnAccent, fontWeight = FontWeight(IronLogType.button.fontWeight))
                }
            }
        }

        // Note field
        AnimatedVisibility(noteOpen) {
            OutlinedTextField(
                value = set.note.orEmpty(),
                onValueChange = { dispatch(WorkoutAction.SetNote(exIndex, setIndex, it)) },
                placeholder = { Text("Set note...") },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                minLines = 2,
            )
        }
        if (!noteOpen && !set.note.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                set.note.orEmpty(),
                color = c.muted,
                fontSize = IronLogType.meta.fontSize.sp,
                maxLines = 1,
                modifier = Modifier.clickable { noteOpen = true },
            )
        }
    }
}

@Composable
private fun SmallEffortField(label: String, value: String, onChange: (String) -> Unit) {
    val c = useTheme()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = c.muted, fontWeight = FontWeight.Bold, fontSize = IronLogType.micro.fontSize.sp)
        OutlinedTextField(
            value,
            onChange,
            modifier = Modifier.width(52.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}

private fun Double.toCleanString(): String =
    if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()
