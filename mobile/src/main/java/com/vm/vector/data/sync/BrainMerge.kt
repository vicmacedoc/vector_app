package com.vm.vector.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** key -> record */
typealias Records = Map<String, JsonObject>

/** collection -> key -> record. See docs/brain-sync-spec.md. */
typealias RecordSet = Map<String, Records>

enum class ConflictKind { EDIT_EDIT, ADD_ADD, DELETE_EDIT, EDIT_DELETE }

enum class Resolution { LOCAL, REMOTE, BOTH }

enum class Side { LOCAL, REMOTE }

/** When each side last edited the file; drives "newer side" picks. Ties favour local. */
data class SideStamps(val local: Long = 0L, val remote: Long = 0L)

/**
 * Collection rules for one sync file. Brain and the daily shards differ only here
 * (docs/brain-sync-spec.md, docs/daily-sync-spec.md).
 */
data class MergePolicy(
    /** Collections offering "Keep both". */
    val keepBoth: Set<String>,
    /** Collection -> stamp field; records there never conflict (edit beats delete, higher stamp wins, tie -> local). */
    val newestRecordWins: Map<String, String>,
    /** Collections whose same-field conflicts go to the newer side, except [textFields]. */
    val autoFields: Set<String>,
    val textFields: Map<String, Set<String>>,
    val finalize: (RecordSet, RecordSet) -> RecordSet,
)

data class MergeConflict(
    val collection: String,
    val key: String,
    val kind: ConflictKind,
    /** Fields changed differently on both sides; empty for delete conflicts. */
    val fields: List<String>,
    val base: JsonObject?,
    val local: JsonObject?,
    val remote: JsonObject?,
    /** Field-merged record holding local values in [fields]; null for delete conflicts. */
    val merged: JsonObject?,
    val canKeepBoth: Boolean,
    /** Side that edited last; the merge sheet preselects it. */
    val newer: Side = Side.LOCAL,
) {
    val id: String get() = "$collection/$key"
}

data class MergeAnalysis(
    val localChanged: Boolean,
    val remoteChanged: Boolean,
    val localChanges: Int,
    val remoteChanges: Int,
    val conflicts: List<MergeConflict>,
    val local: RecordSet,
    val remote: RecordSet,
    internal val autoMerged: RecordSet,
    internal val order: Map<String, List<String>>,
    internal val policy: MergePolicy,
)

object BrainMerge {

    const val NODES = "nodes"
    const val NOTES = "notes"
    const val ASSETS = "assets"
    const val LEARNING_TILES = "learningTiles"
    const val LEARNING_LINKS = "learningLinks"
    const val TAGS = "tags"
    const val TASKS = "tasks"
    const val SCORE_EVENTS = "scoreEvents"
    const val SUPERMARKET_ITEMS = "supermarketItems"
    const val META = "meta"

    val KEEP_BOTH_COLLECTIONS = setOf(NOTES, TASKS)

    val BRAIN_POLICY = MergePolicy(
        keepBoth = KEEP_BOTH_COLLECTIONS,
        newestRecordWins = mapOf(SCORE_EVENTS to "createdAt"),
        autoFields = emptySet(),
        textFields = emptyMap(),
        finalize = { set, remote -> finalize(set, remote) },
    )

    private val canonicalJson = Json { prettyPrint = false }

