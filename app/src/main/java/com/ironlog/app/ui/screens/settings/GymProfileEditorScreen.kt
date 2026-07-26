package com.ironlog.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.data.repository.SettingsRepository
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.util.convertKgToUnit
import com.ironlog.app.util.convertUnitToKg
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.abs

val PRESET_PLATE_COLORS = listOf(
    "#D32F2F" to "Red",
    "#1565C0" to "Blue",
    "#F9A825" to "Yellow",
    "#2E7D32" to "Green",
    "#F5F5F5" to "White",
    "#212121" to "Black",
)

private fun plateColorFromHex(hex: String): Color {
    if (hex.isBlank()) return Color(0xFFBDBDBD)
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color(0xFFBDBDBD) }
}

@Composable
fun GymProfileEditorScreen(
    profileId: String = "",
    onSaved: () -> Unit = {},
    onBack: () -> Unit = {},
    repo: SettingsRepository = SettingsRepository(),
) {
    val c = useTheme()
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }

    var unit by remember { mutableStateOf("kg") }
    val unitLabel = if (unit == "lbs") "lb" else "kg"
    var haptic by remember { mutableStateOf(true) }

    var name by remember { mutableStateOf("") }
    var barWeight by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    var plates by remember { mutableStateOf<List<PlateDto>>(DEFAULT_PLATES) }
    var newPlate by remember { mutableStateOf("") }
    var unavailableEquipment by remember { mutableStateOf<Set<String>>(emptySet()) }
    var colorPickerTarget by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(profileId) {
        // Read haptic + unit from the ironlog_settings JSON blob (canonical location).
        val settingsJson = runCatching {
            repo.getString("ironlog_settings")?.let { org.json.JSONObject(it) }
        }.getOrNull()
        haptic = settingsJson?.optBoolean("hapticFeedback", true) ?: true
        unit = settingsJson?.optString("weightUnit", "kg")?.takeIf { it.isNotBlank() } ?: "kg"
        val raw = repo.getString("gym_profiles_json").orEmpty()
        val existing = runCatching { if (raw.isBlank()) emptyList() else json.decodeFromString<List<GymProfileDto>>(raw) }.getOrDefault(emptyList())
        val hit = existing.firstOrNull { it.id == profileId }
        if (hit != null) {
            name = hit.name
            barWeight = convertKgToUnit(hit.barWeightKg, unit, 2).toString()
            if (hit.plates.isNotEmpty()) plates = hit.plates
            unavailableEquipment = hit.unavailableEquipment.toSet()
        } else {
            barWeight = if (unit == "lbs") "45" else "20"
        }
    }

    val adjustQty: (Double, Int) -> Unit = { w, delta ->
        plates = plates.map { if (it.weightKg == w) it.copy(quantity = (it.quantity + delta).coerceAtLeast(1)) else it }
    }

    val removePlate: (Double) -> Unit = { w ->
        plates = plates.filter { it.weightKg != w }
    }

    fun addPlate() {
        val typedWeight = newPlate.toDoubleOrNull()
        if (typedWeight == null || typedWeight <= 0) {
            status = "Enter a positive plate weight ($unitLabel)."
            return
        }
        val weightKg = convertUnitToKg(typedWeight, unit)
        val exists = plates.any { abs(it.weightKg - weightKg) < 0.001 }
        if (exists) {
            status = "That plate weight is already in the list."
            return
        }
        plates = (plates + PlateDto(weightKg, 2)).sortedByDescending { it.weightKg }
        newPlate = ""
        status = ""
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenHeader(title = if (profileId.isBlank()) "NEW GYM PROFILE" else "EDIT GYM PROFILE", onBack = onBack) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = BorderStroke(1.dp, c.cardBorder),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PROFILE NAME", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 3.sp)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 60) name = it },
                        placeholder = { Text("e.g. Home Gym, Planet Fitness") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = BorderStroke(1.dp, c.cardBorder),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("BAR WEIGHT ($unitLabel)", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 3.sp)
                    OutlinedTextField(
                        value = barWeight,
                        onValueChange = { barWeight = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = BorderStroke(1.dp, c.cardBorder),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AVAILABLE PLATES", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = 3.sp)
                    Text("Quantity = pairs available per side", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, modifier = Modifier.padding(bottom = 8.dp))
                    
                    plates.forEach { plate ->
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${convertKgToUnit(plate.weightKg, unit, 2)}$unitLabel",
                                    color = c.text,
                                    fontSize = IronLogType.section.fontSize.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        Modifier.size(44.dp).clickable { colorPickerTarget = if (colorPickerTarget == plate.weightKg) null else plate.weightKg },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(24.dp)
                                                .background(plateColorFromHex(plate.color), CircleShape)
                                                .border(1.dp, c.cardBorder, CircleShape)
                                        )
                                    }
                                    IconButton(onClick = { adjustQty(plate.weightKg, -1) }) { Icon(Icons.Filled.Remove, null, tint = c.muted) }
                                    Text("${plate.quantity}", color = c.text, fontSize = IronLogType.section.fontSize.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                    IconButton(onClick = { adjustQty(plate.weightKg, 1) }) { Icon(Icons.Filled.Add, null, tint = c.muted) }
                                    IconButton(onClick = { removePlate(plate.weightKg) }) { Icon(Icons.Filled.Close, null, tint = c.danger) }
                                }
                            }
                            if (colorPickerTarget == plate.weightKg) {
                                Row(
                                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PRESET_PLATE_COLORS.forEach { (hexColor, colorName) ->
                                        Box(
                                            Modifier.size(44.dp).clickable {
                                                plates = plates.map { if (it.weightKg == plate.weightKg) it.copy(color = hexColor) else it }
                                                colorPickerTarget = null
                                            },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(28.dp)
                                                    .background(plateColorFromHex(hexColor), CircleShape)
                                                    .border(
                                                        if (plate.color == hexColor) 2.dp else 1.dp,
                                                        if (plate.color == hexColor) c.accent else c.cardBorder,
                                                        CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newPlate,
                            onValueChange = { newPlate = it },
                            placeholder = { Text("Add plate weight ($unitLabel)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { addPlate() }, colors = ButtonDefaults.buttonColors(containerColor = c.accentSoft, contentColor = c.accent)) {
                            Icon(Icons.Filled.Add, contentDescription = "Add plate")
                        }
                    }
                }
            }
        }

        // ── Unavailable Equipment ─────────────────────────────────────────────
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = BorderStroke(1.dp, c.cardBorder),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("UNAVAILABLE EQUIPMENT", color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, letterSpacing = IronLogType.eyebrow.letterSpacing.sp)
                    Text("Check equipment types not available at this gym. These will be filtered from your exercise library when this profile is active.", color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                    val equipmentOptions = listOf("Barbell", "Dumbbell", "Cable", "Machine", "Kettlebell", "Band")
                    equipmentOptions.forEach { equip ->
                        val checked = unavailableEquipment.contains(equip)
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                unavailableEquipment = if (checked) unavailableEquipment - equip else unavailableEquipment + equip
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                unavailableEquipment = if (checked) unavailableEquipment - equip else unavailableEquipment + equip
                            })
                            Text(equip, color = c.text, fontSize = IronLogType.body.fontSize.sp)
                            if (checked) Text("(unavailable)", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    scope.launch {
                        val bwParsed = barWeight.toDoubleOrNull()
                        if (name.isBlank() || bwParsed == null || bwParsed <= 0) {
                            status = "Enter valid name and bar weight."
                            return@launch
                        }
                        val bwKg = convertUnitToKg(bwParsed, unit)
                        val raw = runCatching { repo.getString("gym_profiles_json").orEmpty() }.getOrDefault("")
                        val all = runCatching { if (raw.isBlank()) emptyList() else json.decodeFromString<List<GymProfileDto>>(raw) }.getOrDefault(emptyList()).toMutableList()
                        val id = if (profileId.isBlank()) UUID.randomUUID().toString() else profileId
                        val updated = GymProfileDto(id = id, name = name.trim(), barWeightKg = bwKg, plates = plates, unavailableEquipment = unavailableEquipment.toList())
                        val idx = all.indexOfFirst { it.id == id }
                        if (idx >= 0) all[idx] = updated else all.add(updated)
                        repo.setString("gym_profiles_json", json.encodeToString(ListSerializer(GymProfileDto.serializer()), all), "json")
                        if (repo.getString("active_gym_profile_id").isNullOrBlank()) repo.setString("active_gym_profile_id", id)
                        status = "Profile saved."
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent)
            ) {
                Text("SAVE PROFILE", color = c.bg, fontWeight = FontWeight.Black, letterSpacing = 3.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (status.isNotBlank()) Text(status, color = c.danger, fontSize = IronLogType.meta.fontSize.sp)
        }
        
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

