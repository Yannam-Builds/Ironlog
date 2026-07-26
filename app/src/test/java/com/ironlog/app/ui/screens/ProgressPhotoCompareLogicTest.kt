package com.ironlog.app.ui.screens.body

import com.ironlog.app.data.objectbox.ProgressPhotoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProgressPhotoCompareLogicTest {

    @Test
    fun `selecting compare dates fills A then B then restarts on third distinct date`() {
        val first = updateDateCompareSelection(null, null, LocalDate.of(2026, 5, 10))
        val second = updateDateCompareSelection(first.first, first.second, LocalDate.of(2026, 5, 17))
        val third = updateDateCompareSelection(second.first, second.second, LocalDate.of(2026, 5, 24))

        assertEquals(LocalDate.of(2026, 5, 10), first.first)
        assertNull(first.second)
        assertEquals(LocalDate.of(2026, 5, 10), second.first)
        assertEquals(LocalDate.of(2026, 5, 17), second.second)
        assertEquals(LocalDate.of(2026, 5, 24), third.first)
        assertNull(third.second)
    }

    @Test
    fun `tapping selected B clears only B`() {
        val state = updateDateCompareSelection(
            selectedA = LocalDate.of(2026, 5, 10),
            selectedB = LocalDate.of(2026, 5, 17),
            tappedDate = LocalDate.of(2026, 5, 17),
        )

        assertEquals(LocalDate.of(2026, 5, 10), state.first)
        assertNull(state.second)
    }

    @Test
    fun `latest photo on selected compare date is chosen for side by side`() {
        val rows = listOf(
            photo("a1", LocalDate.of(2026, 5, 10), 9),
            photo("a2", LocalDate.of(2026, 5, 10), 18),
            photo("b1", LocalDate.of(2026, 5, 17), 7),
        )

        val pair = resolveDateComparePhotos(
            rows = rows,
            selectedA = LocalDate.of(2026, 5, 10),
            selectedB = LocalDate.of(2026, 5, 17),
        )

        assertEquals("a2", pair.first?.uid)
        assertEquals("b1", pair.second?.uid)
    }

    private fun photo(uid: String, date: LocalDate, hour: Int): ProgressPhotoEntity =
        ProgressPhotoEntity().apply {
            this.uid = uid
            this.fileUri = "content://$uid"
            this.takenAt = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
}