    fun analyze(
        base: RecordSet,
        local: RecordSet,
        remote: RecordSet,
        policy: MergePolicy = BRAIN_POLICY,
        stamps: SideStamps = SideStamps(),
    ): MergeAnalysis {
        val newer = if (stamps.remote > stamps.local) Side.REMOTE else Side.LOCAL
        val merged = LinkedHashMap<String, LinkedHashMap<String, JsonObject>>()
        val order = LinkedHashMap<String, List<String>>()
        val conflicts = mutableListOf<MergeConflict>()
        var localChanges = 0
        var remoteChanges = 0

        for (collection in orderedUnion(local.keys, remote.keys, base.keys)) {
            val b = base[collection].orEmpty()
            val l = local[collection].orEmpty()
            val r = remote[collection].orEmpty()
            val out = LinkedHashMap<String, JsonObject>()
            val keys = orderedUnion(l.keys, r.keys, b.keys)
            for (key in keys) {
                val bv = b[key]
                val lv = l[key]
                val rv = r[key]
                if (!sameRecord(lv, bv)) localChanges++
                if (!sameRecord(rv, bv)) remoteChanges++
                val keepBoth = collection in policy.keepBoth
                val stampField = policy.newestRecordWins[collection]
                val pick: JsonObject? = when {
                    sameRecord(lv, rv) -> lv
                    sameRecord(lv, bv) -> rv
                    sameRecord(rv, bv) -> lv
                    stampField != null -> newerRecord(lv, rv, stampField)
                    lv != null && rv != null -> {
                        val auto = if (collection in policy.autoFields) newer else null
                        val (fieldMerged, conflicting) = mergeFields(bv, lv, rv, auto, policy.textFields[collection].orEmpty())
                        if (conflicting.isEmpty()) {
                            fieldMerged
                        } else {
                            val kind = if (bv == null) ConflictKind.ADD_ADD else ConflictKind.EDIT_EDIT
                            conflicts += MergeConflict(collection, key, kind, conflicting, bv, lv, rv, fieldMerged, keepBoth, newer)
                            null
                        }
                    }
                    lv == null -> {
                        conflicts += MergeConflict(collection, key, ConflictKind.DELETE_EDIT, emptyList(), bv, null, rv, null, keepBoth, newer)
                        null
                    }
                    else -> {
                        conflicts += MergeConflict(collection, key, ConflictKind.EDIT_DELETE, emptyList(), bv, lv, null, null, keepBoth, newer)
                        null
                    }
                }
                if (pick != null) out[key] = pick
            }
            merged[collection] = out
            order[collection] = keys
        }

        return MergeAnalysis(
            localChanged = canonical(local) != canonical(base),
            remoteChanged = canonical(remote) != canonical(base),
            localChanges = localChanges,
            remoteChanges = remoteChanges,
            conflicts = conflicts,
            local = local,
            remote = remote,
            autoMerged = merged,
            order = order,
            policy = policy,
        )
    }

    /** Smart merge: auto-merged records plus one [Resolution] per conflict id ([default] when missing). */
    fun resolve(
        analysis: MergeAnalysis,
        resolutions: Map<String, Resolution>,
        default: Resolution = Resolution.LOCAL,
    ): RecordSet {
        val conflictsById = analysis.conflicts.associateBy { it.id }
        val out = LinkedHashMap<String, Records>()
        for ((collection, keys) in analysis.order) {
            val auto = analysis.autoMerged[collection].orEmpty()
            val records = LinkedHashMap<String, JsonObject>()
            val copies = mutableListOf<Pair<String, JsonObject>>()
            for (key in keys) {
                val conflict = conflictsById["$collection/$key"]
                if (conflict == null) {
                    auto[key]?.let { records[key] = it }
                    continue
                }
                var choice = resolutions[conflict.id] ?: default
                if (choice == Resolution.BOTH && !conflict.canKeepBoth) choice = Resolution.LOCAL
                when (conflict.kind) {
                    ConflictKind.EDIT_EDIT, ConflictKind.ADD_ADD -> {
                        val merged = conflict.merged ?: continue
                        when (choice) {
                            Resolution.LOCAL -> records[key] = merged
                            Resolution.REMOTE -> records[key] = withFields(merged, conflict.fields, conflict.remote)
                            Resolution.BOTH -> {
                                records[key] = merged
                                conflict.remote?.let { copies += key to it }
                            }
                        }
                    }
                    ConflictKind.DELETE_EDIT -> if (choice != Resolution.LOCAL) conflict.remote?.let { records[key] = it }
                    ConflictKind.EDIT_DELETE -> if (choice != Resolution.REMOTE) conflict.local?.let { records[key] = it }
                }
            }
            for ((key, record) in copies) {
                val copyKey = copyKey(key, records.keys + keys)
                records[copyKey] = withId(record, copyKey)
            }
            out[collection] = records
        }
        return analysis.policy.finalize(out, analysis.remote)
    }

