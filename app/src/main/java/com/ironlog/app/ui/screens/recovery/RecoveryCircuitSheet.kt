package com.ironlog.app.ui.screens.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    onComplete: (circuitId: String) -> Unit,
) {
    var selected: RecoveryCircuit? by remember { mutableStateOf(null) }
    var confirmed by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Recovery Circuit",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Complete one bodyweight circuit to protect this week's proof trail.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!confirmed) {
                CIRCUITS.forEach { circuit ->
                    val isSelected = selected?.id == circuit.id
                    OutlinedCard(
                        onClick = { selected = circuit },
                        modifier = Modifier.fillMaxWidth(),
                        border = if (isSelected)
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(circuit.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(circuit.category, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(4.dp))
                            circuit.exercises.forEach { ex ->
                                Text("- $ex", style = MaterialTheme.typography.bodySmall)
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
                Text("Complete this circuit:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                circuit.exercises.forEach { ex ->
                    Text("- $ex", style = MaterialTheme.typography.bodyMedium)
                }
                Text(circuit.instructions, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { onComplete(circuit.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                ) {
                    Text("Completed -- Save Proof (+25 XP)")
                }

                TextButton(onClick = { confirmed = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose a different circuit")
                }
            }
        }
    }
}

