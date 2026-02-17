package com.vm.vector.data

import com.vm.core.models.VectorList
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Fetches list data from [DriveService]. Root ID in DataStore points to the 'Data' folder.
 * Uses discovered 'lists' folder ID to fetch all JSON files. No automatic folder creation.
 */
@OptIn(ExperimentalSerializationApi::class)
class ListRepository(
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
) {

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
        explicitNulls = false
        encodeDefaults = false
    }

    suspend fun listJsonFiles(): DriveResult<List<DriveFileInfo>> {
        val rootFolderId = preferenceManager.googleDriveFolderId.first()
        if (rootFolderId.isNullOrBlank()) return DriveResult.ConfigurationError
        return driveService.listJsonFiles(rootFolderId)
    }

    suspend fun getFileContent(fileId: String): DriveResult<VectorList> =
        driveService.getFileContent(fileId)

    suspend fun createFile(fileName: String, jsonContent: String): DriveResult<DriveFileInfo> {
        val rootFolderId = preferenceManager.googleDriveFolderId.first()
        if (rootFolderId.isNullOrBlank()) return DriveResult.ConfigurationError
        return driveService.uploadJsonFile(rootFolderId, fileName, jsonContent)
    }

    suspend fun deleteFile(fileId: String): DriveResult<Unit> =
        driveService.deleteFile(fileId)

    suspend fun saveFileContent(fileId: String, list: VectorList): DriveResult<Unit> {
        val export = list.copy(
            id = "",
            items = list.items.map { it.copy(id = "") }
        )
        val json = prettyJson.encodeToString(export)
        return driveService.overwriteFileContent(fileId, json)
    }
}
