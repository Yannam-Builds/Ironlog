package com.ironlog.app.ui.components

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.ironlog.app.ui.theme.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.state.LoggedSet
import com.ironlog.app.ui.state.WorkoutAction
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.util.formatDurationShort
import com.ironlog.app.util.formatWeightFromKg
import com.ironlog.app.util.convertKgToUnit

private val SetTypes = listOf("normal", "warmup", "drop", "failure", "amrap")
private val TypeLabel = mapOf(
    "normal"  to "W",
    "warmup"  to "WU",
    "drop"    to "DS",
    "failure" to "F",
    "amrap"   to "AMRAP",
)

@OptIn(ExperimentalLayoutApi::class)
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
    val editLoad = if (trackingType == "duration_distance") set.weight else convertKgToUnit(set.weight, weightUnit, 3)
    var editWeight by remember(set.id, editLoad) { mutableStateOf(editLoad.toString()) }
    var editReps by remember(set.id, set.reps) { mutableStateOf(set.reps.toString()) }

    val typeKey = set.type.lowercase().ifBlank { "normal" }
    val typeColor: Color = when (typeKey) {
        "warmup"  -> c.info
        "drop"    -> c.accent
        "failure" -> c.danger
        "amrap"   -> c.gold
        else      -> c.muted
    }
    val isNormal = typeKey == "normal"
    val isTimeBased = trackingType.startsWith("duration")
    val isBodyweight = trackingType.contains("bodyweight")
    val isAssisted = trackingType == "assisted_bodyweight"
    val durationSec = set.durationSec ?: set.reps
    val displayValue = if (isTimeBased) {
        "${formatDurationShort(durationSec)}${if (set.weight > 0) " · ${set.weight}" else ""}"
    } else if (isBodyweight) {
        "BW${if (set.weight > 0) " ${if (isAssisted) "−" else "+"} ${formatWeightFromKg(set.weight, weightUnit)}" else ""} × ${set.reps.toCleanString()}"
    } else {
        "${if (set.weight > 0) formatWeightFromKg(set.weight, weightUnit) else "BW"} × ${set.reps.toCleanString()}"
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().appPadding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = appSpacedBy(8.dp),
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

            // Small visual badge inside an unchanged, accessible 48dp tap target.
            Box(
                Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics {
                        contentDescription = "Set type for set ${setIndex + 1}"
                        stateDescription = if (isNormal) "Working set" else typeKey
                    }
                    .clickable(role = Role.Button) {
                        val next = SetTypes[(SetTypes.indexOf(typeKey).coerceAtLeast(0) + 1) % SetTypes.size]
                        dispatch(WorkoutAction.SetType(exIndex, setIndex, next))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    TypeLabel[typeKey] ?: typeKey.uppercase(),
                    color = if (isNormal) c.muted else typeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.3.sp,
                    modifier = Modifier
                        .background(if (isNormal) Color.Transparent else typeColor.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }

            // Weight × Reps + 1RM on one compact line
            Column(Modifier.weight(1f)) {
                Text(
                    displayValue,
                    color = c.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = IronLogType.body.fontSize.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (set.orm > 0 && !isTimeBased) {
                    Text(
                        "~${formatWeightFromKg(set.orm, weightUnit)} 1RM",
                        color = c.muted,
                        fontSize = IronLogType.micro.fontSize.sp,
                    )
                }
            }

        }

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = appSpacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (effortTracking == "rpe" || effortTracking == "both") {
                    SmallEffortField("RPE", set.rpe?.toCleanString().orEmpty(), setIndex + 1) { text ->
                        dispatch(WorkoutAction.SetRpe(exIndex, setIndex, text.toDoubleOrNull()))
                    }
                }
                if (effortTracking == "rir" || effortTracking == "both") {
                    SmallEffortField("RIR", set.rir?.toString().orEmpty(), setIndex + 1) { text ->
                        dispatch(WorkoutAction.SetRir(exIndex, setIndex, text.toIntOrNull()))
                    }
                }
            }

            // Action icons — SVG, not emoji
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { editOpen = false; noteOpen = !noteOpen },
                    modifier = Modifier.size(48.dp),
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
                        editWeight = editLoad.toString()
                        editReps = set.reps.toString()
                        noteOpen = false
                        editOpen = !editOpen
                    },
                    modifier = Modifier.size(48.dp),
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
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = c.muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Edit row
        AnimatedVisibility(editOpen) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = appSpacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    editWeight,
                    { editWeight = it },
                    label = { Text(when {
                        trackingType == "duration_distance" -> "DISTANCE"
                        isAssisted -> "ASSISTANCE (${weightUnit.uppercase()})"
                        else -> weightUnit.uppercase()
                    }) },
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
            Spacer(Modifier.height(appGapDp(4.dp)))
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
private fun SmallEffortField(label: String, value: String, setNumber: Int, onChange: (String) -> Unit) {
    val c = useTheme()
    var open by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                contentDescription = "$label for set $setNumber"
                stateDescription = value.ifBlank { "Not recorded" }
            }
            .clickable(role = Role.Button) { draft = value; open = true },
        contentAlignment = Alignment.Center,
    ) {
        Text("$label ${value.ifBlank { "—" }}", color = c.subtext, fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.background(c.faint, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp))
    }
    if (open) {
        val valid = isValidEffortInput(label, draft)
        fun save() {
            if (valid) { onChange(draft.trim().replace(',', '.')); open = false }
        }
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = c.card,
            title = { Text("$label · Set $setNumber", color = c.text) },
            text = {
                Column(verticalArrangement = appSpacedBy(8.dp)) {
                    Text(if (label == "RIR") "Reps left in reserve (0–10). Leave blank to clear."
                        else "Perceived effort (1–10). Decimals are supported. Leave blank to clear.", color = c.subtext)
                    OutlinedTextField(draft, { draft = it }, label = { Text(label) },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label value" },
                        singleLine = true, isError = !valid,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (label == "RIR") KeyboardType.Number else KeyboardType.Decimal,
                            imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }))
                    if (!valid) Text(if (label == "RIR") "Enter a whole number from 0 to 10." else "Enter a number from 1 to 10.", color = c.danger)
                }
            },
            confirmButton = { TextButton(onClick = { save() }, enabled = valid) { Text("Save") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

internal fun isValidEffortInput(label: String, raw: String): Boolean {
    val value = raw.trim().replace(',', '.')
    if (value.isBlank()) return true
    return if (label == "RIR") value.toIntOrNull()?.let { it in 0..10 } == true
    else value.toDoubleOrNull()?.let { it.isFinite() && it in 1.0..10.0 } == true
}

private fun Double.toCleanString(): String =
    if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()
