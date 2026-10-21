package com.vm.vector.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiPromptsTest {

    private val dir =
        listOf(File("../shared/ai"), File("shared/ai")).firstOrNull { it.isDirectory }
            ?: error("shared/ai not found from ${File(".").absolutePath}")

    private val expected = mapOf(
        "nutrition" to MODES + "system",
        "activity" to MODES + "system",
        "exercise" to MODES + "system",
        "sleep" to listOf("system"),
        "brain" to listOf("transcribe", "summary"),
        "motion" to listOf("todos"),
        "learning" to listOf("links", "draft", "quiz", "grade"),
    )

    @Test
    fun everySectionTheClientsReadExists() {
        for ((file, sections) in expected) {
            val parsed = AiPrompts.parse(File(dir, "$file.md").readText())
            for (section in sections) {
                assertTrue("$file.md is missing '$section'", parsed[section].orEmpty().isNotBlank())
            }
        }
    }

    @Test
    fun sectionsStopAtTheNextMarker() {
        val parsed = AiPrompts.parse("# Title\nintro\n=== a ===\none\n\n=== b ===\ntwo\n")
        assertTrue(parsed == mapOf("a" to "one", "b" to "two"))
    }

    private companion object {
        val MODES = CalendarAiMode.entries.map { "mode.${it.name}" }
    }
}
