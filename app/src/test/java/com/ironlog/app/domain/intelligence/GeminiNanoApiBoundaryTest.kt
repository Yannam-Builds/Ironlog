package com.ironlog.app.domain.intelligence

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiNanoApiBoundaryTest {
    private val projectRoot = File(requireNotNull(System.getProperty("user.dir")))

    @Test
    fun `AICore symbols are isolated to the API 31 runtime implementation`() {
        val sourceRoot = projectRoot.resolve("src/main/java")
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != AICORE_RUNTIME_SOURCE }
            .filter { it.readText().contains(AICORE_PACKAGE) }
            .map { it.relativeTo(projectRoot).invariantSeparatorsPath }
            .toList()

        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `public facade bytecode does not link AICore classes`() {
        val facadeBytes = classBytes("com/ironlog/app/domain/intelligence/GeminiNanoEngine.class")
        val constantPoolText = String(facadeBytes, Charsets.ISO_8859_1)

        assertFalse(constantPoolText.contains(AICORE_PACKAGE.replace('.', '/')))
        assertNotNull(
            "The isolated API 31 implementation must remain packaged",
            javaClass.classLoader?.getResource(
                "com/ironlog/app/domain/intelligence/AicoreNanoRuntime.class",
            ),
        )
    }

    @Test
    fun `runtime loader gate rejects every supported legacy API before class loading`() {
        val gate = Class.forName("com.ironlog.app.domain.intelligence.NanoRuntimeApiGate")
        val supports = gate.getDeclaredMethod("supports", Int::class.javaPrimitiveType)
        val loadIfSupported = gate.getDeclaredMethod(
            "loadIfSupported",
            Int::class.javaPrimitiveType,
            kotlin.jvm.functions.Function0::class.java,
        )
        var loadCount = 0
        val loader = {
            loadCount += 1
            "runtime"
        }

        (26..30).forEach { api ->
            assertFalse("API $api must not load AICore", supports.invoke(null, api) as Boolean)
            assertNull(loadIfSupported.invoke(null, api, loader))
        }
        assertEquals("legacy APIs must not invoke the runtime loader", 0, loadCount)
        assertTrue("API 31 may load AICore", supports.invoke(null, 31) as Boolean)
        assertEquals("runtime", loadIfSupported.invoke(null, 31, loader))
        assertEquals(1, loadCount)
    }

    private fun classBytes(resource: String): ByteArray =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(resource)) {
            "Missing compiled class resource: $resource"
        }.use { it.readBytes() }

    private companion object {
        const val AICORE_PACKAGE = "com.google.ai.edge.aicore"
        const val AICORE_RUNTIME_SOURCE = "AicoreNanoRuntime.kt"
    }
}
