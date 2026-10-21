package com.vm.vector.data

import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.vm.core.models.VectorList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.io.File as JavaFile

private const val TAG = "DriveService"
private const val APP_NAME = "Vector"
private const val FOLDER_MIME = "application/vnd.google-apps.folder"

/**
 * Drive client. Root ID in DataStore points to the 'Data' folder.
 * Every public call goes through [call], which resolves the account, maps consent/auth/HTTP errors
 * to [DriveResult], and runs on [Dispatchers.IO].
 */
class DriveService(
    private val context: android.content.Context,
    private val authManager: GoogleDriveAuthManager,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun buildDrive(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE))
            .apply { selectedAccountName = account.email }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName(APP_NAME)
            .build()
    }

    private suspend fun <T> call(op: String, block: (Drive) -> DriveResult<T>): DriveResult<T> =
        withContext(Dispatchers.IO) {
            val account = authManager.getLastSignedInAccount()
            if (account == null) {
                Log.w(TAG, "$op: No signed-in account")
                return@withContext DriveResult.Unauthorized
            }
            try {
                block(buildDrive(account))
            } catch (e: Exception) {
                mapDriveException(op, e)
            }
        }

    /** Validates the root and makes sure the folders the app writes to exist. */
    suspend fun validateConnection(rootFolderId: String): ValidateConnectionResult {
        val result = call("validateConnection") { drive ->
            if (!validateRoot(drive, rootFolderId)) return@call DriveResult.ConfigurationError
            val brain = ensureFolder(drive, rootFolderId, DriveConfig.BRAIN_SUBFOLDER_NAME)
            ensureFolder(drive, rootFolderId, DriveConfig.DATABASE_SUBFOLDER_NAME)
            val backups = ensureFolder(drive, rootFolderId, DriveConfig.BACKUPS_SUBFOLDER_NAME)
            DriveResult.Success(brain to backups)
        }
        return when (result) {
            is DriveResult.Success -> ValidateConnectionResult.Success(result.data.first, result.data.second)
            is DriveResult.NeedsRemoteConsent -> ValidateConnectionResult.NeedsRemoteConsent(result.intent)
            is DriveResult.Unauthorized -> ValidateConnectionResult.Unauthorized
            is DriveResult.ConfigurationError -> ValidateConnectionResult.RootInaccessible
            is DriveResult.ListsFolderNotFound -> ValidateConnectionResult.RootInaccessible
            is DriveResult.OtherError -> ValidateConnectionResult.OtherError(result.message)
        }
    }

    /** Finds a subfolder by name under [rootFolderId] without creating it. */
    suspend fun discoverSubfolder(rootFolderId: String, folderName: String): String? =
        (call("discoverSubfolder") { drive ->
            val id = findFolder(drive, rootFolderId, folderName)
            if (id != null) DriveResult.Success(id) else DriveResult.ConfigurationError
        } as? DriveResult.Success)?.data

    /** Returns the ID of [folderName] under [parentId], creating it if needed. */
    suspend fun discoverOrCreateFolder(parentId: String, folderName: String): DriveResult<String> =
        call("discoverOrCreateFolder") { drive -> DriveResult.Success(ensureFolder(drive, parentId, folderName)) }

    /** Walks/creates `a/b/c` under [rootId]. */
    suspend fun discoverOrCreatePath(rootId: String, relativePath: String): DriveResult<String> =
        call("discoverOrCreatePath") { drive ->
            var current = rootId
            for (part in relativePath.split('/').filter { it.isNotBlank() }) {
                current = ensureFolder(drive, current, part)
            }
            DriveResult.Success(current)
        }

    suspend fun discoverOrCreateCollectionsFolder(rootFolderId: String): DriveResult<String> =
        discoverOrCreateFolder(rootFolderId, DriveConfig.COLLECTIONS_SUBFOLDER_NAME)

    suspend fun discoverOrCreateBrainFolder(rootFolderId: String): DriveResult<String> =
        discoverOrCreateFolder(rootFolderId, DriveConfig.BRAIN_SUBFOLDER_NAME)

    suspend fun uploadJsonFileToFolder(folderId: String, fileName: String, jsonContent: String): DriveResult<DriveFileInfo> =
        call("uploadJsonFileToFolder") { drive ->
            val name = if (fileName.endsWith(".json")) fileName else "$fileName.json"
            val metadata = File().setName(name).setParents(listOf(folderId))
            val content = ByteArrayContent("application/json", jsonContent.toByteArray(StandardCharsets.UTF_8))
            val file = drive.files().create(metadata, content).setFields("id, name").execute()
            DriveResult.Success(DriveFileInfo(file.id, file.name))
        }

    /** Reads a [VectorList] JSON file, filling blank IDs. */
    suspend fun getFileContent(fileId: String): DriveResult<VectorList> = call("getFileContent") { drive ->
        val raw = readText(drive, fileId)
        val baseTime = System.currentTimeMillis()
        val list = json.decodeFromString<VectorList>(raw).let { list ->
            list.copy(
                id = list.id.ifBlank { "list_$baseTime" },
                items = list.items.mapIndexed { i, item -> item.copy(id = item.id.ifBlank { "item_${baseTime}_$i" }) },
            )
        }
        DriveResult.Success(list)
    }

    suspend fun getTextContent(fileId: String): DriveResult<String> =
        call("getTextContent") { drive -> DriveResult.Success(readText(drive, fileId)) }

    /** Moves a file to Drive's trash (recoverable for 30 days) instead of deleting it permanently. */
    suspend fun deleteFile(fileId: String): DriveResult<Unit> = call("deleteFile") { drive ->
        drive.files().update(fileId, File().setTrashed(true)).setFields("id").execute()
        DriveResult.Success(Unit)
    }

    /** Re-parents a file into [newParentId], removing it from its current folders. */
    suspend fun moveFile(fileId: String, newParentId: String): DriveResult<Unit> = call("moveFile") { drive ->
        val previousParents = drive.files().get(fileId).setFields("parents").execute()
            .parents.orEmpty().joinToString(",")
        drive.files().update(fileId, File())
            .setAddParents(newParentId)
            .setRemoveParents(previousParents)
            .setFields("id, parents")
            .execute()
        DriveResult.Success(Unit)
    }

    suspend fun uploadBinaryToFolder(
        folderId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): DriveResult<DriveFileInfo> = call("uploadBinaryToFolder") { drive ->
        val metadata = File().setName(fileName).setParents(listOf(folderId)).setMimeType(mimeType)
        val file = drive.files().create(metadata, ByteArrayContent(mimeType, bytes)).setFields("id, name").execute()
        DriveResult.Success(DriveFileInfo(file.id, file.name))
    }

    /** Streams a local file into a Drive folder (suitable for large audio/video). */
    suspend fun uploadFileToFolder(
        folderId: String,
        fileName: String,
        mimeType: String,
        localFile: JavaFile,
    ): DriveResult<DriveFileInfo> {
        if (!localFile.isFile) return DriveResult.OtherError("Local file not found: ${localFile.absolutePath}")
        return call("uploadFileToFolder") { drive ->
            val metadata = File().setName(fileName).setParents(listOf(folderId)).setMimeType(mimeType)
            val file = drive.files().create(metadata, FileContent(mimeType, localFile)).setFields("id, name").execute()
            DriveResult.Success(DriveFileInfo(file.id, file.name))
        }
    }

    suspend fun overwriteFileFromLocal(fileId: String, mimeType: String, localFile: JavaFile): DriveResult<Unit> {
        if (!localFile.isFile) return DriveResult.OtherError("Local file not found")
        return call("overwriteFileFromLocal") { drive ->
            drive.files().update(fileId, null, FileContent(mimeType, localFile)).execute()
            DriveResult.Success(Unit)
        }
    }

    /**
     * Writes [localFile] as [fileName] in [folderId]: updates the existing file in place (Drive keeps the
     * previous revision) or creates it. The old content is never removed before the new one is stored.
     */
    suspend fun uploadOrReplaceFile(
        folderId: String,
        fileName: String,
        mimeType: String,
        localFile: JavaFile,
    ): DriveResult<DriveFileInfo> {
        if (!localFile.isFile) return DriveResult.OtherError("Local file not found")
        return call("uploadOrReplaceFile") { drive ->
            val existing = findFile(drive, folderId, fileName)
            val content = FileContent(mimeType, localFile)
            val file = if (existing != null) {
                drive.files().update(existing, null, content).setFields("id, name").execute()
            } else {
                val metadata = File().setName(fileName).setParents(listOf(folderId)).setMimeType(mimeType)
                drive.files().create(metadata, content).setFields("id, name").execute()
            }
            DriveResult.Success(DriveFileInfo(file.id, file.name))
        }
    }

    /** Creates or updates a UTF-8 text file by name. Returns the file info with its new Drive version. */
    suspend fun writeTextFile(
        folderId: String,
        fileName: String,
        content: String,
        mimeType: String = "application/json",
    ): DriveResult<DriveFileMeta> = call("writeTextFile") { drive ->
        val bytes = ByteArrayContent(mimeType, content.toByteArray(StandardCharsets.UTF_8))
        val existing = findFile(drive, folderId, fileName)
        val file = if (existing != null) {
            drive.files().update(existing, null, bytes).setFields(META_FIELDS).execute()
        } else {
            val metadata = File().setName(fileName).setParents(listOf(folderId)).setMimeType(mimeType)
            drive.files().create(metadata, bytes).setFields(META_FIELDS).execute()
        }
        DriveResult.Success(file.toMeta())
    }

    suspend fun getFileMeta(fileId: String): DriveResult<DriveFileMeta> = call("getFileMeta") { drive ->
        DriveResult.Success(drive.files().get(fileId).setFields(META_FIELDS).execute().toMeta())
    }

    suspend fun findFileMeta(folderId: String, fileName: String): DriveResult<DriveFileMeta?> =
        call("findFileMeta") { drive ->
            val q = "'$folderId' in parents and name = '${escape(fileName)}' and trashed=false"
            val file = drive.files().list().setQ(q).setSpaces("drive")
                .setFields("files($META_FIELDS)").execute().files.orEmpty().firstOrNull()
            DriveResult.Success(file?.toMeta())
        }

    suspend fun downloadFileBytes(fileId: String): DriveResult<ByteArray> = call("downloadFileBytes") { drive ->
        val bytes = drive.files().get(fileId).setAlt("media").executeMediaAsInputStream().use { it.readBytes() }
        DriveResult.Success(bytes)
    }

    /** Streams a Drive file into [dest] without loading it into memory. */
    suspend fun downloadToFile(fileId: String, dest: JavaFile): DriveResult<Unit> = call("downloadToFile") { drive ->
        dest.parentFile?.mkdirs()
        drive.files().get(fileId).setAlt("media").executeMediaAsInputStream().use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        DriveResult.Success(Unit)
    }

    /** Lists direct children with metadata, following pagination. */
    suspend fun listChildrenWithMetadata(folderId: String): DriveResult<List<DriveListedFile>> =
        call("listChildrenWithMetadata") { drive ->
            val out = mutableListOf<DriveListedFile>()
            var pageToken: String? = null
            do {
                val result = drive.files().list()
                    .setQ("'$folderId' in parents and trashed=false")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, mimeType, modifiedTime, size)")
                    .setPageSize(1000)
                    .setPageToken(pageToken)
                    .execute()
                result.files.orEmpty().mapTo(out) { f ->
                    DriveListedFile(
                        id = f.id,
                        name = f.name,
                        mimeType = f.mimeType,
                        modifiedTimeRfc3339 = f.modifiedTime?.toStringRfc3339(),
                        sizeBytes = f.getSize(),
                    )
                }
                pageToken = result.nextPageToken
            } while (pageToken != null)
            DriveResult.Success(out)
        }

    /** Direct children with their Drive [DriveFileMeta.version]; one call tells which sync shards moved. */
    suspend fun listChildrenMeta(folderId: String): DriveResult<List<DriveFileMeta>> =
        call("listChildrenMeta") { drive ->
            val out = mutableListOf<DriveFileMeta>()
            var pageToken: String? = null
            do {
                val result = drive.files().list()
                    .setQ("'$folderId' in parents and trashed=false")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files($META_FIELDS)")
                    .setPageSize(1000)
                    .setPageToken(pageToken)
                    .execute()
                result.files.orEmpty().mapTo(out) { it.toMeta() }
                pageToken = result.nextPageToken
            } while (pageToken != null)
            DriveResult.Success(out)
        }

    suspend fun overwriteFileContent(fileId: String, jsonContent: String): DriveResult<Unit> =
        call("overwriteFileContent") { drive ->
            val content = ByteArrayContent("application/json", jsonContent.toByteArray(StandardCharsets.UTF_8))
            drive.files().update(fileId, null, content).execute()
            DriveResult.Success(Unit)
        }

    /** Finds a file by name in a folder. Returns [DriveResult.ConfigurationError] when it doesn't exist. */
    suspend fun findFileInFolder(folderId: String, fileName: String): DriveResult<DriveFileInfo> =
        call("findFileInFolder") { drive ->
            val id = findFile(drive, folderId, fileName) ?: return@call DriveResult.ConfigurationError
            DriveResult.Success(DriveFileInfo(id, fileName))
        }

    private fun validateRoot(drive: Drive, rootFolderId: String): Boolean = try {
        drive.files().get(rootFolderId).setFields("id").execute()
        true
    } catch (e: Exception) {
        if (statusOf(e) == 404) {
            Log.w(TAG, "validateRoot: Root not found id=$rootFolderId")
            false
        } else throw e
    }

    private fun findFolder(drive: Drive, parentId: String, name: String): String? {
        val key = "$parentId/$name"
        folderCache[key]?.let { return it }
        val q = "mimeType = '$FOLDER_MIME' and name = '${escape(name)}' and '$parentId' in parents and trashed=false"
        val id = drive.files().list().setQ(q).setSpaces("drive").setFields("files(id)")
            .execute().files.orEmpty().firstOrNull()?.id
        if (id != null) folderCache[key] = id
        return id
    }

    private fun ensureFolder(drive: Drive, parentId: String, name: String): String {
        findFolder(drive, parentId, name)?.let { return it }
        val metadata = File().setName(name).setMimeType(FOLDER_MIME).setParents(listOf(parentId))
        val folder = drive.files().create(metadata).setFields("id").execute()
        Log.i(TAG, "Created folder $name (id: ${folder.id})")
        folderCache["$parentId/$name"] = folder.id
        return folder.id
    }

    private fun findFile(drive: Drive, folderId: String, fileName: String): String? {
        val q = "'$folderId' in parents and name = '${escape(fileName)}' and trashed=false"
        return drive.files().list().setQ(q).setSpaces("drive").setFields("files(id)")
            .execute().files.orEmpty().firstOrNull()?.id
    }

    private fun readText(drive: Drive, fileId: String): String =
        drive.files().get(fileId).setAlt("media").executeMediaAsInputStream()
            .use { InputStreamReader(it, StandardCharsets.UTF_8).readText() }

    private fun escape(name: String) = name.replace("\\", "\\\\").replace("'", "\\'")

    private fun File.toMeta() = DriveFileMeta(
        id = id,
        name = name,
        version = getVersion() ?: 0L,
        modifiedTimeMillis = modifiedTime?.value ?: 0L,
        sizeBytes = getSize(),
    )

    private fun statusOf(e: Exception): Int? =
        ((e as? GoogleJsonResponseException) ?: (e.cause as? GoogleJsonResponseException))?.statusCode

    private fun <T> mapDriveException(op: String, e: Exception): DriveResult<T> {
        val recoverable = e as? UserRecoverableAuthIOException ?: e.cause as? UserRecoverableAuthIOException
        if (recoverable != null) {
            Log.w(TAG, "Drive API $op: NeedRemoteConsent", e)
            return DriveResult.NeedsRemoteConsent(recoverable.intent)
        }
        Log.e(TAG, "Drive API error in $op", e)
        return when (statusOf(e)) {
            401 -> DriveResult.Unauthorized
            404 -> {
                folderCache.clear()
                DriveResult.ConfigurationError
            }
            else -> DriveResult.OtherError(e.message ?: "Unknown error")
        }
    }

    companion object {
        private const val META_FIELDS = "id, name, version, modifiedTime, size"

        /** "parentId/name" -> folder ID, shared by every instance for the life of the process. */
        private val folderCache = ConcurrentHashMap<String, String>()
    }
}
