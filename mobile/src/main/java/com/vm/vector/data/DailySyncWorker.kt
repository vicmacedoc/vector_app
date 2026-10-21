package com.vm.vector.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vm.vector.VectorApplication
import com.vm.vector.data.backup.BackupNotifier
import com.vm.vector.data.sync.DailyShards
import com.vm.vector.data.sync.ShardSync
import com.vm.vector.data.sync.ShardSyncFiles
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.TimeUnit

private const val TAG = "DailySyncWorker"

/** One pass of the daily shard sync (Calendar, Home, habits, libraries, presets, shared settings). */
class DailySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? VectorApplication ?: return Result.failure()
        val pendingBefore = ShardSyncFiles(applicationContext).pendingShards().toSet()
        val report = ShardSync(applicationContext, app.database).sync(fullScan = inputData.getBoolean(KEY_FULL_SCAN, false))
        Log.i(TAG, "Daily sync: $report")
        if (report.pulled > 0 || report.merged > 0) DailySync.notifyImported()
        if (report.conflicts.any { it !in pendingBefore }) BackupNotifier.notifyDailyMerge(applicationContext)
        return if (report.ok) Result.success() else Result.retry()
    }

    companion object {
        private const val KEY_FULL_SCAN = "fullScan"
        private const val UNIQUE_WORK_NOW = "vector_daily_sync"
        private const val UNIQUE_WORK_DEBOUNCED = "vector_daily_sync_debounced"
        private const val UNIQUE_WORK_PERIODIC = "vector_daily_sync_periodic"

        private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** After edits: a short wait so a burst of saves becomes one push. */
        fun requestDebounced(context: Context) {
            val request = OneTimeWorkRequestBuilder<DailySyncWorker>()
                .setConstraints(connected)
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_DEBOUNCED, ExistingWorkPolicy.REPLACE, request)
        }

        /** Foreground and in-app polls: dirty shards, recent months and anything Drive changed. */
        fun enqueue(context: Context, fullScan: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<DailySyncWorker>()
                .setConstraints(connected)
                .setInputData(workDataOf(KEY_FULL_SCAN to fullScan))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NOW, ExistingWorkPolicy.KEEP, request)
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailySyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(connected)
                .setInputData(workDataOf(KEY_FULL_SCAN to true))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Local edits to daily data: stamp the shard and schedule a push. */
object DailySync {
    private val _imported = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires after sync wrote another device's changes into Room or prefs; screens holding snapshots reload. */
    val imported: SharedFlow<Unit> = _imported.asSharedFlow()

    fun notifyImported() {
        _imported.tryEmit(Unit)
    }

    /** [date] is YYYY-MM-DD. */
    fun markDay(context: Context, date: String) = mark(context, DailyShards.monthShard(date))

    fun markConfig(context: Context) = mark(context, DailyShards.CONFIG)

    private fun mark(context: Context, shard: String) {
        ShardSyncFiles(context).markDirty(shard, System.currentTimeMillis())
        DailySyncWorker.requestDebounced(context)
    }
}
