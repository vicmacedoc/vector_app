package com.vm.vector.motion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vm.core.models.MotionTaskStatus
import com.vm.vector.MainActivity
import com.vm.vector.VectorApplication
import com.vm.vector.data.BrainFileStore
import com.vm.vector.data.BrainRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MotionReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "motion_reminders"
        private const val REQUEST_DONE_OFFSET = 1
        private const val REQUEST_SNOOZE_OFFSET = 2
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val taskId = intent.getStringExtra(MotionReminderScheduler.EXTRA_TASK_ID) ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    MotionReminderScheduler.ACTION_REMINDER -> showReminder(context, taskId)
                    MotionReminderScheduler.ACTION_DONE -> onDone(context, taskId)
                    MotionReminderScheduler.ACTION_SNOOZE -> onSnooze(context, taskId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun brainRepository(context: Context): BrainRepository {
        val app = context.applicationContext as VectorApplication
        val database = app.database
        return BrainRepository(database, BrainFileStore(context.applicationContext, database))
    }

    private suspend fun showReminder(context: Context, taskId: String) {
        val repo = brainRepository(context)
        val task = repo.getTask(taskId) ?: return
        if (task.status != MotionTaskStatus.OPEN) return

        createChannel(context)
        val notificationId = MotionReminderScheduler.requestCodeFor(taskId)

        val openPi = android.app.PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val donePi = MotionReminderScheduler.pendingIntent(
            context,
            taskId,
            MotionReminderScheduler.ACTION_DONE,
            REQUEST_DONE_OFFSET,
        )
        val snoozePi = MotionReminderScheduler.pendingIntent(
            context,
            taskId,
            MotionReminderScheduler.ACTION_SNOOZE,
            REQUEST_SNOOZE_OFFSET,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(task.title)
            .setContentText("Motion task reminder")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .addAction(0, "Done", donePi)
            .addAction(0, "Snooze 1h", snoozePi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)
    }

    private suspend fun onDone(context: Context, taskId: String) {
        val repo = brainRepository(context)
        repo.completeTask(taskId)
        MotionReminderScheduler.cancel(context, taskId)
        dismissNotification(context, taskId)
    }

    private suspend fun onSnooze(context: Context, taskId: String) {
        val repo = brainRepository(context)
        val until = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
        repo.snoozeTask(taskId, until)
        val updated = repo.getTask(taskId) ?: return
        MotionReminderScheduler.schedule(context, updated)
        dismissNotification(context, taskId)
    }

    private fun dismissNotification(context: Context, taskId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(MotionReminderScheduler.requestCodeFor(taskId))
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Motion reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Reminders for Motion tasks"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
