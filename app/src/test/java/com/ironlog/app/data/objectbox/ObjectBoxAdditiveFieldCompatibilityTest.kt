package com.ironlog.app.data.objectbox

import org.junit.Assert.assertNull
import org.junit.Test

class ObjectBoxAdditiveFieldCompatibilityTest {

    @Test
    fun `legacy gamification row may hydrate new badge json as null`() {
        val constructor = GamificationProfileEntity::class.java.declaredConstructors
            .single { it.parameterCount == 15 }
        val row = constructor.newInstance(
            1L,
            "local",
            0L,
            1,
            0L,
            "E",
            "Ledger Initiate",
            "{}",
            0,
            "",
            0,
            "{}",
            "",
            null,
            0L,
        ) as GamificationProfileEntity

        assertNull(row.badgeUnlocksJson)
    }

    @Test
    fun `legacy workout set may hydrate new note as null`() {
        val row = WorkoutSetEntity()

        WorkoutSetEntity::class.java.getMethod("setNotes", String::class.java).invoke(row, null)

        assertNull(row.notes)
    }
}
