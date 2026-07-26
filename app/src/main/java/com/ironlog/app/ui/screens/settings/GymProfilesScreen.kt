package com.ironlog.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.util.UUID

@Serializable
data class PlateDto(
    val weightKg: Double,
    val quantity: Int,
    val color: String = ""
)

@Serializable
data class GymProfileDto(
    val id: String,
    val name: String,
    val barWeightKg: Double,
    val plates: List<PlateDto> = emptyList(),
    val unavailableEquipment: List<String> = emptyList(),
)

val DEFAULT_PLATES = listOf(20.0, 15.0, 10.0, 5.0, 2.5, 1.25).map { PlateDto(it, 2) }

@Composable
fun GymProfilesScreen(
    onBack: () -> Unit = {},
    onCreate: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    repo: SettingsRepository = SettingsRepository(),
) {
    val c = useTheme()
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    var profiles by remember { mutableStateOf<List<GymProfileDto>>(emptyList()) }
    var activeId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<GymProfileDto?>(null) }

    suspend fun save(updated: List<GymProfileDto>) {
        repo.setString("gym_profiles_json", json.encodeToString(ListSerializer(GymProfileDto.serializer()), updated), "json")
        profiles = updated
    }

    suspend fun load() {
        val raw = repo.getString("gym_profiles_json").orEmpty()
        profiles = runCatching { if (raw.isBlank()) emptyList() else json.decodeFromString<List<GymProfileDto>>(raw) }.getOrDefault(emptyList())
        activeId = repo.getString("active_gym_profile_id")
    }

    LaunchedEffect(Unit) { load() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(title = "GYM PROFILES", onBack = onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCreate) { Text("Add Profile") }
                if (profiles.isEmpty()) {
                    Button(onClick = {
                        scope.launch {
                            val seed = listOf(
                                GymProfileDto(
                                    id = UUID.randomUUID().toString(),
                                    name = "Default Gym",
                                    barWeightKg = 20.0,
                                    plates = DEFAULT_PLATES
                                )
                            )
                            save(seed)
                            repo.setString("active_gym_profile_id", seed.first().id)
                            activeId = seed.first().id
                        }
                    }) { Text("Seed Default") }
                }
            }
        }
        if (profiles.isEmpty()) {
            item { Text("No gym profiles yet.", color = c.muted) }
        } else {
            items(profiles, key = { it.id }) { profile ->
                val isActive = profile.id == activeId
                val platesSummary = profile.plates.filter { it.quantity > 0 }
                    .sortedByDescending { it.weightKg }
                    .take(5)
                    .joinToString("  ") { "${it.weightKg}×${it.quantity}" }
                val unavailableSummary = if (profile.unavailableEquipment.isEmpty()) null
                    else "No: ${profile.unavailableEquipment.joinToString(", ")}"

                Card(
                    colors = CardDefaults.cardColors(containerColor = c.card),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isActive) c.accentBorder else c.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(profile.name, color = c.text, fontSize = IronLogType.section.fontSize.sp)
                                    if (isActive) {
                                        androidx.compose.foundation.layout.Box(
                                            Modifier
                                                .background(c.accentSoft, RoundedCornerShape(IronLogRadius.full.dp))
                                                .border(1.dp, c.accentBorder, RoundedCornerShape(IronLogRadius.full.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                        ) {
                                            Text("ACTIVE", color = c.accent, fontSize = 9.sp, letterSpacing = 1.sp)
                                        }
                                    }
                                }
                                Text("Bar ${profile.barWeightKg} kg", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                                if (platesSummary.isNotEmpty()) {
                                    Text("Plates: $platesSummary", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                                }
                                if (unavailableSummary != null) {
                                    Text(unavailableSummary, color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!isActive) {
                                Text(
                                    "Set Active",
                                    color = c.accent,
                                    fontSize = IronLogType.body.fontSize.sp,
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch {
                                                repo.setString("active_gym_profile_id", profile.id)
                                                activeId = profile.id
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                )
                            }
                            Text(
                                "Edit",
                                color = c.accent,
                                fontSize = IronLogType.body.fontSize.sp,
                                modifier = Modifier
                                    .clickable { onEdit(profile.id) }
                                    .padding(vertical = 10.dp),
                            )
                            Text(
                                "Duplicate",
                                color = c.accent,
                                fontSize = IronLogType.body.fontSize.sp,
                                modifier = Modifier
                                    .clickable {
                                        scope.launch {
                                            val copy = profile.copy(
                                                id = UUID.randomUUID().toString(),
                                                name = "${profile.name} (Copy)",
                                            )
                                            save(profiles + copy)
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                            )
                            Text(
                                "Delete",
                                color = c.danger,
                                fontSize = IronLogType.body.fontSize.sp,
                                modifier = Modifier
                                    .clickable { deleteTarget = profile }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = c.card,
            title = { Text("Delete Profile?", color = c.text) },
            text = { Text("\"${target.name}\" will be permanently deleted.", color = c.subtext) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val updated = profiles.filter { it.id != target.id }
                            save(updated)
                            if (activeId == target.id) {
                                val newActive = updated.firstOrNull()?.id
                                if (newActive != null) repo.setString("active_gym_profile_id", newActive)
                                else repo.removeSetting("active_gym_profile_id")
                                activeId = newActive
                            }
                            deleteTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.danger),
                ) { Text("DELETE", color = c.textOnAccent) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("CANCEL", color = c.muted) }
            },
        )
    }
}



