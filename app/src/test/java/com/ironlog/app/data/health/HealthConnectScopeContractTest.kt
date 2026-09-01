package com.ironlog.app.data.health

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectScopeContractTest {
    private val projectRoot = File(requireNotNull(System.getProperty("user.dir")))

    @Test
    fun `manifest requests only the Health Connect data this build consumes`() {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(projectRoot.resolve("src/main/AndroidManifest.xml"))
        val permissions = document.getElementsByTagName("uses-permission")
        val names = (0 until permissions.length).map { node ->
            permissions.item(node).attributes
                .getNamedItemNS(ANDROID_NAMESPACE, "name")
                ?.nodeValue
                .orEmpty()
        }

        assertTrue("sleep read permission must remain declared", "android.permission.health.READ_SLEEP" in names)
        assertTrue("resting-HR read permission must remain declared", "android.permission.health.READ_RESTING_HEART_RATE" in names)
        assertTrue("HRV read permission must remain declared", "android.permission.health.READ_HEART_RATE_VARIABILITY" in names)
        assertFalse("workout export is not implemented", "android.permission.health.WRITE_EXERCISE" in names)
        assertFalse("weight export is not implemented", "android.permission.health.WRITE_WEIGHT" in names)
    }

    @Test
    fun `Home does not silently request or read health data it cannot use`() {
        val home = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/home/HomeScreen.kt",
        ).readText()

        assertFalse(home.contains("HealthConnectRepository"))
        assertFalse(home.contains("HealthConnectPermissionSheet"))
        assertFalse(home.contains("BiometricSnapshot"))
    }

    @Test
    fun `integration copy stays explicit about read only context`() {
        val screen = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/settings/HealthConnectScreen.kt",
        ).readText()
        val rationale = projectRoot.resolve(
            "src/main/java/com/ironlog/app/HealthConnectRationaleActivity.kt",
        ).readText()
        val strings = projectRoot.resolve("src/main/res/values/strings.xml").readText()
        val onboarding = projectRoot.resolve("src/main/res/values/strings_onboarding.xml").readText()

        assertTrue(screen.contains("Read-only health context"))
        assertTrue(screen.contains("do not change your readiness score"))
        assertTrue(rationale.contains("health_connect_rationale_body"))
        assertTrue(strings.contains("read-only context"))
        assertTrue(onboarding.contains("context only"))

        val allCopy = listOf(screen, rationale, strings, onboarding).joinToString("\n").lowercase()
        assertFalse(allCopy.contains("sync workouts"))
        assertFalse(allCopy.contains("body-weight sync"))
        assertFalse(allCopy.contains("better recovery scores"))
    }

    @Test
    fun `readiness engine contains no population HRV threshold scoring`() {
        val engine = projectRoot.resolve(
            "src/main/java/com/ironlog/app/domain/intelligence/RecoveryReadinessEngine.kt",
        ).readText()

        assertFalse(engine.contains("blendWithBiometric"))
        assertFalse(engine.contains("hrvScore"))
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
