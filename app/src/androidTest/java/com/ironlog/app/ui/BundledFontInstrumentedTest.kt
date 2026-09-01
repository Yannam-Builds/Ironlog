package com.ironlog.app.ui

import android.graphics.Paint
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironlog.app.ui.theme.TypographyFonts
import com.ironlog.app.ui.theme.TypographyPreset
import com.ironlog.app.ui.theme.TypographySelection
import com.ironlog.app.ui.theme.applyTypography
import com.ironlog.app.ui.theme.resourceId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Resource-only smoke test. Does not launch MainActivity, alter preferences, or touch workout data. */
@RunWith(AndroidJUnit4::class)
class BundledFontInstrumentedTest {
    @Test fun everyBundledFamilyLoadsAndMeasuresAtEveryPreset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(21, TypographyFonts.all.map { it.resourceId() }.toSet().size)
        for (font in TypographyFonts.all) {
            assertNotNull(font.name, context.resources.getFont(font.resourceId()))
            for (preset in TypographyPreset.entries) {
                val selection = TypographySelection(font.id, preset)
                for (sourceWeight in listOf(400, 500, 900)) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 32f
                        applyTypography(context, selection, sourceWeight)
                    }
                    val bounds = Rect()
                    paint.getTextBounds("Ag0123 kg", 0, 9, bounds)
                    assertTrue("${font.id}/${preset.id} bounds", bounds.width() > 0 && bounds.height() > 0)
                    assertTrue("${font.id}/${preset.id} measure", paint.measureText("Ag0123 kg").isFinite())
                    assertFalse("${font.id}/${preset.id} synthetic bold", paint.isFakeBoldText)
                    assertEquals("'wght' ${selection.weightFor(sourceWeight)}", paint.fontVariationSettings)
                }
            }
        }
    }
}
