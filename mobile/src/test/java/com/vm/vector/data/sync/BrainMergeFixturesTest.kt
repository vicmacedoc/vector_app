package com.vm.vector.data.sync

import com.vm.core.models.BrainNode
import com.vm.core.models.BrainSection
import com.vm.core.models.MemoryNote
import com.vm.core.models.MemoryNoteKind
import com.vm.core.models.VectorItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs the shared cases in sync-fixtures/ (the desktop TypeScript engine runs the same files). */
class BrainMergeFixturesTest {

    private val fixturesDir: File =
        listOf(File("../sync-fixtures"), File("sync-fixtures")).firstOrNull { it.isDirectory }
            ?: error("sync-fixtures/ not found from ${File(".").absolutePath}")

    @Test
    fun allFixturesPass() {
        val files = fixturesDir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        assertTrue("no fixtures found", files.isNotEmpty())
        for (file in files) runFixture(file)
    }

    @Test
    fun dailyFixturesPass() {
        val files = File(fixturesDir, "daily").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        assertTrue("no daily fixtures found", files.isNotEmpty())
        for (file in files) runFixture(file, DailyShards.POLICY)
    }

    private fun runFixture(file: File, policy: MergePolicy = BrainMerge.BRAIN_POLICY) {
        val name = file.name
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val base = recordSet(root["base"])
        val local = recordSet(root["local"])
        val remote = recordSet(root["remote"])
        val expect = root.getValue("expect").jsonObject
        val stamps = root["stamps"]?.jsonObject?.let {
            SideStamps(it.getValue("local").jsonPrimitive.long, it.getValue("remote").jsonPrimitive.long)
        } ?: SideStamps()

        val analysis = BrainMerge.analyze(base, local, remote, policy, stamps)
        assertEquals("$name localChanged", expect.getValue("localChanged").jsonPrimitive.boolean, analysis.localChanged)
        assertEquals("$name remoteChanged", expect.getValue("remoteChanged").jsonPrimitive.boolean, analysis.remoteChanged)

        val expectedConflicts = expect.getValue("conflicts").jsonArray.map { c ->
            val o = c.jsonObject
            Triple(
                o.getValue("id").jsonPrimitive.content,
                o.getValue("kind").jsonPrimitive.content,
                o.getValue("fields").jsonArray.map { it.jsonPrimitive.content }.sorted(),
            )
        }.sortedBy { it.first }
        val actualConflicts = analysis.conflicts
            .map { Triple(it.id, it.kind.name, it.fields.sorted()) }
            .sortedBy { it.first }
        assertEquals("$name conflicts", expectedConflicts, actualConflicts)

        assertSameSet("$name resolvedLocal", recordSet(expect["resolvedLocal"]),
            BrainMerge.resolve(analysis, emptyMap(), Resolution.LOCAL))
        assertSameSet("$name resolvedRemote", recordSet(expect["resolvedRemote"]),
            BrainMerge.resolve(analysis, emptyMap(), Resolution.REMOTE))
        expect["resolvedBoth"]?.let { both ->
            val choices = analysis.conflicts.associate { it.id to Resolution.BOTH }
            assertSameSet("$name resolvedBoth", recordSet(both), BrainMerge.resolve(analysis, choices))
        }
    }

    @Test
    fun stateRoundTripsThroughRecords() {
        val state = BrainState(
            rev = 3,
            nodes = listOf(BrainNode(id = "node_1", section = BrainSection.MEMORY, name = "Books", createdAt = 1)),
            notes = listOf(MemoryNote(id = "note_1", nodeId = "node_1", kind = MemoryNoteKind.TEXT, createdAt = 1, updatedAt = 2)),
            supermarket = SyncSupermarket(
                sections = listOf("Dairy"),
                items = listOf(
                    VectorItem(title = "Milk", quantity = 2.0, category = "Dairy"),
                    VectorItem(title = "milk ", quantity = 1.0),
                ),
            ),
        )
        val records = state.toRecords()
        assertEquals(setOf("milk", "milk#2"), records.getValue(BrainMerge.SUPERMARKET_ITEMS).keys)
        val back = BrainState.fromRecords(records, state)
        assertEquals(BrainMerge.canonical(records), BrainMerge.canonical(back.toRecords()))
        assertEquals(state.supermarket.items.map { it.title }, back.supermarket.items.map { it.title })
    }

    @Test
    fun copyKeysDoNotCollide() {
        val note = { title: String -> JsonObject(mapOf("id" to Json.parseToJsonElement("\"n\""), "title" to Json.parseToJsonElement("\"$title\""))) }
        val base = mapOf(BrainMerge.NOTES to mapOf("n" to note("a")))
        val local = mapOf(BrainMerge.NOTES to mapOf("n" to note("b"), "n_copy" to note("x")))
        val remote = mapOf(BrainMerge.NOTES to mapOf("n" to note("c"), "n_copy" to note("x")))
        val analysis = BrainMerge.analyze(base, local, remote)
        val merged = BrainMerge.resolve(analysis, mapOf("notes/n" to Resolution.BOTH))
        assertEquals(setOf("n", "n_copy", "n_copy2"), merged.getValue(BrainMerge.NOTES).keys)
    }

    private fun assertSameSet(label: String, expected: RecordSet, actual: RecordSet) {
        assertEquals(label, BrainMerge.canonical(expected), BrainMerge.canonical(actual))
    }

    private fun recordSet(element: kotlinx.serialization.json.JsonElement?): RecordSet {
        val obj = element as? JsonObject ?: return emptyMap()
        return obj.mapValues { (_, records) -> records.jsonObject.mapValues { it.value.jsonObject } }
    }
}
