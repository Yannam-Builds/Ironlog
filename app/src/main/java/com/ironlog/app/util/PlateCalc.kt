package com.ironlog.app.util

import com.ironlog.app.ui.screens.settings.PlateDto
import kotlin.math.round

data class PlateCalculationResult(
    val isValid: Boolean,
    val platesPerSide: List<PlateDto>,
    val totalWeightKg: Double
)

fun calculatePlates(
    targetWeightKg: Double,
    barWeightKg: Double,
    inventory: List<PlateDto>
): PlateCalculationResult {
    val perSide = (targetWeightKg - barWeightKg) / 2.0
    if (perSide <= 0) return PlateCalculationResult(false, emptyList(), targetWeightKg)
    
    // Sort inventory from heaviest to lightest
    val available = inventory.sortedByDescending { it.weightKg }
    val used = mutableListOf<PlateDto>()
    
    var remaining = round(perSide * 100.0) / 100.0
    
    for (plate in available) {
        var count = 0
        while (remaining >= plate.weightKg - 0.001 && count < plate.quantity) {
            count++
            remaining = round((remaining - plate.weightKg) * 100.0) / 100.0
        }
        if (count > 0) {
            used.add(PlateDto(plate.weightKg, count))
        }
    }
    
    val isValid = remaining <= 0.001
    return PlateCalculationResult(isValid, used, targetWeightKg)
}
