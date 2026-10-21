package com.vm.vector.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vm.vector.VectorApplication
import com.vm.vector.data.backup.BackupOutcome

class DatabaseBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? VectorApplication
            ?: return Result.failure()
        val manager = app.databaseBackupManager
        val outcome = when (inputData.getString(KEY_MODE)) {
            MODE_LATEST -> manager.backupLatest()
            else -> manager.backupDaily()
        }
        return when (outcome) {
            is BackupOutcome.Success, is BackupOutcome.Conflict -> Result.success()
            is BackupOutcome.Failure -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_MODE = "mode"
        const val MODE_DAILY = "daily"
        const val MODE_LATEST = "latest"
        private const val MAX_ATTEMPTS = 5
    }
}
