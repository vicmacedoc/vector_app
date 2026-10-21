package com.vm.vector.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vm.core.audio.M4aRepair
import com.vm.core.models.BrainAsset
import com.vm.core.models.BrainAssetSyncState
import com.vm.core.models.BrainSection
import com.vm.core.models.VectorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Local file store for Brain media under filesDir/brain/...
 * Imports are stream-copied from content URIs (never fully loaded into memory).
 */
class BrainFileStore(
    private val context: Context,
    private val database: VectorDatabase,
) {
    private val assetDao = database.brainAssetDao()

    fun brainRoot(): File = File(context.filesDir, ROOT).also { it.mkdirs() }

    fun absoluteFile(relativePath: String): File = File(context.filesDir, relativePath)

    fun relativePath(section: BrainSection, vararg parts: String): String {
        val sectionFolder = when (section) {
            BrainSection.MEMORY -> "Memory"
            BrainSection.MODEL -> "Model"
            BrainSection.MOTION -> "Motion"
        }
        val joined = parts.filter { it.isNotBlank() }.joinToString("/") { sanitize(it) }
        return if (joined.isBlank()) "$ROOT/$sectionFolder" else "$ROOT/$sectionFolder/$joined"
    }

    fun inboxRelativePath(fileName: String): String = "$ROOT/Inbox/${sanitize(fileName)}"

    fun displayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    fun mimeType(uri: Uri): String = context.contentResolver.getType(uri) ?: "application/octet-stream"

    suspend fun importFromUri(
        uri: Uri,
        relativePath: String,
        mimeType: String,
        displayName: String,
        wifiOnly: Boolean = false
    ): Result<BrainAsset> = withContext(Dispatchers.IO) {
        try {
            val dest = absoluteFile(relativePath)
            dest.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(Exception("Could not open URI"))
            val asset = BrainAsset(
                id = "asset_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}",
                localRelativePath = relativePath,
                mimeType = mimeType,
                sizeBytes = dest.length(),
                syncState = if (wifiOnly) BrainAssetSyncState.WIFI_ONLY else BrainAssetSyncState.PENDING,
                fileName = displayName.ifBlank { dest.name },
            )
            assetDao.insert(asset)
            Result.success(asset)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeTextFile(
        relativePath: String,
        content: String,
        mimeType: String = "text/plain",
        existingAssetId: String? = null
    ): Result<BrainAsset> = withContext(Dispatchers.IO) {
        try {
            val dest = absoluteFile(relativePath)
            dest.parentFile?.mkdirs()
            dest.writeText(content)
            val existing = existingAssetId?.let { assetDao.getByIdSync(it) }
            val asset = (existing ?: BrainAsset(
                id = "asset_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}",
                localRelativePath = relativePath,
                mimeType = mimeType,
                fileName = dest.name,
            )).copy(
                localRelativePath = relativePath,
                mimeType = mimeType,
                sizeBytes = dest.length(),
                syncState = if (existing?.driveFileId != null) BrainAssetSyncState.PENDING else BrainAssetSyncState.PENDING,
                fileName = dest.name,
                updatedAt = System.currentTimeMillis()
            )
            assetDao.insert(asset)
            Result.success(asset)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readTextFile(relativePath: String): String? = withContext(Dispatchers.IO) {
        val file = absoluteFile(relativePath)
        if (!file.exists()) null else file.readText()
    }

    suspend fun writeDownloadedDriveFile(
        relativePath: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        driveFileId: String,
    ): Result<BrainAsset> = withContext(Dispatchers.IO) {
        try {
            val dest = absoluteFile(relativePath)
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
            val asset = BrainAsset(
                id = "asset_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}",
                localRelativePath = relativePath,
                mimeType = mimeType,
                sizeBytes = dest.length(),
                driveFileId = driveFileId,
                syncState = BrainAssetSyncState.UPLOADED,
                fileName = fileName.ifBlank { dest.name },
            )
            assetDao.insert(asset)
            Result.success(asset)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerExistingLocalFile(
        relativePath: String,
        mimeType: String,
    ): Result<BrainAsset> = withContext(Dispatchers.IO) {
        try {
            val dest = absoluteFile(relativePath)
            if (!dest.exists()) return@withContext Result.failure(Exception("File missing"))
            if (mimeType.startsWith("audio/")) M4aRepair.repairIfNeeded(dest)
            val asset = BrainAsset(
                id = "asset_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}",
                localRelativePath = relativePath,
                mimeType = mimeType,
                sizeBytes = dest.length(),
                syncState = BrainAssetSyncState.PENDING,
                fileName = dest.name,
            )
            assetDao.insert(asset)
            Result.success(asset)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteLocal(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        absoluteFile(relativePath).delete()
    }

    fun isVideoMime(mimeType: String): Boolean = mimeType.startsWith("video/", ignoreCase = true)

    private fun sanitize(name: String): String {
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(200)
        return safe.ifBlank { "file_${UUID.randomUUID().toString().take(8)}" }
    }

    companion object {
        const val ROOT = "brain"
    }
}
