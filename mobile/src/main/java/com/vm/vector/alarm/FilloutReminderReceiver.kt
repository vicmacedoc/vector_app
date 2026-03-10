package com.vm.vector.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vm.vector.MainActivity
import java.util.Calendar

/**
 * Receives fill-out reminder at user-chosen time. Shows notification and reschedules for tomorrow.
 */
class FilloutReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "vector_fillout_reminder"
        const val NOTIFICATION_ID = 1002
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SleepAlarmScheduler.ACTION_FILLOUT_REMINDER) return

        createChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Vector")
            .setContentText("Time to fill your daily inputs")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)

        val reminderMinutes = intent.getIntExtra(SleepAlarmScheduler.EXTRA_BEDTIME_MINUTES, -1)
        if (reminderMinutes in 0..(24 * 60 - 1)) {
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            SleepAlarmScheduler(context).scheduleFilloutReminderForDate(reminderMinutes, tomorrow)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fill-out Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to fill daily inputs at your chosen time"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
