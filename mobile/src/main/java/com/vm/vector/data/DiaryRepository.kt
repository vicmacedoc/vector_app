package com.vm.vector.data

import android.content.Context
import com.vm.core.models.DiaryCollection
import com.vm.core.models.DiaryCollectionImage
import com.vm.core.models.DiaryEntry
import com.vm.core.models.VectorDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Summary of diary photos in one album (or globally). */
data class DiaryPhotoSummary(
    val totalCount: Int,
    val rangeStartMillis: Long,
    val rangeEndMillis: Long,
    val latestPreviewDriveFileIds: List<String>,
)

/** Result of a Drive ↔ Room sync for diary photos. */
data class DiaryDriveSyncResult(
    val importedCount: Int,
    val removedImagesCount: Int,
    val removedCollectionsCount: Int,
)

class DiaryRepository(
    private val context: Context,
    private val database: VectorDatabase,
    private val preferenceManager: PreferenceManager,
    private val driveService: DriveService,
) {
    private val diaryEntryDao = database.diaryEntryDao()
    private val diaryCollectionDao = database.diaryCollectionDao()
    private val diaryCollectionImageDao = database.diaryCollectionImageDao()

    private val dateFormatUtc = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    // ---- Diary entry (mood + journal) ----

    fun getDiaryEntryByDate(date: String): Flow<DiaryEntry?> = diaryEntryDao.getByDate(date)

    suspend fun getDiaryEntryByDateSync(date: String): DiaryEntry? = diaryEntryDao.getByDateSync(date)

    suspend fun saveDiaryEntry(entry: DiaryEntry) {
        diaryEntryDao.insert(entry)
    }

    // ---- Albums (each album = one subfolder under Drive `collections/`) ----

    fun getCollectionsByDate(date: String): Flow<List<DiaryCollection>> =
        diaryCollectionDao.getByDate(date)

    suspend fun getCollectionsByDateSync(date: String): List<DiaryCollection> =
        diaryCollectionDao.getByDateSync(date)

    fun getImagesByCollectionId(collectionId: String): Flow<List<DiaryCollectionImage>> =
        diaryCollectionImageDao.getByCollectionId(collectionId)

    suspend fun getImagesByCollectionIdSync(collectionId: String): List<DiaryCollectionImage> =
        diaryCollectionImageDao.getByCollectionIdSync(collectionId)

    suspend fun getAllCollectionImagesSync(): List<DiaryCollectionImage> =
        diaryCollectionImageDao.getAllSync()

    fun calendarDateForMillis(millis: Long): String = dateFormatUtc.format(Date(millis))

    fun buildPhotoSummary(images: List<DiaryCollectionImage>): DiaryPhotoSummary? {
        if (images.isEmpty()) return null
        val times = images.map { it.takenAtMillis }
        val latestIds = images
            .sortedByDescending { it.takenAtMillis }
            .take(4)
            .map { it.driveFileId }
        return DiaryPhotoSummary(
            totalCount = images.size,
            rangeStartMillis = times.minOrNull()!!,
            rangeEndMillis = times.maxOrNull()!!,
            latestPreviewDriveFileIds = latestIds,
        )
    }

    /** All albums (sorted by name) with images sorted by time. */
    suspend fun getAllCollectionsWithImagesSync(): List<Pair<DiaryCollection, List<DiaryCollectionImage>>> {
        val cols = diaryCollectionDao.getAllSync().sortedBy { it.name.lowercase(Locale.US) }
        return cols.map { col ->
            col to diaryCollectionImageDao.getByCollectionIdSync(col.id).sortedBy { it.takenAtMillis }
        }
    }

    /** @deprecated Use [getAllCollectionsWithImagesSync] for Diary UI. */
    suspend fun getCollectionsWithImagesSync(date: String): List<Pair<DiaryCollection, List<DiaryCollectionImage>>> {
        val collections = diaryCollectionDao.getByDateSync(date)
        return collections.map { col ->
            col to diaryCollectionImageDao.getByCollectionIdSync(col.id)
        }
    }

    /**
     * Creates a Drive subfolder under `collections/` named with the album title,
     * and saves the album row with [DiaryCollection.driveFolderId] set.
     */
    suspend fun createCollection(date: String, name: String): Result<DiaryCollection> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return Result.failure(Exception("Album name cannot be empty"))
        if (diaryCollectionDao.getAllSync().any { it.name.equals(trimmedName, ignoreCase = true) }) {
            return Result.failure(Exception("An album with this name already exists"))
        }
        val rootFolderId = preferenceManager.googleDriveFolderId.first() ?: ""
        if (rootFolderId.isBlank()) {
            return Result.failure(Exception("Drive not configured"))
        }
        val collectionsResult = driveService.discoverOrCreateCollectionsFolder(rootFolderId)
        val collectionsRootId = when (collectionsResult) {
            is DriveResult.Success -> collectionsResult.data
            is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Drive consent needed"))
            else -> return Result.failure(Exception("Could not access collections folder"))
        }
        val driveFolderName = sanitizeDriveFolderName(trimmedName)
        val folderResult = driveService.discoverOrCreateFolder(collectionsRootId, driveFolderName)
        val folderId = when (folderResult) {
            is DriveResult.Success -> folderResult.data
            is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Drive consent needed"))
            else -> return Result.failure(Exception("Could not create album folder"))
        }
        val id = "col_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val collection = DiaryCollection(id = id, date = date, name = trimmedName, driveFolderId = folderId)
        diaryCollectionDao.insert(collection)
        return Result.success(collection)
    }

    /**
     * Uploads an image into the album's Drive folder only.
     * Filename: `yyyy-MM-ddTHH-mm-ss-SSS.ext` (datetime only).
     */
    suspend fun addImageToCollection(
        collectionId: String,
        mimeType: String,
        bytes: ByteArray,
        takenAtMillis: Long = System.currentTimeMillis()
    ): Result<DiaryCollectionImage> {
        val collection = diaryCollectionDao.getByIdSync(collectionId)
            ?: return Result.failure(Exception("Album not found"))
        val driveFolderId = collection.driveFolderId
            ?: return Result.failure(Exception("Album has no Drive folder"))
        val ext = when {
            mimeType.contains("png", ignoreCase = true) -> "png"
            mimeType.contains("webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }
        val uploadMime = when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val fileName = buildDateTimeOnlyFileName(takenAtMillis, ext)
        val uploadResult = driveService.uploadBinaryToFolder(driveFolderId, fileName, uploadMime, bytes)
        val fileInfo = when (uploadResult) {
            is DriveResult.Success -> uploadResult.data
            is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Drive consent needed"))
            else -> return Result.failure(Exception("Upload failed"))
        }
        val id = stableImageRowId(fileInfo.id)
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

    /** Removes the album and its image rows locally only; Drive folder/files remain unless you delete them in Drive. */
    suspend fun deleteCollection(collection: DiaryCollection): Result<Unit> {
        diaryCollectionImageDao.deleteByCollectionId(collection.id)
        diaryCollectionDao.deleteById(collection.id)
        return Result.success(Unit)
    }

    suspend fun getImageBytes(driveFileId: String): ByteArray? {
        return when (val r = driveService.downloadFileBytes(driveFileId)) {
            is DriveResult.Success -> r.data
            else -> null
        }
    }

    /**
     * Syncs Drive `collections/`: each **direct subfolder** is an album; images use datetime-only names.
     * Imports new files, removes local rows for deleted files, removes albums whose folder was deleted on Drive.
     */
    suspend fun syncDiaryPhotosFromDrive(): Result<DiaryDriveSyncResult> {
        val rootFolderId = preferenceManager.googleDriveFolderId.first() ?: ""
        if (rootFolderId.isBlank()) return Result.failure(Exception("Drive not configured"))
        val collectionsRootResult = driveService.discoverOrCreateCollectionsFolder(rootFolderId)
        val collectionsRootId = when (collectionsRootResult) {
            is DriveResult.Success -> collectionsRootResult.data
            is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Drive consent needed"))
            else -> return Result.failure(Exception("Could not access collections folder"))
        }
        val childrenResult = driveService.listChildrenWithMetadata(collectionsRootId)
        val children = when (childrenResult) {
            is DriveResult.Success -> childrenResult.data
            is DriveResult.NeedsRemoteConsent -> return Result.failure(Exception("Drive consent needed"))
            else -> return Result.failure(Exception("Failed to list collections folder"))
        }
        val allImageFileIdsOnDrive = mutableSetOf<String>()
        val albumFolderIdsOnDrive = mutableSetOf<String>()
        val knownDriveIds = diaryCollectionImageDao.getAllSync().map { it.driveFileId }.toMutableSet()
        var added = 0
        for (child in children) {
            val mime = child.mimeType ?: ""
            if (mime != "application/vnd.google-apps.folder") continue
            albumFolderIdsOnDrive.add(child.id)
            val collection = findOrCreateAlbumFromDriveFolder(child.id, child.name)
            val name = child.name.trim().ifBlank { "Album" }
            if (!collection.name.equals(name, ignoreCase = true)) {
                diaryCollectionDao.updateName(collection.id, name)
            }
            val folderList = driveService.listChildrenWithMetadata(child.id)
            val files = when (folderList) {
                is DriveResult.Success -> folderList.data
                else -> emptyList()
            }
            for (f in files) {
                if (f.mimeType?.startsWith("image/") != true) continue
                allImageFileIdsOnDrive.add(f.id)
                if (!knownDriveIds.add(f.id)) continue
                val taken = parseDateTimeOnlyFileName(f.name)
                    ?: parseRfc3339ToMillis(f.modifiedTimeRfc3339)
                    ?: System.currentTimeMillis()
                upsertImageFromDrive(f.id, f.name, collection.id, taken)
                added++
            }
        }

        var removedImages = 0
        for (img in diaryCollectionImageDao.getAllSync()) {
            if (img.driveFileId !in allImageFileIdsOnDrive) {
                diaryCollectionImageDao.deleteById(img.id)
                removedImages++
            }
        }

        var removedCollections = 0
        for (col in diaryCollectionDao.getAllSync()) {
            val folderId = col.driveFolderId
            if (folderId == null || folderId !in albumFolderIdsOnDrive) {
                diaryCollectionImageDao.deleteByCollectionId(col.id)
                diaryCollectionDao.deleteById(col.id)
                removedCollections++
            }
        }

        return Result.success(
            DiaryDriveSyncResult(
                importedCount = added,
                removedImagesCount = removedImages,
                removedCollectionsCount = removedCollections
            )
        )
    }

    suspend fun uploadDiaryAudioFromPath(date: String, localRelativePath: String): Result<String> {
        val file = File(context.filesDir, localRelativePath)
        if (!file.exists()) return Result.failure(Exception("Audio file not found"))
        val bytes = file.readBytes()
        return uploadDiaryAudio(date, bytes)
    }

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

    suspend fun getDiaryAudioBytes(driveFileId: String): ByteArray? = getImageBytes(driveFileId)

    private suspend fun findOrCreateAlbumFromDriveFolder(driveFolderId: String, rawFolderName: String): DiaryCollection {
        val displayName = rawFolderName.trim().ifBlank { "Album" }
        diaryCollectionDao.getByDriveFolderIdSync(driveFolderId)?.let { return it }
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val id = "col_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val col = DiaryCollection(id = id, date = date, name = displayName, driveFolderId = driveFolderId)
        diaryCollectionDao.insert(col)
        return col
    }

    private suspend fun upsertImageFromDrive(
        driveFileId: String,
        fileName: String,
        collectionId: String,
        takenAtMillis: Long
    ) {
        val id = stableImageRowId(driveFileId)
        diaryCollectionImageDao.insert(
            DiaryCollectionImage(
                id = id,
                collectionId = collectionId,
                driveFileId = driveFileId,
                fileName = fileName,
                takenAtMillis = takenAtMillis
            )
        )
    }

    private fun stableImageRowId(driveFileId: String) = "img_$driveFileId"

    /** e.g. `2026-04-06T07-25-46-195.jpg` */
    private fun buildDateTimeOnlyFileName(takenAtMillis: Long, ext: String): String {
        val cal = Calendar.getInstance().apply { timeInMillis = takenAtMillis }
        fun p2(n: Int) = n.toString().padStart(2, '0')
        fun p3(n: Int) = n.toString().padStart(3, '0')
        val y = cal.get(Calendar.YEAR)
        val mo = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val hh = cal.get(Calendar.HOUR_OF_DAY)
        val mi = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND)
        val ms = cal.get(Calendar.MILLISECOND)
        return "${y}-${p2(mo)}-${p2(d)}T${p2(hh)}-${p2(mi)}-${p2(s)}-${p3(ms)}.$ext"
    }

    private fun parseDateTimeOnlyFileName(fileName: String): Long? {
        val m = DATETIME_FILENAME_REGEX.matchEntire(fileName) ?: return null
        val yMd = m.groupValues[1].split("-")
        if (yMd.size != 3) return null
        val y = yMd[0].toIntOrNull() ?: return null
        val mo = yMd[1].toIntOrNull() ?: return null
        val d = yMd[2].toIntOrNull() ?: return null
        val hh = m.groupValues[2].toIntOrNull() ?: return null
        val mm = m.groupValues[3].toIntOrNull() ?: return null
        val ss = m.groupValues[4].toIntOrNull() ?: return null
        val mss = m.groupValues[5].toIntOrNull() ?: return null
        val cal = Calendar.getInstance()
        cal.set(y, mo - 1, d, hh, mm, ss)
        cal.set(Calendar.MILLISECOND, mss)
        return cal.timeInMillis
    }

    private fun sanitizeDriveFolderName(name: String): String {
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(200)
        return if (safe.isBlank()) "Album_${UUID.randomUUID().toString().take(8)}" else safe
    }

    private fun parseRfc3339ToMillis(rfc: String?): Long? {
        if (rfc.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(rfc).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                fmt.parse(rfc)?.time
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        private val DATETIME_FILENAME_REGEX = Regex(
            "^(\\d{4}-\\d{2}-\\d{2})T(\\d{2})-(\\d{2})-(\\d{2})-(\\d{3})\\.(jpg|jpeg|png|webp)$",
            RegexOption.IGNORE_CASE
        )
    }
}
