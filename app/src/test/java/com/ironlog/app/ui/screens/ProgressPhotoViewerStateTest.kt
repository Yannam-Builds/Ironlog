package com.ironlog.app.ui.screens.body

import com.ironlog.app.data.objectbox.ProgressPhotoEntity
import org.junit.Assert.*
import org.junit.Test

class ProgressPhotoViewerStateTest {
    @Test fun `view C is single C even after comparing A and B`() {
        val rows = listOf(photo("A"), photo("B"), photo("C"))
        var state: ProgressPhotoViewerState = ProgressPhotoViewerState.Compare("A", "B")
        assertEquals(listOf("A", "B"), resolveProgressPhotoViewer(state, rows).map { it.uid })
        state = ProgressPhotoViewerState.Single("C")
        assertEquals(listOf("C"), resolveProgressPhotoViewer(state, rows).map { it.uid })
    }

    @Test fun `viewer references remain stable when dates reorder list`() {
        val state = ProgressPhotoViewerState.Compare("A", "B")
        assertEquals(listOf("A", "B"), resolveProgressPhotoViewer(state, listOf(photo("B"), photo("C"), photo("A"))).map { it.uid })
    }

    @Test fun `deleting single or either comparison photo invalidates viewer`() {
        val rows = listOf(photo("C"))
        assertTrue(resolveProgressPhotoViewer(ProgressPhotoViewerState.Single("A"), rows).isEmpty())
        assertTrue(resolveProgressPhotoViewer(ProgressPhotoViewerState.Compare("A", "C"), rows).isEmpty())
    }

    @Test fun `back confirms dirty notes and cannot interrupt pending save`() {
        assertEquals(PhotoViewerDismissal.CLOSE, photoViewerDismissal("same", "same", false))
        assertEquals(PhotoViewerDismissal.CONFIRM_DISCARD, photoViewerDismissal("old", "draft", false))
        assertEquals(PhotoViewerDismissal.WAIT_FOR_SAVE, photoViewerDismissal("old", "draft", true))
    }

    private fun photo(id: String) = ProgressPhotoEntity().apply { uid = id }
}
