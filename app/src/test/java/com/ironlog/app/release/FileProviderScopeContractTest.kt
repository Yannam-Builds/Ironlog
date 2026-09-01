package com.ironlog.app.release

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileProviderScopeContractTest {
    private val projectRoot = File(requireNotNull(System.getProperty("user.dir")))
    private val sourceRoot = projectRoot.resolve("src/main/java")

    @Test
    fun `provider exposes only the directories used for explicit sharing`() {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(projectRoot.resolve("src/main/res/xml/file_paths.xml"))
        val entries = buildSet {
            val children = document.documentElement.childNodes
            for (index in 0 until children.length) {
                val element = children.item(index)
                if (element.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
                val name = element.attributes.getNamedItem("name").nodeValue
                val path = element.attributes.getNamedItem("path").nodeValue
                assertFalse("FileProvider must not expose a storage root", path.isBlank() || path == "." || path == "/")
                add("${element.nodeName}:$name:$path")
            }
        }

        assertEquals(
            setOf(
                "files-path:progress_photos:progress_photos/",
                "files-path:exports:exports/",
                "files-path:plan_exports:plan_exports/",
                "cache-path:cache_images:images/",
                "cache-path:cache_exports:exports/",
            ),
            entries,
        )
    }

    @Test
    fun `every FileProvider caller stages files inside an exposed purpose directory`() {
        val expectedCallers = mapOf(
            "com/ironlog/app/services/ShareService.kt" to listOf("File(context.cacheDir, \"images\")"),
            "com/ironlog/app/ui/screens/body/ProgressPhotosScreen.kt" to listOf(
                "File(context.filesDir, \"progress_photos\")",
                "File(context.cacheDir, \"exports\")",
            ),
            "com/ironlog/app/ui/screens/plans/AIPlanScreen.kt" to listOf("File(context.cacheDir, \"exports\")"),
            "com/ironlog/app/ui/screens/plans/PlansScreen.kt" to listOf("java.io.File(context.filesDir, \"plan_exports\")"),
            "com/ironlog/app/ui/screens/settings/DataPortabilityScreen.kt" to listOf("File(context.filesDir, \"exports\")"),
            "com/ironlog/app/ui/screens/settings/SettingsScreen.kt" to listOf("File(context.cacheDir, \"exports\")"),
            "com/ironlog/app/ui/screens/stats/ExerciseProgressScreen.kt" to listOf("File(context.cacheDir, \"exports\")"),
        )
        val actualCallers = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .filter { it.readText().contains("FileProvider.getUriForFile") }
            .associateBy { it.relativeTo(sourceRoot).invariantSeparatorsPath }

        assertEquals("A FileProvider caller was added or removed without auditing its path", expectedCallers.keys, actualCallers.keys)
        expectedCallers.forEach { (relativePath, requiredScopes) ->
            val source = requireNotNull(actualCallers[relativePath]).readText()
            requiredScopes.forEach { scope ->
                assertTrue("$relativePath must stage its shared file under $scope", source.contains(scope))
            }
        }
    }
}
