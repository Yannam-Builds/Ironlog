package com.ironlog.app.ui.state

import com.ironlog.app.util.convertUnitToKg

/** The overloaded first field contains distance for cardio, not an external load. */
internal fun canonicalSetLoad(display: Double, tracking: String, unit: String): Double =
    if (tracking.trim().lowercase() in setOf("duration_distance", "cardio", "duration")) display
    else convertUnitToKg(display, if (unit.equals("lb", true)) "lbs" else unit)

internal fun canonicalSetType(type: String): String = type.trim().lowercase().let {
    if (it == "dropset") "drop" else it
}
