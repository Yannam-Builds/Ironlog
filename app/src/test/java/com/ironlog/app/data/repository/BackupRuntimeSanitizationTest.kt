package com.ironlog.app.data.repository

import com.ironlog.app.data.objectbox.AppSettingEntity
import com.ironlog.app.data.objectbox.MyObjectBox
import com.ironlog.app.data.objectbox.WorkoutEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupRuntimeSanitizationTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `export contains durable preferences and finalized workouts but no live runtime state`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder("export-db")).build()
        try {
            store.boxFor(WorkoutEntity::class.java).put(listOf(
                WorkoutEntity().apply { uid = "done"; status = "completed" },
                WorkoutEntity().apply { uid = "live"; status = "active" },
            ))
            store.boxFor(AppSettingEntity::class.java).put(listOf(
                setting("notifications_enabled", "true"),
                setting("active_workout_id", "live"),
                setting("active_workout_rest_end_ms", "999999"),
                setting("pending_workout_action_queue_v1", "[{\"actionId\":\"ironlog.finish_workout\"}]"),
                setting("notification_schedule_generation", "44"),
            ))

            val data = ImportExportRepository(store, temporary.newFolder("export-recovery"))
                .exportDatabase().getJSONObject("data")
            val workoutIds = data.getJSONArray("workouts").objectStrings("id")
            val settingKeys = data.getJSONArray("app_settings").objectStrings("key")

            assertEquals(setOf("done"), workoutIds)
            assertEquals(setOf("notifications_enabled"), settingKeys)
        } finally {
            store.close()
        }
    }

    @Test
    fun `restore ignores legacy live session timers actions and schedule metadata`() = runBlocking {
        val store = MyObjectBox.builder().directory(temporary.newFolder("import-db")).build()
        try {
            val payload = """{
                "type":"ironlog_watermelon_export",
                "version":1,
                "data":{
                    "workouts":[{"id":"live","name":"Old session","status":"active","started_at":1}],
                    "app_settings":[
                        {"id":"durable","key":"notifications_enabled","value":"true","value_type":"boolean"},
                        {"id":"active","key":"active_workout_id","value":"live","value_type":"string"},
                        {"id":"timer","key":"active_workout_rest_end_ms","value":"999999","value_type":"number"},
                        {"id":"queue","key":"pending_workout_action_queue_v1","value":"[]","value_type":"json"},
                        {"id":"schedule","key":"notification_schedule_generation","value":"44","value_type":"number"}
                    ]
                }
            }"""

            val result = ImportExportRepository(store, temporary.newFolder("import-recovery"))
                .runConfirmedImport(payload)

            assertTrue(result.unsupportedRows >= 4)
            assertTrue(store.boxFor(WorkoutEntity::class.java).all.isEmpty())
            val settings = store.boxFor(AppSettingEntity::class.java).all.associate { it.key to it.value }
            assertEquals("true", settings["notifications_enabled"])
            assertFalse(settings.keys.any(::isTransientBackupRuntimeSetting))
        } finally {
            store.close()
        }
    }

    private fun setting(key: String, value: String) = AppSettingEntity().apply {
        this.key = key
        this.value = value
        valueType = "string"
    }

    private fun org.json.JSONArray.objectStrings(key: String): Set<String> =
        (0 until length()).mapNotNull { optJSONObject(it)?.optString(key) }.toSet()
}
