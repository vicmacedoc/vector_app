package com.vm.vector.data

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.util.Log
import com.vm.core.models.BrainAssetSyncState
import com.vm.vector.VectorApplication
import com.vm.vector.data.sync.BrainStateSync
import com.vm.vector.data.sync.BrainSyncOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

private const val TAG = "BrainSyncWorker"

/**
 * One Brain sync pass: asset deletes and uploads (videos are WIFI_ONLY), then the shared state
 * ([BrainStateSync]), then files other devices added, then the Drive folder scan.
 */
class BrainSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = RUN_LOCK.withLock { run() }

    private suspend fun run(): Result {
        val app = applicationContext as? VectorApplication ?: return Result.failure()
        val database = app.database
        val assetDao = database.brainAssetDao()
        val prefManager = PreferenceManager(applicationContext)
        val rootFolderId = prefManager.googleDriveFolderId.first()
        if (rootFolderId.isNullOrBlank()) return Result.retry()

        val authManager = GoogleDriveAuthManager(applicationContext)
        val driveService = DriveService(applicationContext, authManager)
        val fileStore = BrainFileStore(applicationContext, database)

        val brainFolder = when (val r = driveService.discoverOrCreateBrainFolder(rootFolderId)) {
            is DriveResult.Success -> r.data
            else -> return Result.retry()
        }

        // Deletes first
        for (asset in assetDao.getBySyncState(BrainAssetSyncState.DELETE_PENDING)) {
            val driveId = asset.driveFileId
            if (!driveId.isNullOrBlank()) {
                when (driveService.deleteFile(driveId)) {
                    is DriveResult.Success -> {
                        fileStore.deleteLocal(asset.localRelativePath)
                        assetDao.deleteById(asset.id)
                    }
                    is DriveResult.NeedsRemoteConsent -> return Result.retry()
                    else -> { /* leave for next run */ }
                }
            } else {
                fileStore.deleteLocal(asset.localRelativePath)
                assetDao.deleteById(asset.id)
            }
        }

        val pending = assetDao.getBySyncState(BrainAssetSyncState.PENDING) +
            assetDao.getBySyncState(BrainAssetSyncState.FAILED)
        val wifiOnly = assetDao.getBySyncState(BrainAssetSyncState.WIFI_ONLY)
        val toUpload = pending + wifiOnly

        for (asset in toUpload) {
            val local = fileStore.absoluteFile(asset.localRelativePath)
            if (!local.exists()) {
                assetDao.updateSyncState(asset.id, BrainAssetSyncState.FAILED, System.currentTimeMillis())
                continue
            }
            // Ensure Mirror folder under brain matching relative path parent
            val parentRelative = asset.localRelativePath
                .removePrefix("${BrainFileStore.ROOT}/")
                .substringBeforeLast('/', missingDelimiterValue = "")
            val folderId = ensureNestedFolders(driveService, brainFolder, parentRelative)
                ?: continue

            when (
                val result = if (!asset.driveFileId.isNullOrBlank()) {
                    when (val over = driveService.overwriteFileFromLocal(
                        fileId = asset.driveFileId!!,
                        mimeType = asset.mimeType,
                        localFile = local
                    )) {
                        is DriveResult.Success -> DriveResult.Success(
                            DriveFileInfo(asset.driveFileId!!, asset.fileName.ifBlank { local.name })
                        )
                        is DriveResult.NeedsRemoteConsent -> over
                        is DriveResult.Unauthorized -> over
                        is DriveResult.ConfigurationError -> over
                        is DriveResult.ListsFolderNotFound -> over
                        is DriveResult.OtherError -> over
                    }
                } else {
                    driveService.uploadFileToFolder(
                        folderId = folderId,
                        fileName = asset.fileName.ifBlank { local.name },
                        mimeType = asset.mimeType,
                        localFile = local
                    )
                }
            ) {
                is DriveResult.Success -> {
                    assetDao.updateSync(
                        id = asset.id,
                        driveFileId = result.data.id,
                        syncState = BrainAssetSyncState.UPLOADED,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                is DriveResult.NeedsRemoteConsent -> return Result.retry()
                else -> {
                    assetDao.updateSyncState(asset.id, BrainAssetSyncState.FAILED, System.currentTimeMillis())
                }
            }
        }

        when (val outcome = BrainStateSync(applicationContext, database).sync()) {
            is BrainSyncOutcome.Failed -> Log.w(TAG, "Brain state sync: ${outcome.message}")
            else -> Log.i(TAG, "Brain state sync: $outcome")
        }

        downloadMissingFiles(driveService, fileStore, assetDao)

        val repository = BrainRepository(database, fileStore)
        val pullSync = BrainDrivePullSync(driveService, repository, fileStore)
        pullSync.pull(brainFolder).onFailure { Log.w(TAG, "Brain Drive pull failed", it) }

        return Result.success()
    }

    /** Files added on another device: small ones always, large ones only on unmetered networks. */
    private suspend fun downloadMissingFiles(
        driveService: DriveService,
        fileStore: BrainFileStore,
        assetDao: com.vm.core.models.BrainAssetDao,
    ) {
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
        val metered = connectivity?.isActiveNetworkMetered ?: true
        for (asset in assetDao.getBySyncState(BrainAssetSyncState.UPLOADED)) {
            val driveId = asset.driveFileId ?: continue
            val dest = fileStore.absoluteFile(asset.localRelativePath)
            if (dest.exists()) continue
            if (metered && asset.sizeBytes > EAGER_DOWNLOAD_BYTES) continue
            val tmp = java.io.File(dest.parentFile, dest.name + ".part")
            when (driveService.downloadToFile(driveId, tmp)) {
                is DriveResult.Success -> if (!tmp.renameTo(dest)) tmp.delete()
                else -> tmp.delete()
            }
        }
    }

    private suspend fun ensureNestedFolders(
        driveService: DriveService,
        rootId: String,
        relativePath: String
    ): String? {
        if (relativePath.isBlank()) return rootId
        var current = rootId
        for (part in relativePath.split('/').filter { it.isNotBlank() }) {
            when (val r = driveService.discoverOrCreateFolder(current, part)) {
                is DriveResult.Success -> current = r.data
                else -> return null
            }
        }
        return current
    }

    companion object {
        const val UNIQUE_WORK_NAME = "vector_brain_sync"
        const val UNIQUE_WORK_WIFI = "vector_brain_sync_wifi"
        private const val UNIQUE_WORK_DEBOUNCED = "vector_brain_sync_debounced"
        private const val UNIQUE_WORK_PERIODIC = "vector_brain_sync_periodic"
        private const val EAGER_DOWNLOAD_BYTES = 20L * 1024 * 1024
        private val RUN_LOCK = Mutex()

        /** After edits: waits a minute so a burst of changes becomes one sync. */
        fun requestDebounced(context: Context) {
            val request = OneTimeWorkRequestBuilder<BrainSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_DEBOUNCED, ExistingWorkPolicy.REPLACE, request)
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<BrainSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun enqueue(context: Context, wifiOnly: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()
            val request = OneTimeWorkRequestBuilder<BrainSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                if (wifiOnly) UNIQUE_WORK_WIFI else UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
