package com.vm.vector.data

import android.content.Context
import com.vm.core.models.DiaryCollection
import com.vm.core.models.DiaryCollectionImage
import com.vm.core.models.DiaryEntry
import com.vm.core.models.VectorDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.*

class DiaryRepository(
    private val context: Context,
    private val database: VectorDatabase,
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
) {
    private val diaryEntryDao = database.diaryEntryDao()
    private val diaryCollectionDao = database.diaryCollectionDao()
    private val diaryCollectionImageDao = database.diaryCollectionImageDao()

    // ---- Diary entry (mood + journal) ----

    fun getDiaryEntryByDate(date: String): Flow<DiaryEntry?> = diaryEntryDao.getByDate(date)

    suspend fun getDiaryEntryByDateSync(date: String): DiaryEntry? = diaryEntryDao.getByDateSync(date)

    suspend fun saveDiaryEntry(entry: DiaryEntry) {
        diaryEntryDao.insert(entry)
    }

    // ---- Collections ----

    fun getCollectionsByDate(date: String): Flow<List<DiaryCollection>> =
        diaryCollectionDao.getByDate(date)

    suspend fun getCollectionsByDateSync(date: String): List<DiaryCollection> =
        diaryCollectionDao.getByDateSync(date)

    fun getImagesByCollectionId(collectionId: String): Flow<List<DiaryCollectionImage>> =
        diaryCollectionImageDao.getByCollectionId(collectionId)

    suspend fun getImagesByCollectionIdSync(collectionId: String): List<DiaryCollectionImage> =
        diaryCollectionImageDao.getByCollectionIdSync(collectionId)

    /** Creates a new collection: creates folder in Drive under collections/{date}_{sanitizedName}, then saves to DB. */
    suspend fun createCollection(date: String, name: String): Result<DiaryCollection> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return Result.failure(Exception("Collection name cannot be empty"))
        val existing = diaryCollectionDao.getByDateSync(date)
        if (existing.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            return Result.failure(Exception("A collection with this name already exists for this date"))
        }
        val rootFolderId = preferenceManager.googleDriveFolderId.first() ?: ""
        if (rootFolderId.isBlank()) {
            return Result.failure(Exception("Drive not configured"))
        }
        val collectionsResult = driveService.discoverOrCreateCollectionsFolder(rootFolderId)
        val collectionsId = when (collectionsResult) {
            is DriveResult.Success -> collectionsResult.data
            is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Drive consent needed"))
            else -> return Result.failure(Exception("Could not get collections folder"))
        }
        val folderName = sanitizeCollectionFolderName(date, trimmedName)
        val folderResult = driveService.discoverOrCreateFolder(collectionsId, folderName)
        val driveFolderId = when (folderResult) {
            is DriveResult.Success -> folderResult.data
            is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Drive consent needed"))
            else -> return Result.failure(Exception("Could not create collection folder"))
        }
        val id = "col_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val collection = DiaryCollection(id = id, date = date, name = trimmedName, driveFolderId = driveFolderId)
        diaryCollectionDao.insert(collection)
        return Result.success(collection)
    }

    suspend fun addImageToCollection(
        collectionId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        takenAtMillis: Long = System.currentTimeMillis()
    ): Result<DiaryCollectionImage> {
        val collection = diaryCollectionDao.getByIdSync(collectionId) ?: return Result.failure(Exception("Collection not found"))
        val driveFolderId = collection.driveFolderId ?: return Result.failure(Exception("Collection has no Drive folder"))
        val rootFolderId = preferenceManager.googleDriveFolderId.first() ?: ""
        if (rootFolderId.isBlank()) return Result.failure(Exception("Drive not configured"))
        val uploadResult = driveService.uploadBinaryToFolder(driveFolderId, fileName, mimeType, bytes)
        val fileInfo = when (uploadResult) {
            is DriveResult.Success -> uploadResult.data
            is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Drive consent needed"))
            else -> return Result.failure(Exception("Upload failed"))
        }
        val id = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val image = DiaryCollectionImage(
            id = id,
            collectionId = collectionId,
            driveFileId = fileInfo.id,
            fileName = fileInfo.name,
            takenAtMillis = takenAtMillis
        )
        diaryCollectionImageDao.insert(image)
        return Result.success(image)
    }

    suspend fun deleteImage(image: DiaryCollectionImage): Result<Unit> {
        val deleteResult = driveService.deleteFile(image.driveFileId)
        when (deleteResult) {
            is DriveResult.Success -> { /* ok */ }
            is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Drive consent needed"))
            else -> return Result.failure(Exception("Failed to delete file from Drive"))
        }
        diaryCollectionImageDao.deleteById(image.id)
        return Result.success(Unit)
    }

    /** Deletes collection locally first so the UI updates; then best-effort delete from Drive. */
    suspend fun deleteCollection(collection: DiaryCollection): Result<Unit> {
        diaryCollectionImageDao.deleteByCollectionId(collection.id)
        diaryCollectionDao.deleteById(collection.id)
        collection.driveFolderId?.let { folderId ->
            when (driveService.deleteFolderAndContents(folderId)) {
                is DriveResult.Success -> { /* cleaned up on Drive */ }
                is DriveResult.NeedsRemoteConsent,
                is DriveResult.Unauthorized,
                is DriveResult.ConfigurationError,
                is DriveResult.OtherError -> { /* best-effort: collection already removed locally */ }
                else -> { }
            }
        }
        return Result.success(Unit)
    }

    /** Loads all collections for a date with their images (sync). */
    suspend fun getCollectionsWithImagesSync(date: String): List<Pair<DiaryCollection, List<DiaryCollectionImage>>> {
        val collections = diaryCollectionDao.getByDateSync(date)
        return collections.map { col ->
            col to diaryCollectionImageDao.getByCollectionIdSync(col.id)
        }
    }

    /** Downloads image bytes from Drive for display. Returns null on failure. */
    suspend fun getImageBytes(driveFileId: String): ByteArray? {
        return when (val r = driveService.downloadFileBytes(driveFileId)) {
            is DriveResult.Success -> r.data
            else -> null
        }
    }

    /** Uploads journal audio from a local relative path (e.g. diary_audio/yyyy-MM-dd.3gp). Returns the Drive file ID on success. */
    suspend fun uploadDiaryAudioFromPath(date: String, localRelativePath: String): Result<String> {
        val file = File(context.filesDir, localRelativePath)
        if (!file.exists()) return Result.failure(Exception("Audio file not found"))
        val bytes = file.readBytes()
        return uploadDiaryAudio(date, bytes)
    }

    /** Uploads journal audio to Drive under diary_audio/{date}.3gp. Returns the Drive file ID on success. */
    suspend fun uploadDiaryAudio(date: String, audioBytes: ByteArray): Result<String> {
        val rootFolderId = preferenceManager.googleDriveFolderId.first() ?: ""
        if (rootFolderId.isBlank()) return Result.failure(Exception("Drive not configured"))
        val folderResult = driveService.discoverOrCreateFolder(rootFolderId, "diary_audio")
        val folderId = when (folderResult) {
            is DriveResult.Success -> folderResult.data
            is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Drive consent needed"))
            else -> return Result.failure(Exception("Could not get diary_audio folder"))
        }
        val fileName = "$date.3gp"
        val uploadResult = driveService.uploadBinaryToFolder(folderId, fileName, "audio/3gpp", audioBytes)
        return when (uploadResult) {
            is DriveResult.Success -> Result.success(uploadResult.data.id)
            is DriveResult.NeedsRemoteConsent -> Result.failure(Exception("Drive consent needed"))
            else -> Result.failure(Exception("Upload failed"))
        }
    }

    /** Downloads journal audio bytes from Drive for playback. Returns null on failure. */
    suspend fun getDiaryAudioBytes(driveFileId: String): ByteArray? = getImageBytes(driveFileId)

    private fun sanitizeCollectionFolderName(date: String, name: String): String {
        val safe = name.replace(Regex("[^a-zA-Z0-9 _-]"), "_").take(80).trim()
        return "${date}_${if (safe.isBlank()) "collection" else safe}"
    }
}
