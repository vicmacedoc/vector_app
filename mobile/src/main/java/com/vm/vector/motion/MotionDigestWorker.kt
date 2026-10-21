package com.vm.vector.motion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vm.vector.MainActivity
import com.vm.vector.VectorApplication
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MotionDigestWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? VectorApplication ?: return Result.failure()
        val dao = app.database.motionTaskDao()
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfDay = startOfDay + TimeUnit.DAYS.toMillis(1)

        val overdue = dao.getOverdue(now)
        val dueToday = dao.getOpenDueBetween(startOfDay, endOfDay)
        if (overdue.isEmpty() && dueToday.isEmpty()) return Result.success()

        createChannel(applicationContext)

        val title = when {
            overdue.isNotEmpty() && dueToday.isNotEmpty() ->
                "Motion: ${overdue.size} overdue, ${dueToday.size} due today"
            overdue.isNotEmpty() -> "Motion: ${overdue.size} overdue"
            else -> "Motion: ${dueToday.size} due today"
        }

        val lines = buildList {
            if (overdue.isNotEmpty()) {
                add("Overdue: ${overdue.joinToString(", ") { it.title }}")
            }
            if (dueToday.isNotEmpty()) {
                add("Today: ${dueToday.joinToString(", ") { it.title }}")
            }
        }
        val body = lines.joinToString("\n")

        val openPi = android.app.PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, MotionReminderReceiver.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(title)
            .setContentText(body.take(120).let { if (body.length > 120) "$it…" else it })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MotionReminderReceiver.CHANNEL_ID,
                "Motion reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Motion task reminders and daily digest"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "vector_motion_digest_daily"
        private const val NOTIFICATION_ID = 3100
        private const val HOUR = 8
        private const val MINUTE = 0

        fun enqueueDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<MotionDigestWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(millisUntilNextRun(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
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
}
