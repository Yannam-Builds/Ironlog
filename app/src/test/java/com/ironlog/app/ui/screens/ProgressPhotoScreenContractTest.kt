package com.ironlog.app.ui.screens.body

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProgressPhotoScreenContractTest {
    private val source get() = File("src/main/java/com/ironlog/app/ui/screens/body/ProgressPhotosScreen.kt").readText()

    @Test fun `photo viewer uses dialog boundary with safe area keyboard and explicit dismissal`() {
        val text = source
        assertTrue("Fullscreen viewer must have a real modal window", text.contains("Dialog("))
        assertTrue(text.contains("usePlatformDefaultWidth = false"))
        assertTrue(text.contains("dismissOnBackPress = true"))
        assertTrue(text.contains("safeDrawingPadding()"))
        assertTrue(text.contains("imePadding()"))
        assertTrue(text.contains("photoViewerDismissal("))
    }

    @Test fun `view opens an exclusive single state and both delete actions use same cleanup`() {
        val text = source
        assertTrue(text.contains("ProgressPhotoViewerState.Single(row.uid)"))
        assertTrue(text.contains("deletePhotos(listOf(row))"))
        assertTrue(text.contains("deletePhotos(rows)"))
        assertFalse(text.contains("photoBox.removeAll()"))
        assertFalse(text.contains("java.io.File(uri.path!!).delete()"))
    }

    @Test fun `constrained viewer keeps a usable photo slot and scroll recovery for controls`() {
        val viewer = source.substringAfter("internal fun ProgressPhotoViewerDialog(").substringBefore("private fun PhotoCalendar(")
        assertTrue("Viewer controls need scroll recovery when keyboard or large text exhausts height", viewer.contains("verticalScroll("))
        assertTrue("Photo must retain a usable minimum height", viewer.contains("heightIn(min = 160.dp)"))
    }
}
