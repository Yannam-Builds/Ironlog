package com.ironlog.app.domain.intelligence

import com.ironlog.app.ui.screens.settings.PlateDto
import com.ironlog.app.util.calculatePlates

/** Conservative availability check using the same inventory contract as the plate sheet. */
fun progressionBarbellStep(baselineKg: Double, barKg: Double, inventory: List<PlateDto>): Double {
    if (!baselineKg.isFinite() || !barKg.isFinite() || barKg <= 0 || baselineKg < barKg) return 0.0
    val plates = inventory.filter { it.weightKg.isFinite() && it.weightKg > 0 && it.quantity in 1..100 }
    val step = plates.minOfOrNull { it.weightKg * 2.0 } ?: return 0.0
    if (!calculatePlates(baselineKg, barKg, plates).isValid || !calculatePlates(baselineKg + step, barKg, plates).isValid) return 0.0
    return step
}
