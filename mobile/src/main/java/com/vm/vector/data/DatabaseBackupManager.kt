package com.vm.vector.data

import android.content.Context
import com.vm.core.models.VectorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles backup of the full Room database to Google Drive and restore from Drive.
 * Local Room is primary; backup is periodic (e.g. after Save day, on app background).
 */
class DatabaseBackupManager(
    private val context: Context,
    private val database: VectorDatabase,
    private val driveService: DriveService,
    private val preferenceManager: PreferenceManager,
) {

    suspend fun backup(): Result<Unit> = withContext(Dispatchers.IO) {
        val rootFolderId = preferenceManager.googleDriveFolderId.first()
        if (rootFolderId.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Drive folder ID not configured"))
        }
        try {
            // Flush WAL into main DB so the file is complete
            try {
                database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
            } catch (_: Exception) {
                // Some wrappers only allow query/rawQuery; skip checkpoint and use current file
            }
            val dbFile = context.getDatabasePath("vector_database")
            if (!dbFile.exists()) {
                return@withContext Result.failure(Exception("Database file not found"))
            }
            val bytes = dbFile.readBytes()
            when (val result = driveService.uploadDatabaseBackup(rootFolderId, bytes)) {
                is DriveResult.Success -> Result.success(Unit)
                is DriveResult.NeedsRemoteConsent -> Result.failure(Exception("Needs remote consent. Please authorize Drive in Settings."))
                is DriveResult.Unauthorized -> Result.failure(Exception("Drive access unauthorized"))
                is DriveResult.ConfigurationError -> Result.failure(Exception("Drive folder not found or invalid"))
                is DriveResult.ListsFolderNotFound -> Result.failure(Exception("Database folder not found in Drive"))
                is DriveResult.OtherError -> Result.failure(Exception(result.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restore(): Result<Unit> = withContext(Dispatchers.IO) {
        val rootFolderId = preferenceManager.googleDriveFolderId.first()
        if (rootFolderId.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Drive folder ID not configured"))
        }
        try {
            when (val result = driveService.downloadDatabaseBackup(rootFolderId)) {
                is DriveResult.Success -> {
                    database.close()
                    val dbFile = context.getDatabasePath("vector_database")
                    dbFile.writeBytes(result.data)
                    File(dbFile.absolutePath + "-wal").takeIf { it.exists() }?.delete()
                    File(dbFile.absolutePath + "-shm").takeIf { it.exists() }?.delete()
                    Result.success(Unit)
                }
                is DriveResult.NeedsRemoteConsent -> Result.failure(Exception("Needs remote consent. Please authorize Drive in Settings."))
                is DriveResult.Unauthorized -> Result.failure(Exception("Drive access unauthorized"))
                is DriveResult.ConfigurationError -> Result.failure(Exception("No database backup found in Drive"))
                is DriveResult.ListsFolderNotFound -> Result.failure(Exception("Database folder not found in Drive"))
                is DriveResult.OtherError -> Result.failure(Exception(result.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
