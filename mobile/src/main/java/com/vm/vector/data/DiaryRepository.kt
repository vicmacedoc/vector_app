package com.vm.vector.data

import android.content.Context
import androidx.room.Room
import com.vm.core.models.DiaryCollection
import com.vm.core.models.DiaryCollectionImage
import com.vm.core.models.DiaryEntry
import com.vm.core.models.VectorDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.*

class DiaryRepository(
    private val context: Context,
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
) {
    private val database: VectorDatabase = Room.databaseBuilder(
        context,
        VectorDatabase::class.java,
        "vector_database"
    )
        .fallbackToDestructiveMigration()
        .build()

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
        val folderName = sanitizeCollectionFolderName(date, name)
        val folderResult = driveService.discoverOrCreateFolder(collectionsId, folderName)
        val driveFolderId = when (folderResult) {
            is DriveResult.Success -> folderResult.data
            is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Drive consent needed"))
            else -> return Result.failure(Exception("Could not create collection folder"))
        }
        val id = "col_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val collection = DiaryCollection(id = id, date = date, name = name, driveFolderId = driveFolderId)
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

    suspend fun deleteCollection(collection: DiaryCollection): Result<Unit> {
        val driveFolderId = collection.driveFolderId
        if (driveFolderId != null) {
            val deleteResult = driveService.deleteFolderAndContents(driveFolderId)
            when (deleteResult) {
                is DriveResult.Success -> { /* ok */ }
                is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Drive consent needed"))
                else -> return Result.failure(Exception("Failed to delete folder from Drive"))
            }
        }
        diaryCollectionImageDao.deleteByCollectionId(collection.id)
        diaryCollectionDao.deleteById(collection.id)
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

    private fun sanitizeCollectionFolderName(date: String, name: String): String {
        val safe = name.replace(Regex("[^a-zA-Z0-9 _-]"), "_").take(80).trim()
        return "${date}_${if (safe.isBlank()) "collection" else safe}"
    }
}
