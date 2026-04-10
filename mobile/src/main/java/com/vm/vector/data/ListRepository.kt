package com.vm.vector.data

import com.vm.core.models.VectorList
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Lists data: local cache is primary. Drive is used only on explicit refresh and when saving (if online).
 * Root ID in DataStore points to the 'Data' folder.
 */
@OptIn(ExperimentalSerializationApi::class)
class ListRepository(
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
    private val listsLocalCache: ListsLocalCache,
) {

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
        explicitNulls = false
        encodeDefaults = false
    }

    /** Lists JSON files from Drive (used only when user taps Refresh). */
    suspend fun listJsonFilesFromDrive(): DriveResult<List<DriveFileInfo>> {
        val rootFolderId = preferenceManager.googleDriveFolderId.first()
        if (rootFolderId.isNullOrBlank()) return DriveResult.ConfigurationError
        return driveService.listJsonFiles(rootFolderId)
    }

    /** Lists JSON files from local cache. Use this for dropdown; no network. */
    suspend fun listLocalJsonFiles(): List<DriveFileInfo> =
        listsLocalCache.loadManifest()

    /** Gets list content from local cache. Returns null if not found. */
    suspend fun getLocalFileContent(fileId: String): VectorList? =
        listsLocalCache.loadFileContent(fileId)

    /**
     * Refreshes from Drive: clears local cache, downloads all JSON files from the lists folder,
     * saves them locally so local state matches Drive.
     */
    suspend fun refreshFromDrive(): DriveResult<List<DriveFileInfo>> {
        val rootFolderId = preferenceManager.googleDriveFolderId.first()
        if (rootFolderId.isNullOrBlank()) return DriveResult.ConfigurationError
        when (val listResult = driveService.listJsonFiles(rootFolderId)) {
            is DriveResult.Success -> {
                listsLocalCache.clear()
                val files = listResult.data
                for (file in files) {
                    when (val contentResult = driveService.getFileContent(file.id)) {
                        is DriveResult.Success -> {
                            val export = contentResult.data.copy(
                                id = "",
                                items = contentResult.data.items.map { it.copy(id = "") }
                            )
                            val json = prettyJson.encodeToString(export)
                            listsLocalCache.saveFileContent(file.name, json)
                        }
                        else -> { /* skip this file on error */ }
                    }
                }
                listsLocalCache.saveManifest(files)
                return DriveResult.Success(listsLocalCache.loadManifest())
            }
            is DriveResult.Unauthorized -> return listResult
            is DriveResult.ConfigurationError -> return listResult
            is DriveResult.ListsFolderNotFound -> return listResult
            is DriveResult.NeedsRemoteConsent -> return listResult
            is DriveResult.OtherError -> return listResult
        }
    }

    suspend fun getFileContentFromDrive(fileId: String): DriveResult<VectorList> =
        driveService.getFileContent(fileId)

    suspend fun createFile(fileName: String, jsonContent: String): DriveResult<DriveFileInfo> {
        val rootFolderId = preferenceManager.googleDriveFolderId.first()
        if (rootFolderId.isNullOrBlank()) return DriveResult.ConfigurationError
        val result = driveService.uploadJsonFile(rootFolderId, fileName, jsonContent)
        if (result is DriveResult.Success) {
            listsLocalCache.saveFileContent(result.data.name, jsonContent)
            val manifest = listsLocalCache.loadManifest() + result.data
            listsLocalCache.saveManifest(manifest)
        }
        return result
    }

    suspend fun deleteFile(fileId: String): DriveResult<Unit> {
        val manifest = listsLocalCache.loadManifest()
        val entry = manifest.find { it.id == fileId }
        val result = driveService.deleteFile(fileId)
        if (result is DriveResult.Success && entry != null) {
            listsLocalCache.removeFile(entry.name)
            listsLocalCache.saveManifest(manifest.filter { it.id != fileId })
        }
        return result
    }

    /**
     * Saves list to local cache first, then tries to upload to Drive if online.
     * Caller can show "Saved locally" when Drive fails (e.g. offline).
     */
    suspend fun saveFileContent(fileId: String, list: VectorList): SaveFileResult {
        val export = list.copy(
            id = "",
            items = list.items.map { it.copy(id = "") }
        )
        val json = prettyJson.encodeToString(export)
        val manifest = listsLocalCache.loadManifest()
        val name = manifest.find { it.id == fileId }?.name ?: "list.json"
        listsLocalCache.saveFile(fileId, name, list)
        return when (val driveResult = driveService.overwriteFileContent(fileId, json)) {
            is DriveResult.Success -> SaveFileResult.Success
            is DriveResult.NeedsRemoteConsent -> SaveFileResult.NeedsRemoteConsent(driveResult.intent)
            is DriveResult.OtherError -> SaveFileResult.SavedLocallyOnly(driveResult.message)
            else -> SaveFileResult.SavedLocallyOnly("Offline or error — saved locally.")
        }
    }
}

sealed class SaveFileResult {
    object Success : SaveFileResult()
    data class SavedLocallyOnly(val message: String) : SaveFileResult()
    data class NeedsRemoteConsent(val intent: android.content.Intent) : SaveFileResult()
}
