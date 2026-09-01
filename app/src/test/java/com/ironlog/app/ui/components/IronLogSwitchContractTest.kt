package com.ironlog.app.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class IronLogSwitchContractTest {
    @Test
    fun `production switches use one shared visual contract`() {
        val sourceRoot = File("src/main/java")
        val rawSwitchCalls = sourceRoot
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" && file.name != "IronLogSwitch.kt" }
            .flatMap { file ->
                Regex("""\bSwitch\s*\(""")
                    .findAll(file.readText())
                    .map { match -> "${file.invariantSeparatorsPath}:${match.range.first}" }
            }
            .toList()

        assertEquals("Raw Material switches bypass the IronLog contrast contract", emptyList<String>(), rawSwitchCalls)
    }
}
