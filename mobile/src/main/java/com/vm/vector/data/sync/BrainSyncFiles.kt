package com.vm.vector.data.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.io.File

/** Remote state that changed while this device also had edits; waits for the user's merge choice. */
@Serializable
data class PendingMerge(
    val remote: BrainState,
    val remoteVersion: Long,
    val detectedAt: Long,
)

/** Device-local sync bookkeeping under filesDir/brain_sync/. */
class BrainSyncFiles(context: Context) {

    private val dir = File(context.applicationContext.filesDir, DIR).also { it.mkdirs() }
    private val baseFile = File(dir, "base.json")
    private val pendingFile = File(dir, "pending_remote.json")

    fun readBase(): BrainState? = read(baseFile)?.let { runCatching { BrainStateCodec.decode(it) }.getOrNull() }

    fun writeBase(state: BrainState) = write(baseFile, BrainStateCodec.encode(state))

    /** After a database restore the old base no longer describes this device; the next sync starts fresh. */
    fun clearBase() {
        baseFile.delete()
        clearPending()
    }

    fun readPending(): PendingMerge? = read(pendingFile)?.let {
        runCatching { BrainStateCodec.json.decodeFromString(PendingMerge.serializer(), it) }.getOrNull()
    }

    fun writePending(pending: PendingMerge) {
        write(pendingFile, BrainStateCodec.json.encodeToString(PendingMerge.serializer(), pending))
        _hasPending.value = true
    }

    fun clearPending() {
        pendingFile.delete()
        _hasPending.value = false
    }

    fun refreshPendingFlag() {
        _hasPending.value = pendingFile.exists()
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
        private const val DIR = "brain_sync"
        private val _hasPending = MutableStateFlow(false)
        val hasPending: StateFlow<Boolean> = _hasPending.asStateFlow()
    }
}
