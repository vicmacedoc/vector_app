package com.vm.vector.data.sync

import com.vm.core.models.BrainNode
import com.vm.core.models.BrainTag
import com.vm.core.models.LearningLink
import com.vm.core.models.LearningTile
import com.vm.core.models.MemoryNote
import com.vm.core.models.MotionScoreEvent
import com.vm.core.models.MotionScoring
import com.vm.core.models.MotionTask
import com.vm.core.models.VectorItem
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

const val BRAIN_STATE_FORMAT_VERSION = 1

@Serializable
data class SyncDevice(
    val deviceId: String = "",
    val name: String = "",
    val platform: String = "",
)

@Serializable
data class SyncSettings(
    val motionDailyTarget: Int = MotionScoring.DEFAULT_DAILY_TARGET,
    val supermarketDay: String = MotionScoring.DEFAULT_SUPERMARKET_DAY.name,
)

/** [com.vm.core.models.BrainAsset] without device-local fields; [path] is relative to Drive `brain/`. */
@Serializable
data class SyncAsset(
    val id: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val driveFileId: String? = null,
    val fileName: String = "",
    val description: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val path: String = "",
)

@Serializable
data class SyncSupermarket(
    val name: String = "Supermarket",
    val sections: List<String> = emptyList(),
    val items: List<VectorItem> = emptyList(),
)

@Serializable
data class BrainState(
    val formatVersion: Int = BRAIN_STATE_FORMAT_VERSION,
    val rev: Long = 0L,
    val writtenBy: SyncDevice? = null,
    val writtenAt: Long = 0L,
    val settings: SyncSettings = SyncSettings(),
    val nodes: List<BrainNode> = emptyList(),
    val notes: List<MemoryNote> = emptyList(),
    val assets: List<SyncAsset> = emptyList(),
    val learningTiles: List<LearningTile> = emptyList(),
    val learningLinks: List<LearningLink> = emptyList(),
    val tags: List<BrainTag> = emptyList(),
    val tasks: List<MotionTask> = emptyList(),
    val scoreEvents: List<MotionScoreEvent> = emptyList(),
    val supermarket: SyncSupermarket = SyncSupermarket(),
) {
    fun toRecords(): RecordSet = BrainStateCodec.toRecords(this)

    companion object {
        fun fromRecords(set: RecordSet, header: BrainState = BrainState()): BrainState =
            BrainStateCodec.fromRecords(set, header)
    }
}

object BrainStateCodec {

    /** Writers emit every field (explicit nulls) so other devices can field-merge. */
    val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun encode(state: BrainState): String = json.encodeToString(BrainState.serializer(), state)

    fun decode(text: String): BrainState = json.decodeFromString(BrainState.serializer(), text)

    fun supermarketKey(title: String): String = title.trim().lowercase()

    fun linkKey(fromNodeId: String, toNodeId: String): String = "$fromNodeId|$toNodeId"

    fun toRecords(state: BrainState): RecordSet {
        val out = LinkedHashMap<String, Records>()
        out[BrainMerge.NODES] = keyed(state.nodes, BrainNode.serializer()) { it.id }
        out[BrainMerge.NOTES] = keyed(state.notes, MemoryNote.serializer()) { it.id }
        out[BrainMerge.ASSETS] = keyed(state.assets, SyncAsset.serializer()) { it.id }
        out[BrainMerge.LEARNING_TILES] = keyed(state.learningTiles, LearningTile.serializer()) { it.id }
        out[BrainMerge.LEARNING_LINKS] =
            keyed(state.learningLinks, LearningLink.serializer()) { linkKey(it.fromNodeId, it.toNodeId) }
        out[BrainMerge.TAGS] = keyed(state.tags, BrainTag.serializer()) { it.id }
        out[BrainMerge.TASKS] = keyed(state.tasks, MotionTask.serializer()) { it.id }
        out[BrainMerge.SCORE_EVENTS] = keyed(state.scoreEvents, MotionScoreEvent.serializer()) { it.id }

        val items = LinkedHashMap<String, JsonObject>()
        val seen = HashMap<String, Int>()
        for (item in state.supermarket.items) {
            val base = supermarketKey(item.title)
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            items[if (n == 1) base else "$base#$n"] = encodeObject(VectorItem.serializer(), item.copy(id = ""))
        }
        out[BrainMerge.SUPERMARKET_ITEMS] = items

        out[BrainMerge.META] = linkedMapOf(
            META_SETTINGS to encodeObject(SyncSettings.serializer(), state.settings),
            META_SUPERMARKET to JsonObject(
                mapOf(
                    "name" to JsonPrimitive(state.supermarket.name),
                    "sections" to json.encodeToJsonElement(ListSerializer(String.serializer()), state.supermarket.sections),
                )
            ),
        )
        return out
    }

    /** Records that no longer decode (corrupt or from a newer format) are dropped. */
    fun fromRecords(set: RecordSet, header: BrainState): BrainState {
        val meta = set[BrainMerge.META].orEmpty()
        val settings = meta[META_SETTINGS]?.let { decodeOrNull(SyncSettings.serializer(), it) } ?: header.settings
        val marketMeta = meta[META_SUPERMARKET]
        val marketName = (marketMeta?.get("name") as? JsonPrimitive)?.content ?: header.supermarket.name
        val sections = marketMeta?.get("sections")
            ?.let { runCatching { json.decodeFromJsonElement(ListSerializer(String.serializer()), it) }.getOrNull() }
            ?: header.supermarket.sections

        return header.copy(
            settings = settings,
            nodes = decodeAll(set, BrainMerge.NODES, BrainNode.serializer()),
            notes = decodeAll(set, BrainMerge.NOTES, MemoryNote.serializer()),
            assets = decodeAll(set, BrainMerge.ASSETS, SyncAsset.serializer()),
            learningTiles = decodeAll(set, BrainMerge.LEARNING_TILES, LearningTile.serializer()),
            learningLinks = decodeAll(set, BrainMerge.LEARNING_LINKS, LearningLink.serializer()),
            tags = decodeAll(set, BrainMerge.TAGS, BrainTag.serializer()),
            tasks = decodeAll(set, BrainMerge.TASKS, MotionTask.serializer()),
            scoreEvents = decodeAll(set, BrainMerge.SCORE_EVENTS, MotionScoreEvent.serializer()),
            supermarket = SyncSupermarket(
                name = marketName,
                sections = sections,
                items = decodeAll(set, BrainMerge.SUPERMARKET_ITEMS, VectorItem.serializer()),
            ),
        )
    }

    /** Round-trips through the typed models so both sides of a comparison carry the same fields. */
    fun normalize(state: BrainState): BrainState = decode(encode(state))

    private fun <T> keyed(items: List<T>, serializer: KSerializer<T>, key: (T) -> String): Records {
        val out = LinkedHashMap<String, JsonObject>()
        for (item in items) out[key(item)] = encodeObject(serializer, item)
        return out
    }

    private fun <T> encodeObject(serializer: KSerializer<T>, value: T): JsonObject =
        json.encodeToJsonElement(serializer, value).jsonObject

    private fun <T> decodeOrNull(serializer: KSerializer<T>, element: JsonElement): T? =
        runCatching { json.decodeFromJsonElement(serializer, element) }.getOrNull()

    private fun <T> decodeAll(set: RecordSet, collection: String, serializer: KSerializer<T>): List<T> =
        set[collection].orEmpty().values.mapNotNull { decodeOrNull(serializer, it) }

    const val META_SETTINGS = "settings"
    const val META_SUPERMARKET = "supermarket"
}