    /** Keep one side as-is ("Keep this device" / "Keep the other device"), then run the clean-up passes. */
    fun whole(chosen: RecordSet, remote: RecordSet, policy: MergePolicy = BRAIN_POLICY): RecordSet =
        policy.finalize(chosen, remote)

    fun finalize(set: RecordSet, remote: RecordSet): RecordSet {
        val out = LinkedHashMap<String, LinkedHashMap<String, JsonObject>>()
        set.forEach { (c, records) -> out[c] = LinkedHashMap(records) }
        dedupeDriveFiles(out, remote)
        enforceIntegrity(out)
        return out
    }

    // ---- equality & canonical form ----

    fun sameRecord(a: JsonObject?, b: JsonObject?): Boolean =
        if (a == null || b == null) a == null && b == null else sameJson(a, b)

    /** Deep equality where numbers compare by value and a missing field equals null. */
    fun sameJson(a: JsonElement?, b: JsonElement?): Boolean {
        val x = a ?: JsonNull
        val y = b ?: JsonNull
        if (x is JsonNull || y is JsonNull) return x is JsonNull && y is JsonNull
        return when (x) {
            is JsonObject -> y is JsonObject && (x.keys + y.keys).all { sameJson(x[it], y[it]) }
            is JsonArray -> y is JsonArray && x.size == y.size && x.indices.all { sameJson(x[it], y[it]) }
            is JsonPrimitive -> {
                if (y !is JsonPrimitive || x.isString != y.isString) return false
                if (x.isString) return x.content == y.content
                val dx = x.content.toDoubleOrNull()
                val dy = y.content.toDoubleOrNull()
                if (dx != null && dy != null) dx == dy else x.content == y.content
            }
        }
    }

    /** Sorted collections/keys/fields, null fields dropped, numbers normalized. */
    fun canonical(set: RecordSet): String {
        val root = LinkedHashMap<String, JsonElement>()
        for (collection in set.keys.sorted()) {
            val records = set[collection].orEmpty()
            if (records.isEmpty()) continue
            val sorted = LinkedHashMap<String, JsonElement>()
            for (key in records.keys.sorted()) sorted[key] = canonicalElement(records.getValue(key))
            root[collection] = JsonObject(sorted)
        }
        return canonicalJson.encodeToString(JsonElement.serializer(), JsonObject(root))
    }

    fun isEmpty(set: RecordSet): Boolean = set.values.all { it.isEmpty() }

    private fun canonicalElement(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> {
            val sorted = LinkedHashMap<String, JsonElement>()
            for (k in e.keys.sorted()) {
                val v = e.getValue(k)
                if (v !is JsonNull) sorted[k] = canonicalElement(v)
            }
            JsonObject(sorted)
        }
        is JsonArray -> JsonArray(e.map { canonicalElement(it) })
        is JsonNull -> e
        is JsonPrimitive -> {
            if (e.isString) {
                e
            } else {
                val d = e.content.toDoubleOrNull()
                when {
                    d == null -> e
                    d == Math.floor(d) && kotlin.math.abs(d) < 1e15 -> JsonPrimitive(d.toLong())
                    else -> JsonPrimitive(d)
                }
            }
        }
    }

    // ---- helpers ----

    private fun mergeFields(
        base: JsonObject?,
        local: JsonObject,
        remote: JsonObject,
        autoNewer: Side?,
        textFields: Set<String>,
    ): Pair<JsonObject, List<String>> {
        val out = LinkedHashMap<String, JsonElement>()
        val conflicting = mutableListOf<String>()
        for (field in orderedUnion(local.keys, remote.keys, base?.keys.orEmpty())) {
            val bv = base?.get(field)
            val lv = local[field]
            val rv = remote[field]
            val pick = when {
                sameJson(lv, rv) -> lv
                sameJson(lv, bv) -> rv
                sameJson(rv, bv) -> lv
                autoNewer != null && field !in textFields -> if (autoNewer == Side.REMOTE) rv else lv
                else -> {
                    conflicting += field
                    lv
                }
            }
            out[field] = pick ?: JsonNull
        }
        return JsonObject(out) to conflicting
    }

