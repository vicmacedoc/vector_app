package com.vm.vector.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * - Daily job at 14:00 local time on unmetered networks: dated snapshot, pruning, then the latest copy.
 * - Debounced job: every save/background event pushes the latest copy back by [DEBOUNCE_MINUTES], so a burst
 *   of edits produces one upload.
 */
object DatabaseBackupScheduler {
    const val UNIQUE_WORK_NAME = "vector_database_backup_daily"
    const val UNIQUE_DEBOUNCED_WORK_NAME = "vector_db_backup_debounced"
    const val DEBOUNCE_MINUTES = 10L

    private const val HOUR = 14
    private const val MINUTE = 0

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DatabaseBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNextRun(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(DatabaseBackupWorker.KEY_MODE to DatabaseBackupWorker.MODE_DAILY))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun requestDebounced(context: Context) {
        val request = OneTimeWorkRequestBuilder<DatabaseBackupWorker>()
            .setInitialDelay(DEBOUNCE_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(DatabaseBackupWorker.KEY_MODE to DatabaseBackupWorker.MODE_LATEST))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_DEBOUNCED_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    internal fun millisUntilNextRun(now: Calendar = Calendar.getInstance()): Long {
        val target = now.clone() as Calendar
        target.set(Calendar.HOUR_OF_DAY, HOUR)
        target.set(Calendar.MINUTE, MINUTE)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
