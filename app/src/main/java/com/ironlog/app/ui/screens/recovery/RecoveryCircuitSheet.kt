package com.ironlog.app.ui.screens.recovery

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedCard
import com.ironlog.app.ui.theme.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.ironlog.app.data.repository.RecoveryCircuitResult
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironlog.app.R

data class RecoveryCircuit(
    val id: String,
    val name: String,
    val category: String,  // "Push", "Pull", "Core", "Full Body"
    val exercises: List<String>,
    val instructions: String,
)

private val CIRCUITS = listOf(
    RecoveryCircuit(
        id = "push_basic",
        name = "Push Circuit",
        category = "Push",
        exercises = listOf("20 Push-ups", "15 Tricep Dips (chair)", "10 Pike Push-ups"),
        instructions = "3 rounds, 60 sec rest between rounds.",
    ),
    RecoveryCircuit(
        id = "pull_basic",
        name = "Pull Circuit",
        category = "Pull",
        exercises = listOf("10 Pull-ups (or 15 Inverted Rows)", "12 Chin-ups", "20 Band Pull-Aparts"),
        instructions = "3 rounds, 90 sec rest between rounds.",
    ),
    RecoveryCircuit(
        id = "core_basic",
        name = "Core Circuit",
        category = "Core",
        exercises = listOf("30 Crunches", "20 Leg Raises", "60 sec Plank", "20 Russian Twists"),
        instructions = "3 rounds, 45 sec rest between rounds.",
    ),
    RecoveryCircuit(
        id = "fullbody_basic",
        name = "Full Body Circuit",
        category = "Full Body",
        exercises = listOf("20 Burpees", "20 Squats", "15 Push-ups", "10 Pull-ups", "30 sec Plank"),
        instructions = "4 rounds, 90 sec rest between rounds.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryCircuitSheet(
    onDismiss: () -> Unit,
    onComplete: suspend (circuitId: String) -> RecoveryCircuitResult,
) {
    var selected: RecoveryCircuit? by remember { mutableStateOf(null) }
    var confirmed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.recovery_circuit_emblem),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Fit,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Recovery Circuit",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        if (confirmed) "Circuit in progress" else "Choose a short proof session",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                "Use this only when a full workout is not practical. Pick a circuit you can perform with clean technique; stop if an exercise causes pain.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!confirmed) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(CIRCUITS, key = { it.id }) { circuit ->
                        val isSelected = selected?.id == circuit.id
                        OutlinedCard(
                            onClick = { selected = circuit },
                            modifier = Modifier.fillMaxWidth(),
                            border = if (isSelected)
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            else
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(circuit.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(circuit.category, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                                circuit.exercises.forEach { ex ->
                                    Text("• $ex", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(circuit.instructions, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Button(
                    onClick = { if (selected != null) confirmed = true },
                    enabled = selected != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start Circuit")
                }
            } else {
                val circuit = selected!!
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Text("Complete this circuit:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(circuit.exercises) { ex ->
                        Text("• $ex", style = MaterialTheme.typography.bodyMedium)
                    }
                    item {
                        Text(circuit.instructions, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Button(
                    enabled = !saving,
                    onClick = {
                        if (saving) return@Button
                        saving = true
                        scope.launch {
                            try {
                                when (onComplete(circuit.id)) {
                                    RecoveryCircuitResult.RECORDED -> onDismiss()
                                    RecoveryCircuitResult.ALREADY_RECORDED -> saveMessage = "This week's recovery proof is already saved. No extra XP was added."
                                    RecoveryCircuitResult.INELIGIBLE -> saveMessage = "No XP added. Recovery proof is available once per current week, when you are exactly one credited session below your weekly goal."
                                }
                            } catch (error: Exception) {
                                if (error is kotlinx.coroutines.CancellationException) throw error
                                saveMessage = "Could not save recovery proof. Please try again."
                            } finally { saving = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                ) {
                    Text(if (saving) "Saving…" else "Complete & check proof eligibility")
                }
                saveMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

                TextButton(enabled = !saving, onClick = { confirmed = false; saveMessage = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose a different circuit")
                }
            }
        }
    }
}

