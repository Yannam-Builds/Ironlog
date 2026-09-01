package com.ironlog.app.ui.theme

import androidx.annotation.FontRes
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ironlog.app.R

@FontRes
fun TypographyFont.resourceId(): Int = when (id) {
    "inter" -> R.font.inter_variable
    "manrope" -> R.font.manrope_variable
    "dmsans" -> R.font.dmsans_variable
    "plusjakartasans" -> R.font.plusjakartasans_variable
    "outfit" -> R.font.outfit_variable
    "sora" -> R.font.sora_variable
    "urbanist" -> R.font.urbanist_variable
    "nunito" -> R.font.nunito_variable
    "nunitosans" -> R.font.nunitosans_variable
    "rubik" -> R.font.rubik_variable
    "worksans" -> R.font.worksans_variable
    "publicsans" -> R.font.publicsans_variable
    "figtree" -> R.font.figtree_variable
    "assistant" -> R.font.assistant_variable
    "mulish" -> R.font.mulish_variable
    "quicksand" -> R.font.quicksand_variable
    "raleway" -> R.font.raleway_variable
    "montserrat" -> R.font.montserrat_variable
    "exo2" -> R.font.exo2_variable
    "sourcesans3" -> R.font.sourcesans3_variable
    else -> R.font.lexend_variable
}

// Every requested preset weight has a real wght-axis instance, including 350/450/650/750.
@OptIn(ExperimentalTextApi::class)
private val families: Map<String, FontFamily> by lazy {
    TypographyFonts.all.associate { font ->
        val weights = ((100..1000 step 100).toList() + listOf(350, 450, 650, 750))
            .map { it.coerceIn(font.minWeight, font.maxWeight) }.distinct()
        font.id to FontFamily(weights.map { weight ->
            Font(font.resourceId(), weight = FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
        })
    }
}

fun TypographySelection.fontFamily(): FontFamily = families.getValue(font.id)
