package com.vm.vector.data.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Last shard this device agreed with Drive on, and the Drive version it came from. */
@Serializable
data class ShardBase(
    val file: ShardFile,
    val driveVersion: Long? = null,
)

/** A shard where both devices changed the same record; waits for the user's merge choice. */
@Serializable
data class ShardPending(
    val remote: ShardFile,
    val remoteVersion: Long,
    val detectedAt: Long,
)

/** Device-local daily sync bookkeeping under filesDir/daily_sync/. */
class ShardSyncFiles(context: Context) {

    private val dir = File(context.applicationContext.filesDir, DIR).also { it.mkdirs() }
    private val dirtyFile = File(dir, "dirty.json")
    private val json = ShardFile.json
    private val dirtySerializer = MapSerializer(String.serializer(), Long.serializer())

    private fun key(shard: String) = shard.replace('/', '_')
    private fun baseFile(shard: String) = File(dir, "base_${key(shard)}.json")
    private fun pendingFile(shard: String) = File(dir, "pending_${key(shard)}.json")

    fun readBase(shard: String): ShardBase? =
        read(baseFile(shard))?.let { runCatching { json.decodeFromString(ShardBase.serializer(), it) }.getOrNull() }

    fun writeBase(shard: String, base: ShardBase) = write(baseFile(shard), json.encodeToString(ShardBase.serializer(), base))

    fun readPending(shard: String): ShardPending? =
        read(pendingFile(shard))?.let { runCatching { json.decodeFromString(ShardPending.serializer(), it) }.getOrNull() }

    fun writePending(shard: String, pending: ShardPending) {
        write(pendingFile(shard), json.encodeToString(ShardPending.serializer(), pending))
        refreshPendingFlag()
    }

    fun clearPending(shard: String) {
        pendingFile(shard).delete()
        refreshPendingFlag()
    }

    fun pendingShards(): List<String> = dir.listFiles().orEmpty()
        .mapNotNull { f -> f.name.removePrefix("pending_").removeSuffix(".json").takeIf { f.name.startsWith("pending_") && f.name.endsWith(".json") } }
        .map { if (it.startsWith("days_")) "days/${it.removePrefix("days_")}" else it }
        .sorted()

    fun refreshPendingFlag() {
        _pendingShards.value = pendingShards()
    }

    /** After a database restore the bases no longer describe this device; the next sync starts fresh. */
    fun clearAll() {
        dir.listFiles().orEmpty()
            .filter { it.name.startsWith("base_") || it.name.startsWith("pending_") || it.name == dirtyFile.name }
            .forEach { it.delete() }
        refreshPendingFlag()
    }

    @Synchronized
    fun dirty(): Map<String, Long> =
        read(dirtyFile)?.let { runCatching { json.decodeFromString(dirtySerializer, it) }.getOrNull() }.orEmpty()

    @Synchronized
    fun markDirty(shard: String, at: Long) {
        write(dirtyFile, json.encodeToString(dirtySerializer, dirty() + (shard to at)))
    }

    /** Clears [shard] unless it was edited again after [ifAt]. */
    @Synchronized
    fun clearDirty(shard: String, ifAt: Long) {
        val current = dirty()
        val at = current[shard] ?: return
        if (at <= ifAt) write(dirtyFile, json.encodeToString(dirtySerializer, current - shard))
    }

    private fun read(file: File): String? = if (file.exists()) runCatching { file.readText() }.getOrNull() else null

    private fun write(file: File, text: String) {
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        const val DIR = "daily_sync"
        private val _pendingShards = MutableStateFlow<List<String>>(emptyList())
        val pending: StateFlow<List<String>> = _pendingShards.asStateFlow()
    }
}
