package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.MyObjectBox
import com.ironlog.app.data.objectbox.PlanEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlanRepositoryOrderingTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `reordering plans never changes the explicitly active plan`() = runBlocking(Dispatchers.IO) {
        MyObjectBox.builder().directory(temporary.newFolder("plan-order")).build().use { store ->
            val box = store.boxFor(PlanEntity::class.java)
            val first = plan("first", active = false, updatedAt = 3)
            val active = plan("active", active = true, updatedAt = 2)
            val last = plan("last", active = false, updatedAt = 1)
            box.put(listOf(first, active, last))

            PlanRepository(store).reorderPlans(listOf(last.uid, first.uid, active.uid))

            val rows = box.all.associateBy { it.uid }
            assertEquals(false, rows.getValue(first.uid).isActive)
            assertEquals(true, rows.getValue(active.uid).isActive)
            assertEquals(false, rows.getValue(last.uid).isActive)
            assertEquals(1, rows.values.count { it.isActive })
        }
    }

    private fun plan(name: String, active: Boolean, updatedAt: Long) = PlanEntity().apply {
        this.name = name
        goal = "General Fitness"
        isActive = active
        createdAt = updatedAt
        this.updatedAt = updatedAt
    }
}