    private fun newerRecord(local: JsonObject?, remote: JsonObject?, field: String): JsonObject? {
        if (local == null) return remote
        if (remote == null) return local
        val lt = (local[field] as? JsonPrimitive)?.longOrNull ?: 0L
        val rt = (remote[field] as? JsonPrimitive)?.longOrNull ?: 0L
        return if (rt > lt) remote else local
    }

    private fun withFields(target: JsonObject, fields: List<String>, source: JsonObject?): JsonObject {
        val out = LinkedHashMap<String, JsonElement>(target)
        for (f in fields) out[f] = source?.get(f) ?: JsonNull
        return JsonObject(out)
    }

    private fun withId(record: JsonObject, id: String): JsonObject =
        if ("id" in record) JsonObject(LinkedHashMap(record).apply { put("id", JsonPrimitive(id)) }) else record

    private fun copyKey(key: String, taken: Collection<String>): String {
        val takenSet = taken.toSet()
        var candidate = "${key}_copy"
        var n = 2
        while (candidate in takenSet) candidate = "${key}_copy${n++}"
        return candidate
    }

    private fun str(e: JsonElement?): String? =
        (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun dedupeDriveFiles(out: MutableMap<String, LinkedHashMap<String, JsonObject>>, remote: RecordSet) {
        val assets = out[ASSETS] ?: return
        val remoteAssets = remote[ASSETS].orEmpty()
        val replaced = HashMap<String, String>()
        assets.entries
            .mapNotNull { e -> str(e.value["driveFileId"])?.takeIf { it.isNotBlank() }?.let { it to e.key } }
            .groupBy({ it.first }, { it.second })
            .values
            .filter { it.size > 1 }
            .forEach { keys ->
                val keep = keys.firstOrNull { it in remoteAssets } ?: keys.min()
                keys.filter { it != keep }.forEach { replaced[it] = keep }
            }
        if (replaced.isEmpty()) return
        replaced.keys.forEach { assets.remove(it) }

        val notes = out[NOTES] ?: return
        for (key in notes.keys.toList()) {
            val note = notes.getValue(key)
            val target = replaced[str(note["assetId"])] ?: continue
            notes[key] = JsonObject(LinkedHashMap(note).apply { put("assetId", JsonPrimitive(target)) })
        }
        val remoteNotes = remote[NOTES].orEmpty()
        val kept = replaced.values.toSet()
        notes.entries
            .filter { str(it.value["assetId"]) in kept }
            .groupBy({ Triple(str(it.value["assetId"]), str(it.value["nodeId"]), str(it.value["kind"])) }, { it.key })
            .values
            .filter { it.size > 1 }
            .forEach { keys ->
                val keep = keys.firstOrNull { it in remoteNotes } ?: keys.min()
                keys.filter { it != keep }.forEach { notes.remove(it) }
            }
    }

    private fun enforceIntegrity(out: MutableMap<String, LinkedHashMap<String, JsonObject>>) {
        val nodeIds = out[NODES]?.keys?.toSet().orEmpty()
        fun clearMissing(collection: String, vararg fields: String) {
            val records = out[collection] ?: return
            for (key in records.keys.toList()) {
                val record = records.getValue(key)
                val stale = fields.filter { f -> str(record[f])?.let { it !in nodeIds } == true }
                if (stale.isEmpty()) continue
                records[key] = JsonObject(LinkedHashMap(record).apply { stale.forEach { put(it, JsonNull) } })
            }
        }
        clearMissing(NOTES, "nodeId")
        clearMissing(TASKS, "nodeId", "linkedTileId")
        out[LEARNING_TILES]?.keys?.retainAll(nodeIds)
        out[LEARNING_LINKS]?.entries?.removeAll { (_, link) ->
            str(link["fromNodeId"]) !in nodeIds || str(link["toNodeId"]) !in nodeIds
        }
    }

    private fun orderedUnion(vararg sets: Collection<String>): List<String> {
        val seen = LinkedHashSet<String>()
        sets.forEach { seen.addAll(it) }
        return seen.toList()
    }
}
