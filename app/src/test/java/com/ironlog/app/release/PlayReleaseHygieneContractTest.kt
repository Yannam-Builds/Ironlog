package com.ironlog.app.release

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class PlayReleaseHygieneContractTest {
    private val projectRoot = File(requireNotNull(System.getProperty("user.dir")))

    @Test
    fun `external progress-photo capture does not request camera permission`() {
        val document = manifestDocument()
        val permissions = document.androidAttributeValues("uses-permission", "name")
        val features = document.androidAttributeValues("uses-feature", "name")
        val progressPhotos = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/body/ProgressPhotosScreen.kt",
        ).readText()

        assertFalse("the external camera contract needs no app camera permission", "android.permission.CAMERA" in permissions)
        assertFalse("camera hardware must not affect Play device filtering", "android.hardware.camera" in features)
        assertTrue(progressPhotos.contains("ActivityResultContracts.TakePicture()"))
        assertFalse(progressPhotos.contains("Manifest.permission.CAMERA"))

        val permissionStep = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step7Permissions.kt",
        ).readText()
        val onboardingScreen = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/OnboardingScreen.kt",
        ).readText()
        val onboardingState = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/onboarding/OnboardingViewModel.kt",
        ).readText()
        val onboardingStrings = projectRoot.resolve("src/main/res/values/strings_onboarding.xml").readText()

        val onboardingSurface = listOf(
            permissionStep,
            onboardingScreen,
            onboardingState,
            onboardingStrings,
        ).joinToString("\n")
        assertFalse(onboardingSurface.contains("Manifest.permission.CAMERA"))
        assertFalse(onboardingSurface.contains("cameraGranted"))
        assertFalse(onboardingSurface.contains("onb_perm_camera"))
    }

    @Test
    fun `removed QR import has no exported deep-link alias`() {
        val document = manifestDocument()
        val aliases = document.getElementsByTagName("activity-alias")
        val qrAlias = (0 until aliases.length)
            .mapNotNull { aliases.item(it) as? Element }
            .firstOrNull { it.androidAttribute("name") == ".QrImportAlias" }
        val importDeepLink = document.getElementsByTagName("data")
            .let { nodes ->
                (0 until nodes.length)
                    .mapNotNull { nodes.item(it) as? Element }
                    .firstOrNull {
                        it.androidAttribute("scheme") == "ironlog" &&
                            it.androidAttribute("host") == "import"
                    }
            }

        assertNull("removed QR import must not leave an exported component", qrAlias)
        assertNull("removed QR import must not retain its custom deep link", importDeepLink)
    }

    @Test
    fun `privacy copy discloses cloud transmission and non-medical limits`() {
        val privacy = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/settings/PrivacyScreen.kt",
        ).readText().lowercase()
        val rationale = projectRoot.resolve("src/main/res/values/strings.xml").readText().lowercase()
        val rationaleActivity = projectRoot.resolve(
            "src/main/java/com/ironlog/app/HealthConnectRationaleActivity.kt",
        ).readText()

        assertTrue(privacy.contains("cloud ai"))
        assertTrue(privacy.contains("provider you configure"))
        assertTrue(privacy.contains("workout"))
        assertTrue(privacy.contains("bodyweight"))
        assertTrue(privacy.contains("readiness"))
        assertTrue(privacy.contains("api key"))
        assertTrue(privacy.contains("not medical advice"))
        assertTrue(rationale.contains("not medical advice"))
        assertTrue(rationale.contains("does not send health connect data to cloud ai"))
        assertFalse("do not expose a known-dead policy button", rationaleActivity.contains("https://ironlogpro.app/privacy"))
    }

    private fun manifestDocument() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(projectRoot.resolve("src/main/AndroidManifest.xml"))

    private fun org.w3c.dom.Document.androidAttributeValues(tag: String, attribute: String): List<String> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).mapNotNull { index ->
            (nodes.item(index) as? Element)?.androidAttribute(attribute)?.takeIf(String::isNotBlank)
        }
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
