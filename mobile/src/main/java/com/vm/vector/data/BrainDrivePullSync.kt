package com.vm.vector.data

import android.util.Log
import com.vm.core.models.MemoryNoteKind
import java.util.UUID

private const val TAG = "BrainDrivePullSync"
private const val FOLDER_MIME = "application/vnd.google-apps.folder"

/**
 * One-way import of new Drive files/folders into local Brain (does not delete local-only items).
 */
class BrainDrivePullSync(
    private val driveService: DriveService,
    private val repository: BrainRepository,
    private val fileStore: BrainFileStore,
) {
    data class PullStats(
        val importedFiles: Int = 0,
        val importedFolders: Int = 0,
    )

    suspend fun pull(brainRootFolderId: String): Result<PullStats> = runCatching {
        var stats = PullStats()
        val memoryFolderId = ensureSubfolder(brainRootFolderId, DriveConfig.BRAIN_MEMORY_FOLDER) ?: return@runCatching stats
        val modelFolderId = ensureSubfolder(brainRootFolderId, DriveConfig.BRAIN_MODEL_FOLDER) ?: return@runCatching stats
        val inboxFolderId = ensureSubfolder(brainRootFolderId, DriveConfig.BRAIN_INBOX_FOLDER) ?: return@runCatching stats

        stats = stats.copy(importedFolders = stats.importedFolders + pullMemoryFolders(memoryFolderId))
        stats = stats.copy(importedFiles = stats.importedFiles + pullInboxFiles(inboxFolderId))
        stats = stats.copy(importedFiles = stats.importedFiles + pullMemoryFilesAtRoot(memoryFolderId))
        stats = stats.copy(importedFiles = stats.importedFiles + pullModelTiles(modelFolderId))
        stats
    }.onFailure { Log.w(TAG, "Brain pull failed", it) }

    private suspend fun ensureSubfolder(parentId: String, name: String): String? =
        when (val r = driveService.discoverOrCreateFolder(parentId, name)) {
            is DriveResult.Success -> r.data
            else -> {
                Log.w(TAG, "Could not ensure folder $name under $parentId")
                null
            }
        }

    /** Each top-level Drive folder under Memory is a memory tile; nested folders feed the same tile. */
    private suspend fun pullMemoryFolders(memoryFolderId: String): Int {
        val rootPrefix = "${BrainFileStore.ROOT}/${DriveConfig.BRAIN_MEMORY_FOLDER}"
        var imported = 0
        val children = listChildren(memoryFolderId) ?: return 0
        for (item in children) {
            if (item.mimeType != FOLDER_MIME) continue
            val known = repository.getNodeByDriveFolderId(item.id) != null
            val tile = repository.memoryTileForDriveFolder(item.id, item.name)
            if (!known) imported++
            imported += importMemoryFolderTree(item.id, "$rootPrefix/${sanitizePathSegment(item.name)}", tile.id)
        }
        return imported
    }

    private suspend fun importMemoryFolderTree(
        driveFolderId: String,
        relativePrefix: String,
        tileId: String,
    ): Int {
        var count = importMemoryFilesInFolder(driveFolderId, relativePrefix, tileId)
        val children = listChildren(driveFolderId) ?: return count
        for (item in children) {
            if (item.mimeType != FOLDER_MIME) continue
            count += importMemoryFolderTree(item.id, "$relativePrefix/${sanitizePathSegment(item.name)}", tileId)
        }
        return count
    }

    private suspend fun pullMemoryFilesAtRoot(memoryFolderId: String): Int {
        val prefix = "${BrainFileStore.ROOT}/${DriveConfig.BRAIN_MEMORY_FOLDER}"
        return importMemoryFilesInFolder(memoryFolderId, prefix, null)
    }

    private suspend fun pullInboxFiles(inboxFolderId: String): Int {
        val prefix = "${BrainFileStore.ROOT}/${DriveConfig.BRAIN_INBOX_FOLDER}"
        return importMemoryFilesInFolder(inboxFolderId, prefix, null)
    }

    private suspend fun importMemoryFilesInFolder(
        driveFolderId: String,
        relativePrefix: String,
        nodeId: String?,
    ): Int {
        val children = listChildren(driveFolderId) ?: return 0
        var count = 0
        for (item in children) {
            if (item.mimeType == FOLDER_MIME) continue
            if (repository.getAssetByDriveFileId(item.id) != null) continue
            if (importMemoryFile(item, relativePrefix, nodeId)) count++
        }
        return count
    }

    private suspend fun importMemoryFile(
        file: DriveListedFile,
        relativePrefix: String,
        nodeId: String?,
    ): Boolean {
        val mime = file.mimeType ?: "application/octet-stream"
        val relativePath = "$relativePrefix/${sanitizePathSegment(file.name)}"
        val bytes = downloadBytes(file.id) ?: return false
        val asset = fileStore.writeDownloadedDriveFile(
            relativePath = relativePath,
            bytes = bytes,
            mimeType = mime,
            fileName = file.name,
            driveFileId = file.id,
        ).getOrElse {
            Log.w(TAG, "Failed to write ${file.name}", it)
            return false
        }
        when (val kind = memoryNoteKindForMime(mime)) {
            MemoryNoteKind.TEXT -> {
                val text = runCatching { bytes.decodeToString() }.getOrDefault("")
                val note = repository.createTextNote(
                    title = file.name,
                    body = text,
                    nodeId = nodeId,
                )
                repository.saveNote(note.copy(assetId = asset.id))
            }
            else -> repository.createAssetNote(kind, file.name, asset, nodeId)
        }
        return true
    }

    /** Each Drive folder under Model is a Learn tile; its files become the tile's notes. */
    private suspend fun pullModelTiles(modelFolderId: String): Int {
        val children = listChildren(modelFolderId) ?: return 0
        var count = 0
        for (item in children) {
            if (item.mimeType != FOLDER_MIME) continue
            val tile = repository.learnTileForDriveFolder(item.id, item.name)
            val localPrefix = "${BrainFileStore.ROOT}/${DriveConfig.BRAIN_MODEL_FOLDER}/${tile.id}"
            count += importMemoryFilesInFolder(item.id, localPrefix, tile.id)
        }
        return count
    }

    private suspend fun listChildren(folderId: String): List<DriveListedFile>? =
        when (val r = driveService.listChildrenWithMetadata(folderId)) {
            is DriveResult.Success -> r.data
            else -> {
                Log.w(TAG, "listChildren failed for $folderId")
                null
            }
        }

    private suspend fun downloadBytes(fileId: String): ByteArray? =
        when (val r = driveService.downloadFileBytes(fileId)) {
            is DriveResult.Success -> r.data
            else -> {
                Log.w(TAG, "download failed for $fileId")
                null
            }
        }

    private fun sanitizePathSegment(name: String): String {
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(200)
        return safe.ifBlank { "file_${UUID.randomUUID().toString().take(8)}" }
    }
}
