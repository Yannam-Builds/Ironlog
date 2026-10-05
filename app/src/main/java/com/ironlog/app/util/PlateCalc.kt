package com.ironlog.app.util

import com.ironlog.app.ui.screens.settings.PlateDto
import kotlin.math.round

data class PlateCalculationResult(
    val isValid: Boolean,
    val platesPerSide: List<PlateDto>,
    val totalWeightKg: Double,
    val achievedWeightKg: Double,
    val remainderKg: Double,
)

fun calculatePlates(
    targetWeightKg: Double,
    barWeightKg: Double,
    inventory: List<PlateDto>
): PlateCalculationResult {
    fun invalid() = PlateCalculationResult(false, emptyList(), targetWeightKg,
        barWeightKg.takeIf { it.isFinite() } ?: 0.0, 0.0)
    if (!targetWeightKg.isFinite() || !barWeightKg.isFinite() || barWeightKg < 0 ||
        inventory.any { !it.weightKg.isFinite() || it.weightKg <= 0 || it.quantity < 0 }) return invalid()
    val perSide = (targetWeightKg - barWeightKg) / 2.0
    if (perSide < 0) return PlateCalculationResult(false, emptyList(), targetWeightKg, barWeightKg, targetWeightKg - barWeightKg)
    if (perSide == 0.0) return PlateCalculationResult(true, emptyList(), targetWeightKg, barWeightKg, 0.0)
    // Micro-kg keys preserve converted pound stock. Actual canonical weights also
    // bound candidates within the existing one-gram total loading tolerance.
    val scale = 1_000_000.0
    val ceiling = perSide + 0.0005
    if (ceiling * scale >= Long.MAX_VALUE.toDouble()) return invalid()
    val target = round(ceiling * scale).toLong()
    data class Choice(val count: Long, val actual: Double, val plate: PlateDto?, val quantity: Int, val previous: Choice?)
    val reachable = mutableMapOf(0L to Choice(0, 0.0, null, 0, null))
    val available = inventory.filter { it.quantity > 0 }.groupBy { round(it.weightKg * scale).toLong() }
    for ((weight, rows) in available.toSortedMap(compareByDescending { it })) {
        if (weight <= 0 || weight > target) continue
        var remaining = minOf(rows.sumOf { it.quantity.toLong() }, target / weight, Int.MAX_VALUE.toLong()).toInt()
        var bundle = 1
        while (remaining > 0) {
            val quantity = minOf(bundle, remaining)
            val cost = weight * quantity
            // Snapshot each bundle: a physical plate can only be spent once.
            for ((sum, previous) in reachable.toList()) {
                if (sum > target - cost) continue
                val next = sum + cost
                val actual = previous.actual + rows.first().weightKg * quantity
                if (actual <= ceiling && (reachable[next]?.count ?: Long.MAX_VALUE) > previous.count + quantity)
                    reachable[next] = Choice(previous.count + quantity, actual,
                        rows.first(), quantity, previous)
            }
            remaining -= quantity
            bundle = if (bundle > Int.MAX_VALUE / 2) Int.MAX_VALUE else bundle * 2
        }
    }
    val best = reachable.keys.maxOrNull() ?: 0L
    val used = mutableMapOf<Double, PlateDto>()
    var choice = reachable.getValue(best)
    while (choice.plate != null) {
        val plate = choice.plate!!
        used[plate.weightKg] = plate.copy(quantity = (used[plate.weightKg]?.quantity ?: 0) + choice.quantity)
        choice = choice.previous!!
    }
    val achieved = barWeightKg + used.values.sumOf { it.weightKg * it.quantity * 2 }
    val remainder = round((targetWeightKg - achieved) * 100) / 100.0
    return PlateCalculationResult(kotlin.math.abs(targetWeightKg - achieved) <= 0.001,
        used.values.sortedByDescending { it.weightKg }, targetWeightKg, achieved, remainder)
}
