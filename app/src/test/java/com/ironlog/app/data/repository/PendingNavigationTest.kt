package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PendingNavigationTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun staleConsumerCannotClearNewerRouteAndCurrentRouteIsConsumedOnce() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            try {
                val repo = SettingsRepository(store.boxFor(AppSettingEntity::class.java))
                repo.setString("pending_nav_route", "Stats")
                assertFalse(repo.consumeString("pending_nav_route", "Home"))
                assertEquals("Stats", repo.getString("pending_nav_route"))
                assertTrue(repo.consumeString("pending_nav_route", "Stats"))
                assertFalse(repo.consumeString("pending_nav_route", "Stats"))
                assertNull(repo.getString("pending_nav_route"))
            } finally { store.closeThreadResources() }
        }
    }
}
