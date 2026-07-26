package com.ironlog.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ironlog.app.R

@Composable
fun IronGradeBadge(
    rank: String,
    modifier: Modifier = Modifier,
    accent: Color = ironGradeColor(rank),
) {
    Image(
        painter = painterResource(ironGradeDrawable(rank)),
        contentDescription = "$rank Iron Ledger badge",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@DrawableRes
private fun ironGradeDrawable(rank: String): Int = when (rank) {
    "Uncalibrated", "E" -> R.drawable.iron_grade_uncalibrated
    "Graphite", "D" -> R.drawable.iron_grade_graphite
    "Iron", "C" -> R.drawable.iron_grade_iron
    "Steel", "B" -> R.drawable.iron_grade_steel
    "Titanium", "A" -> R.drawable.iron_grade_titanium
    "Obsidian", "S" -> R.drawable.iron_grade_obsidian
    "Iridium" -> R.drawable.iron_grade_iridium
    "Aether" -> R.drawable.iron_grade_aether
    "Apex" -> R.drawable.iron_grade_apex
    else -> R.drawable.iron_grade_uncalibrated
}

fun ironGradeColor(rank: String): Color = when (rank) {
    "Uncalibrated", "E" -> Color(0xFF9E9E9E)
    "Graphite", "D" -> Color(0xFF8D8D8D)
    "Iron", "C" -> Color(0xFFB0BEC5)
    "Steel", "B" -> Color(0xFF90A4AE)
    "Titanium", "A" -> Color(0xFF64B5F6)
    "Obsidian", "S" -> Color(0xFFB39DDB)
    "Iridium" -> Color(0xFFFFB74D)
    "Aether" -> Color(0xFF80CBC4)
    "Apex" -> Color(0xFFFFD700)
    else -> Color(0xFF9E9E9E)
}
