package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsObservationTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `selected observed keys use same legacy decoding and omit unrelated private values`() = runBlocking {
        MyObjectBox.builder().directory(temporary.newFolder()).build().use { store ->
            val box = store.boxFor(AppSettingEntity::class.java)
            box.put(AppSettingEntity().apply { key = "checkin"; valueType = "json"; value = "\"legacy\"" })
            box.put(AppSettingEntity().apply { key = "private"; value = "not selected" })
            val actual = withTimeout(5000) { SettingsRepository(box).observeStrings(setOf("checkin", "missing")).first() }
            assertEquals(mapOf("checkin" to "legacy", "missing" to null), actual)
        }
    }
}
