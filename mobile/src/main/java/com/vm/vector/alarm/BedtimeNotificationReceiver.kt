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

class BedtimeNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "vector_bedtime"
        const val NOTIFICATION_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SleepAlarmScheduler.ACTION_BEDTIME_REMINDER) return

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
            .setContentText("Time to fill your daily inputs before bed")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)

        // Reschedule for tomorrow so the reminder repeats every day until a new bedtime is saved
        val bedtimeMinutes = intent.getIntExtra(SleepAlarmScheduler.EXTRA_BEDTIME_MINUTES, -1)
        if (bedtimeMinutes in 0..(24 * 60 - 1)) {
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            SleepAlarmScheduler(context).scheduleBedtimeReminderForDate(bedtimeMinutes, tomorrow)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bedtime Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to fill daily inputs 30 minutes before bedtime"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
