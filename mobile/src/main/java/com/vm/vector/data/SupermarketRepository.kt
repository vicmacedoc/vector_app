package com.vm.vector.data

import android.content.Context
import com.vm.core.models.VectorList
import com.vm.vector.data.sync.SyncSupermarket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The pinned Supermarket list, part of Brain Motion.
 * The local file filesDir/brain/Motion/Supermarket.json is primary and travels to other devices inside the
 * Brain sync state. Drive's brain/Motion/Supermarket.json is only a human-readable export written after sync.
 * Edits written with [saveLocal] stay "unsaved" (marker file) until the user saves explicitly.
 */
@OptIn(ExperimentalSerializationApi::class)
class SupermarketRepository(
    private val context: Context,
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
        explicitNulls = false
        encodeDefaults = false
    }

    private val motionDir: File
        get() = File(context.filesDir, "${BrainFileStore.ROOT}/${DriveConfig.BRAIN_MOTION_FOLDER}").also { it.mkdirs() }
    private val localFile: File get() = File(motionDir, DriveConfig.SUPERMARKET_FILENAME)
    private val driveIdFile: File get() = File(motionDir, DRIVE_ID_FILE)
    private val unsyncedFile: File get() = File(motionDir, UNSYNCED_FILE)
    private val baselineFile: File get() = File(motionDir, BASELINE_FILE)

    suspend fun hasUnsyncedChanges(): Boolean = withContext(Dispatchers.IO) { unsyncedFile.exists() }

    /** Writes only the phone copy and marks it as not explicitly saved yet. */
    suspend fun saveLocal(list: VectorList) = withContext(Dispatchers.IO) {
        localFile.writeText(encode(list))
        runCatching { unsyncedFile.createNewFile() }
        BrainSyncWorker.requestDebounced(context)
    }

    suspend fun loadLocal(): VectorList? = withContext(Dispatchers.IO) {
        migrateLegacyCache()
        if (!localFile.exists()) return@withContext null
        decode(localFile.readText())?.also { if (!baselineFile.exists()) writeBaseline(it) }
    }

    fun hasLocalFile(): Boolean = localFile.exists()

    /**
     * `remaining` per item title (lowercased) as of the last explicit save. Restock detection
     * compares against it, since autosaves keep rewriting the local file.
     */
    suspend fun loadBaseline(): Map<String, Double> = withContext(Dispatchers.IO) {
        if (!baselineFile.exists()) return@withContext emptyMap()
        runCatching { json.decodeFromString<Map<String, Double>>(baselineFile.readText()) }.getOrDefault(emptyMap())
    }

    private fun writeBaseline(list: VectorList) {
        val snapshot = list.items.associate { baselineKey(it.title) to (it.remaining ?: it.quantity ?: 1.0) }
        runCatching { baselineFile.writeText(json.encodeToString(snapshot)) }
    }

    /** Explicit save: writes locally and queues a Brain sync. Watch toggles pass [updateBaseline] = false. */
    suspend fun save(list: VectorList, updateBaseline: Boolean = true): SaveFileResult {
        withContext(Dispatchers.IO) {
            localFile.writeText(encode(list))
            if (updateBaseline) writeBaseline(list)
            runCatching { unsyncedFile.delete() }
        }
        BrainSyncWorker.requestDebounced(context)
        return SaveFileResult.Success
    }

    /** The list as carried in the Brain sync state. */
    suspend fun syncSnapshot(): SyncSupermarket = withContext(Dispatchers.IO) {
        migrateLegacyCache()
        val list = if (localFile.exists()) decode(localFile.readText()) else null
        if (list == null) {
            SyncSupermarket()
        } else {
            SyncSupermarket(name = list.name, sections = list.sections, items = list.items.map { it.copy(id = "") })
        }
    }

    /** Replaces the local copy with the synced list and tells an open Supermarket screen to reload. */
    suspend fun applySynced(market: SyncSupermarket) {
        withContext(Dispatchers.IO) {
            val list = VectorList(name = market.name, sections = market.sections, items = market.items)
            localFile.writeText(encode(list))
            if (!baselineFile.exists()) writeBaseline(list)
        }
        _changes.tryEmit(Unit)
    }

    /** Writes the human-readable export on Drive; failures are ignored (the sync state is the source of truth). */
    suspend fun exportToDrive() {
        if (!localFile.exists()) return
        val content = withContext(Dispatchers.IO) { localFile.readText() }
        cachedDriveId()?.let { id ->
            if (driveService.overwriteFileContent(id, content) is DriveResult.Success) return
        }
        val folderId = (motionFolderId() as? DriveResult.Success)?.data ?: return
        when (val existing = driveService.findFileMeta(folderId, DriveConfig.SUPERMARKET_FILENAME)) {
            is DriveResult.Success -> {
                val meta = existing.data
                if (meta != null) {
                    cacheDriveId(meta.id)
                    driveService.overwriteFileContent(meta.id, content)
                } else {
                    val created = driveService.uploadJsonFileToFolder(folderId, DriveConfig.SUPERMARKET_FILENAME, content)
                    if (created is DriveResult.Success) cacheDriveId(created.data.id)
                }
            }
            else -> Unit
        }
    }

    /**
     * Phones that never synced Brain state may only have the list on Drive (brain/Motion or the legacy lists folder).
     * Copies it to the phone once so the first sync carries it.
     */
    suspend fun importLegacyFromDrive(): Boolean {
        if (localFile.exists()) return false
        val folderId = (motionFolderId() as? DriveResult.Success)?.data ?: return false
        val file = (driveService.findFileMeta(folderId, DriveConfig.SUPERMARKET_FILENAME) as? DriveResult.Success)?.data?.id
            ?: legacyListsFileId()
            ?: return false
        val content = (driveService.getFileContent(file) as? DriveResult.Success)?.data ?: return false
        withContext(Dispatchers.IO) {
            localFile.writeText(encode(content))
            writeBaseline(content)
        }
        _changes.tryEmit(Unit)
        return true
    }

    private suspend fun legacyListsFileId(): String? {
        val root = preferenceManager.googleDriveFolderId.first()?.takeIf { it.isNotBlank() } ?: return null
        val listsId = driveService.discoverSubfolder(root, DriveConfig.LISTS_SUBFOLDER_NAME) ?: return null
        return (driveService.findFileMeta(listsId, DriveConfig.SUPERMARKET_FILENAME) as? DriveResult.Success)?.data?.id
    }

    private suspend fun motionFolderId(): DriveResult<String> {
        val root = preferenceManager.googleDriveFolderId.first()
        if (root.isNullOrBlank()) return DriveResult.ConfigurationError
        val brainId = when (val r = driveService.discoverOrCreateBrainFolder(root)) {
            is DriveResult.Success -> r.data
            else -> return r
        }
        return driveService.discoverOrCreateFolder(brainId, DriveConfig.BRAIN_MOTION_FOLDER)
    }

    /** One-time copy of the Supermarket list out of the old lists cache. */
    private fun migrateLegacyCache() {
        if (localFile.exists()) return
        val legacyDir = File(context.filesDir, LEGACY_LISTS_CACHE_DIR)
        val candidates = legacyDir.listFiles()?.filter { it.isFile && it.name != LEGACY_MANIFEST } ?: return
        val legacy = candidates.firstOrNull { it.name.equals(DriveConfig.SUPERMARKET_FILENAME, ignoreCase = true) }
            ?: candidates.firstOrNull { it.name.contains("supermarket", ignoreCase = true) }
            ?: return
        runCatching { legacy.copyTo(localFile) }
        legacyDir.deleteRecursively()
    }

    private fun cachedDriveId(): String? =
        driveIdFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    private fun cacheDriveId(id: String) {
        runCatching { driveIdFile.writeText(id) }
    }

    private fun encode(list: VectorList): String =
        json.encodeToString(list.copy(id = "", items = list.items.map { it.copy(id = "") }))

    private fun decode(raw: String): VectorList? = try {
        val list = json.decodeFromString<VectorList>(raw)
        val baseTime = System.currentTimeMillis()
        list.copy(
            id = list.id.ifBlank { "list_$baseTime" },
            items = list.items.mapIndexed { i, item -> item.copy(id = item.id.ifBlank { "item_${baseTime}_$i" }) },
        )
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val DRIVE_ID_FILE = ".supermarket_drive_id"
        private const val UNSYNCED_FILE = ".supermarket_unsynced"
        private const val BASELINE_FILE = ".supermarket_baseline.json"

        private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Emits when sync replaced the local list. */
        val changes: SharedFlow<Unit> = _changes.asSharedFlow()

        fun baselineKey(title: String): String = title.trim().lowercase()
        private const val LEGACY_LISTS_CACHE_DIR = "lists_cache"
        private const val LEGACY_MANIFEST = "manifest.json"

        fun create(context: Context): SupermarketRepository {
            val app = context.applicationContext
            return SupermarketRepository(
                app,
                PreferenceManager(app),
                DriveService(app, GoogleDriveAuthManager(app)),
            )
        }
    }
}

sealed class SaveFileResult {
    object Success : SaveFileResult()
    data class SavedLocallyOnly(val message: String) : SaveFileResult()
    data class NeedsRemoteConsent(val intent: android.content.Intent) : SaveFileResult()
}
