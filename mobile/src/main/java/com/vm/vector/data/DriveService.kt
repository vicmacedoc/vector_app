package com.vm.vector.data

import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.vm.core.models.VectorList
import java.nio.charset.StandardCharsets
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.InputStreamReader

private const val TAG = "DriveService"
private const val TAG_DISCOVERY = "VectorDriveDiscovery"
private const val APP_NAME = "Vector"

/**
 * Drive client. Root ID in DataStore points to the 'Data' folder.
 * Discovery queries: mimeType = 'application/vnd.google-apps.folder' and name = 'X' and 'ROOT_ID' in parents.
 */
class DriveService(
    private val context: android.content.Context,
    private val authManager: GoogleDriveAuthManager,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val driveScopes = listOf(DriveScopes.DRIVE)

    private fun buildDrive(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, driveScopes)
            .apply { selectedAccountName = account.email }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName(APP_NAME)
            .build()
    }

    /**
     * Validates Root ID is accessible, runs discovery for 'lists' and 'presets',
     * logs resulting IDs to Logcat. Returns clear errors if a folder is not found.
     */
    suspend fun validateConnection(rootFolderId: String): ValidateConnectionResult = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
        if (account == null) {
            Log.w(TAG, "validateConnection: No signed-in account")
            return@withContext ValidateConnectionResult.Unauthorized
        }
        try {
            val drive = buildDrive(account)
            if (!validateRoot(drive, rootFolderId)) {
                Log.w(TAG, "validateConnection: Root not accessible id=$rootFolderId")
                return@withContext ValidateConnectionResult.RootInaccessible
            }
            val listsId = discoverSubfolder(drive, rootFolderId, DriveConfig.LISTS_SUBFOLDER_NAME)
            val presetsId = discoverSubfolder(drive, rootFolderId, DriveConfig.PRESETS_SUBFOLDER_NAME)
            Log.i(TAG_DISCOVERY, "lists folder ID: $listsId")
            Log.i(TAG_DISCOVERY, "presets folder ID: $presetsId")
            when {
                listsId == null -> {
                    Log.w(TAG, "validateConnection: 'lists' not found under root=$rootFolderId")
                    return@withContext ValidateConnectionResult.ListsNotFound
                }
                presetsId == null -> {
                    Log.w(TAG, "validateConnection: 'presets' not found under root=$rootFolderId")
                    return@withContext ValidateConnectionResult.PresetsNotFound
                }
                else -> return@withContext ValidateConnectionResult.Success(listsId, presetsId)
            }
        } catch (e: Exception) {
            val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
            if (recoverable != null) {
                Log.w(TAG, "validateConnection: NeedRemoteConsent", e)
                return@withContext ValidateConnectionResult.NeedsRemoteConsent(recoverable.intent)
            }
            Log.e(TAG, "validateConnection", e)
            return@withContext ValidateConnectionResult.OtherError(e.message ?: "Unknown error")
        }
    }

    /**
     * Discovery query: mimeType = 'application/vnd.google-apps.folder' and name = '[name]' and 'ROOT_ID' in parents.
     */
    private fun discoverSubfolder(drive: Drive, rootId: String, name: String): String? {
        val q = "mimeType = 'application/vnd.google-apps.folder' and name = '$name' and '$rootId' in parents and trashed=false"
        val result = drive.files().list()
            .setQ(q)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
        return (result.files ?: emptyList()).firstOrNull()?.id
    }

    /**
     * Public method to discover a subfolder by name under root folder.
     */
    suspend fun discoverSubfolder(rootFolderId: String, folderName: String): String? = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
        if (account == null) {
            Log.w(TAG, "discoverSubfolder: No signed-in account")
            return@withContext null
        }
        try {
            val drive = buildDrive(account)
            if (!validateRoot(drive, rootFolderId)) {
                Log.w(TAG, "discoverSubfolder: Invalid root id=$rootFolderId")
                return@withContext null
            }
            return@withContext discoverSubfolder(drive, rootFolderId, folderName)
        } catch (e: Exception) {
            Log.e(TAG, "discoverSubfolder", e)
            null
        }
    }

    /**
     * Creates a folder if it doesn't exist, or returns existing folder ID.
     */
    suspend fun discoverOrCreateFolder(rootFolderId: String, folderName: String): DriveResult<String> = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
        if (account == null) {
            Log.w(TAG, "discoverOrCreateFolder: No signed-in account")
            return@withContext DriveResult.Unauthorized
        }
        try {
            val drive = buildDrive(account)
            if (!validateRoot(drive, rootFolderId)) {
                Log.w(TAG, "discoverOrCreateFolder: Invalid root id=$rootFolderId")
                return@withContext DriveResult.ConfigurationError
            }
            // Try to discover existing folder
            val existingId = discoverSubfolder(drive, rootFolderId, folderName)
            if (existingId != null) {
                return@withContext DriveResult.Success(existingId)
            }
            // Create folder if it doesn't exist
            val folderMetadata = File().apply {
                setName(folderName)
                setMimeType("application/vnd.google-apps.folder")
                setParents(Collections.singletonList(rootFolderId))
            }
            val folder = drive.files().create(folderMetadata).setFields("id, name").execute()
            Log.i(TAG, "Created folder: ${folder.name} (id: ${folder.id})")
            return@withContext DriveResult.Success(folder.id)
        } catch (e: Exception) {
            val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
            if (recoverable != null) {
                Log.w(TAG, "discoverOrCreateFolder: NeedRemoteConsent", e)
                return@withContext DriveResult.NeedsRemoteConsent(recoverable.intent)
            }
            mapDriveException("discoverOrCreateFolder", e)
        }
    }

    /**
     * Uploads a JSON file to a specific folder (by folder ID).
     */
    suspend fun uploadJsonFileToFolder(folderId: String, fileName: String, jsonContent: String): DriveResult<DriveFileInfo> =
        withContext(Dispatchers.IO) {
            val account = authManager.getLastSignedInAccount()
            if (account == null) {
                Log.w(TAG, "uploadJsonFileToFolder: No signed-in account")
                return@withContext DriveResult.Unauthorized
            }
            try {
                val drive = buildDrive(account)
                val name = if (fileName.endsWith(".json")) fileName else "$fileName.json"
                val metadata = File().apply {
                    setName(name)
                    setParents(Collections.singletonList(folderId))
                }
                val content = ByteArrayContent("application/json", jsonContent.toByteArray(StandardCharsets.UTF_8))
                val file = drive.files().create(metadata, content).setFields("id, name").execute()
                return@withContext DriveResult.Success(DriveFileInfo(file.id, file.name))
            } catch (e: Exception) {
                val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
                if (recoverable != null) {
                    Log.w(TAG, "uploadJsonFileToFolder: NeedRemoteConsent", e)
                    return@withContext DriveResult.NeedsRemoteConsent(recoverable.intent)
                }
                mapDriveException("uploadJsonFileToFolder", e)
            }
        }

    /**
     * Validates root, discovers 'lists', fetches all .json files from the discovered lists folder.
     */
    suspend fun listJsonFiles(rootFolderId: String): DriveResult<List<DriveFileInfo>> = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
        if (account == null) {
            Log.w(TAG, "listJsonFiles: No signed-in account")
            return@withContext DriveResult.Unauthorized
        }
        try {
            val drive = buildDrive(account)
            if (!validateRoot(drive, rootFolderId)) {
                Log.w(TAG, "listJsonFiles: Invalid root id=$rootFolderId")
                return@withContext DriveResult.ConfigurationError
            }
            val listsId = discoverSubfolder(drive, rootFolderId, DriveConfig.LISTS_SUBFOLDER_NAME)
                ?: run {
                    Log.w(TAG, "listJsonFiles: lists not found under root=$rootFolderId")
                    return@withContext DriveResult.ListsFolderNotFound
                }
            val files = drive.files().list()
                .setQ("'$listsId' in parents and (mimeType='application/json' or name contains '.${DriveConfig.LIST_FILE_EXTENSION}') and trashed=false")
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            val list = (files.files ?: emptyList()).map { DriveFileInfo(it.id, it.name) }
            return@withContext DriveResult.Success(list)
        } catch (e: Exception) {
            val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
            if (recoverable != null) {
                Log.w(TAG, "listJsonFiles: NeedRemoteConsent", e)
                return@withContext DriveResult.NeedsRemoteConsent(recoverable.intent)
            }
            mapDriveException("listJsonFiles", e)
        }
    }

    suspend fun getFileContent(fileId: String): DriveResult<VectorList> = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
        if (account == null) {
            Log.w(TAG, "getFileContent: No signed-in account")
            return@withContext DriveResult.Unauthorized
        }
        try {
            val drive = buildDrive(account)
            val inputStream: InputStream = drive.files().get(fileId).setAlt("media").executeMediaAsInputStream()
            val raw = InputStreamReader(inputStream).use { it.readText() }
            val list = json.decodeFromString<VectorList>(raw)
            return@withContext DriveResult.Success(list)
        } catch (e: Exception) {
            val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
            if (recoverable != null) {
                Log.w(TAG, "getFileContent: NeedRemoteConsent", e)
                return@withContext DriveResult.NeedsRemoteConsent(recoverable.intent)
            }
            mapDriveException("getFileContent", e)
        }
    }

    /**
     * Creates a new JSON file in the 'lists' folder. Handles [UserRecoverableAuthIOException]
     * by returning [DriveResult.NeedsRemoteConsent] so the UI can launch the system consent dialog.
     */
    suspend fun uploadJsonFile(rootFolderId: String, fileName: String, jsonContent: String): DriveResult<DriveFileInfo> =
        withContext(Dispatchers.IO) {
            val account = authManager.getLastSignedInAccount()
            if (account == null) {
                Log.w(TAG, "uploadJsonFile: No signed-in account")
                return@withContext DriveResult.Unauthorized
            }
            try {
                val drive = buildDrive(account)
                if (!validateRoot(drive, rootFolderId)) {
                    Log.w(TAG, "uploadJsonFile: Invalid root id=$rootFolderId")
                    return@withContext DriveResult.ConfigurationError
                }
                val listsId = discoverSubfolder(drive, rootFolderId, DriveConfig.LISTS_SUBFOLDER_NAME)
                    ?: run {
                        Log.w(TAG, "uploadJsonFile: lists not found under root=$rootFolderId")
                        return@withContext DriveResult.ListsFolderNotFound
                    }
                val name = if (fileName.endsWith(".json")) fileName else "$fileName.json"
                val metadata = File().apply {
                    setName(name)
                    setParents(Collections.singletonList(listsId))
                }
                val content = ByteArrayContent("application/json", jsonContent.toByteArray(StandardCharsets.UTF_8))
                val file = drive.files().create(metadata, content).setFields("id, name").execute()
                return@withContext DriveResult.Success(DriveFileInfo(file.id, file.name))
            } catch (e: Exception) {
                val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
                if (recoverable != null) {
                    Log.w(TAG, "uploadJsonFile: NeedRemoteConsent", e)
                    return@withContext DriveResult.NeedsRemoteConsent(recoverable.intent)
                }
                mapDriveException("uploadJsonFile", e)
            }
        }

    /**
     * Deletes a file from Drive. Handles [UserRecoverableAuthIOException] via [DriveResult.NeedsRemoteConsent].
     */
    suspend fun deleteFile(fileId: String): DriveResult<Unit> = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
        if (account == null) {
            Log.w(TAG, "deleteFile: No signed-in account")
            return@withContext DriveResult.Unauthorized
        }
        try {
            val drive = buildDrive(account)
            drive.files().delete(fileId).execute()
            return@withContext DriveResult.Success(Unit)
        } catch (e: Exception) {
            val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
            if (recoverable != null) {
                Log.w(TAG, "deleteFile: NeedRemoteConsent", e)
                return@withContext DriveResult.NeedsRemoteConsent(recoverable.intent)
            }
            mapDriveException("deleteFile", e)
        }
    }

    /**
     * Overwrites file content with pretty-printed JSON. Handles [UserRecoverableAuthIOException]
     * via [DriveResult.NeedsRemoteConsent].
     */
    suspend fun overwriteFileContent(fileId: String, jsonContent: String): DriveResult<Unit> = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
        if (account == null) {
            Log.w(TAG, "overwriteFileContent: No signed-in account")
            return@withContext DriveResult.Unauthorized
        }
        try {
            val drive = buildDrive(account)
            val content = ByteArrayContent("application/json", jsonContent.toByteArray(StandardCharsets.UTF_8))
            drive.files().update(fileId, null, content).execute()
            return@withContext DriveResult.Success(Unit)
        } catch (e: Exception) {
            val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
            if (recoverable != null) {
                Log.w(TAG, "overwriteFileContent: NeedRemoteConsent", e)
                return@withContext DriveResult.NeedsRemoteConsent(recoverable.intent)
            }
            mapDriveException("overwriteFileContent", e)
        }
    }

    /**
     * Fetches [DriveConfig.DIET_WEEKLY_FILENAME] from the discovered 'presets' folder.
     */
    suspend fun getDietWeekly(rootFolderId: String): DriveResult<String> = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
        if (account == null) {
            Log.w(TAG, "getDietWeekly: No signed-in account")
            return@withContext DriveResult.Unauthorized
        }
        try {
            val drive = buildDrive(account)
            if (!validateRoot(drive, rootFolderId)) {
                Log.w(TAG, "getDietWeekly: Invalid root id=$rootFolderId")
                return@withContext DriveResult.ConfigurationError
            }
            val presetsId = discoverSubfolder(drive, rootFolderId, DriveConfig.PRESETS_SUBFOLDER_NAME)
                ?: run {
                    Log.w(TAG, "getDietWeekly: presets not found under root=$rootFolderId")
                    return@withContext DriveResult.ListsFolderNotFound
                }
            val q = "'$presetsId' in parents and name = '${DriveConfig.DIET_WEEKLY_FILENAME}' and trashed=false"
            val result = drive.files().list()
                .setQ(q)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            val file = (result.files ?: emptyList()).firstOrNull()
                ?: run {
                    Log.w(TAG, "getDietWeekly: ${DriveConfig.DIET_WEEKLY_FILENAME} not found in presets")
                    return@withContext DriveResult.ListsFolderNotFound
                }
            val inputStream = drive.files().get(file.id).setAlt("media").executeMediaAsInputStream()
            val raw = InputStreamReader(inputStream).use { it.readText() }
            return@withContext DriveResult.Success(raw)
        } catch (e: Exception) {
            val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
            if (recoverable != null) {
                Log.w(TAG, "getDietWeekly: NeedRemoteConsent", e)
                return@withContext DriveResult.NeedsRemoteConsent(recoverable.intent)
            }
            mapDriveException("getDietWeekly", e)
        }
    }

    /**
     * Fetches [DriveConfig.ROUTINE_WEEKLY_FILENAME] from the discovered 'presets' folder.
     */
    suspend fun getRoutineWeekly(rootFolderId: String): DriveResult<String> = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
        if (account == null) {
            Log.w(TAG, "getRoutineWeekly: No signed-in account")
            return@withContext DriveResult.Unauthorized
        }
        try {
            val drive = buildDrive(account)
            if (!validateRoot(drive, rootFolderId)) {
                Log.w(TAG, "getRoutineWeekly: Invalid root id=$rootFolderId")
                return@withContext DriveResult.ConfigurationError
            }
            val presetsId = discoverSubfolder(drive, rootFolderId, DriveConfig.PRESETS_SUBFOLDER_NAME)
                ?: run {
                    Log.w(TAG, "getRoutineWeekly: presets not found under root=$rootFolderId")
                    return@withContext DriveResult.ListsFolderNotFound
                }
            val q = "'$presetsId' in parents and name = '${DriveConfig.ROUTINE_WEEKLY_FILENAME}' and trashed=false"
            val result = drive.files().list()
                .setQ(q)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            val file = (result.files ?: emptyList()).firstOrNull()
                ?: run {
                    Log.w(TAG, "getRoutineWeekly: routine_weekly.json not found in presets")
                    return@withContext DriveResult.ListsFolderNotFound
                }
            val inputStream = drive.files().get(file.id).setAlt("media").executeMediaAsInputStream()
            val raw = InputStreamReader(inputStream).use { it.readText() }
            return@withContext DriveResult.Success(raw)
        } catch (e: Exception) {
            val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
            if (recoverable != null) {
                Log.w(TAG, "getRoutineWeekly: NeedRemoteConsent", e)
                return@withContext DriveResult.NeedsRemoteConsent(recoverable.intent)
            }
            mapDriveException("getRoutineWeekly", e)
        }
    }

    /**
     * Finds a file by name in a folder and returns its ID.
     */
    suspend fun findFileInFolder(folderId: String, fileName: String): DriveResult<DriveFileInfo> = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
        if (account == null) {
            Log.w(TAG, "findFileInFolder: No signed-in account")
            return@withContext DriveResult.Unauthorized
        }
        try {
            val drive = buildDrive(account)
            val q = "'$folderId' in parents and name = '$fileName' and trashed=false"
            val result = drive.files().list()
                .setQ(q)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            val file = (result.files ?: emptyList()).firstOrNull()
                ?: return@withContext DriveResult.ConfigurationError
            return@withContext DriveResult.Success(DriveFileInfo(file.id, file.name))
        } catch (e: Exception) {
            val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
            if (recoverable != null) {
                Log.w(TAG, "findFileInFolder: NeedRemoteConsent", e)
                return@withContext DriveResult.NeedsRemoteConsent(recoverable.intent)
            }
            mapDriveException("findFileInFolder", e)
        }
    }

    /**
     * Gets file content as string from a file in a folder by name.
     */
    suspend fun getFileContentFromFolder(folderId: String, fileName: String): DriveResult<String> = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
        if (account == null) {
            Log.w(TAG, "getFileContentFromFolder: No signed-in account")
            return@withContext DriveResult.Unauthorized
        }
        try {
            val drive = buildDrive(account)
            val q = "'$folderId' in parents and name = '$fileName' and trashed=false"
            val result = drive.files().list()
                .setQ(q)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            val file = (result.files ?: emptyList()).firstOrNull()
                ?: return@withContext DriveResult.ConfigurationError
            val inputStream = drive.files().get(file.id).setAlt("media").executeMediaAsInputStream()
            val raw = InputStreamReader(inputStream).use { it.readText() }
            return@withContext DriveResult.Success(raw)
        } catch (e: Exception) {
            val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
            if (recoverable != null) {
                Log.w(TAG, "getFileContentFromFolder: NeedRemoteConsent", e)
                return@withContext DriveResult.NeedsRemoteConsent(recoverable.intent)
            }
            mapDriveException("getFileContentFromFolder", e)
        }
    }

    private fun validateRoot(drive: Drive, rootFolderId: String): Boolean {
        return try {
            drive.files().get(rootFolderId).setFields("id").execute()
            true
        } catch (e: Exception) {
            val gje = e as? com.google.api.client.googleapis.json.GoogleJsonResponseException
                ?: e.cause as? com.google.api.client.googleapis.json.GoogleJsonResponseException
            if (gje?.statusCode == 404) {
                Log.w(TAG, "validateRoot: Root not found id=$rootFolderId")
                false
            } else throw e
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> mapDriveException(op: String, e: Exception): DriveResult<T> {
        val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
        if (recoverable != null) {
            Log.w(TAG, "Drive API $op: NeedRemoteConsent", e)
            return DriveResult.NeedsRemoteConsent(recoverable.intent) as DriveResult<T>
        }
        Log.e(TAG, "Drive API error in $op", e)
        val gje = e as? com.google.api.client.googleapis.json.GoogleJsonResponseException
            ?: e.cause as? com.google.api.client.googleapis.json.GoogleJsonResponseException
        val status = gje?.statusCode
        return when {
            status == 401 -> DriveResult.Unauthorized
            status == 404 -> DriveResult.ConfigurationError
            else -> DriveResult.OtherError(e.message ?: "Unknown error")
        }
    }
}
