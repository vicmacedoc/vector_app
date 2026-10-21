package com.vm.vector.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/** Gemini prompt texts from shared/ai/<file>.md (also used by the desktop app); sections start at `=== name ===`. */
object AiPrompts {
    private val cache = ConcurrentHashMap<String, Map<String, String>>()
    private val MARKER = Regex("""^=== ([\w.:-]+) ===\s*$""")

    fun get(context: Context, file: String, section: String): String {
        val sections = cache.getOrPut(file) {
            parse(context.applicationContext.assets.open("ai/$file.md").bufferedReader().use { it.readText() })
        }
        return sections[section] ?: error("Missing prompt section '$section' in ai/$file.md")
    }

    fun parse(text: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        var name: String? = null
        val body = StringBuilder()
        fun flush() {
            name?.let { out[it] = body.toString().trim() }
            body.setLength(0)
        }
        for (line in text.replace("\r\n", "\n").lines()) {
            val match = MARKER.matchEntire(line)
            if (match != null) {
                flush()
                name = match.groupValues[1]
            } else if (name != null) {
                body.append(line).append('\n')
            }
        }
        flush()
        return out
    }
}
