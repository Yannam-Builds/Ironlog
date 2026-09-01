package com.ironlog.app.services

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class NotificationEntrySecurityContractTest {
    private val projectRoot = File(requireNotNull(System.getProperty("user.dir")))

    @Test
    fun `launcher stays exported while notification entry and shade receiver stay private`() {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(projectRoot.resolve("src/main/AndroidManifest.xml"))

        val mainActivity = document.component("activity", ".MainActivity")
        val notificationEntry = document.component(
            "activity",
            ".services.NotificationEntryActivity",
        )
        val restReceiver = document.component(
            "receiver",
            ".services.WorkoutNotificationActionReceiver",
        )

        assertNotNull(mainActivity)
        assertEquals("true", mainActivity?.androidAttribute("exported"))
        assertNotNull(notificationEntry)
        assertEquals("false", notificationEntry?.androidAttribute("exported"))
        assertNotNull(restReceiver)
        assertEquals("false", restReceiver?.androidAttribute("exported"))
    }

    @Test
    fun `exported main activity never consumes privileged notification extras`() {
        val activity = projectRoot.resolve(
            "src/main/java/com/ironlog/app/MainActivity.kt",
        ).readText()

        assertFalse(activity.contains("getStringExtra(\"actionId\")"))
        assertFalse(activity.contains("getStringExtra(\"workout_id\")"))
        assertFalse(activity.contains("getBooleanExtra(\"navigate_to_workout\""))
        assertFalse(activity.contains("WorkoutNotificationActionInbox.commitNotificationTap"))
        assertFalse(activity.contains("NotificationEntryActivity.EXTRA_WORKOUT_ID"))
        assertFalse(activity.contains("NotificationEntryActivity.EXTRA_ACTION_ID"))
    }

    @Test
    fun `private entry commits notification work then opens main without forwarding extras`() {
        val entry = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/NotificationEntryActivity.kt",
        ).readText()

        val persist = entry.indexOf("persistNotificationRequest(intent)")
        val launch = entry.indexOf("startActivity(Intent(this, MainActivity::class.java)")
        assertTrue(persist >= 0)
        assertTrue(launch > persist)
        assertTrue(entry.contains("WorkoutNotificationActionInbox.commitNotificationTap"))
        assertTrue(entry.contains("NotificationActionRouter.Actions.FINISH_WORKOUT"))
        assertFalse(entry.substring(launch).contains("putExtra("))
        assertFalse(entry.substring(launch).contains("putExtras("))
    }

    @Test
    fun `all notification activity intents target the private entry and remain immutable`() {
        val foregroundService = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/WorkoutForegroundService.kt",
        ).readText()
        val notifications = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/NotificationServices.kt",
        ).readText()

        assertTrue(foregroundService.contains("Intent(this, NotificationEntryActivity::class.java)"))
        assertFalse(foregroundService.contains("Intent(this, MainActivity::class.java)"))
        assertTrue(notifications.contains("Intent(context, NotificationEntryActivity::class.java)"))
        assertFalse(notifications.contains("Intent(context, MainActivity::class.java)"))
        assertTrue(foregroundService.contains("PendingIntent.FLAG_IMMUTABLE"))
        assertTrue(notifications.contains("PendingIntent.FLAG_IMMUTABLE"))
    }

    private fun org.w3c.dom.Document.component(tag: String, name: String): Element? {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length)
            .mapNotNull { nodes.item(it) as? Element }
            .firstOrNull { it.androidAttribute("name") == name }
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
