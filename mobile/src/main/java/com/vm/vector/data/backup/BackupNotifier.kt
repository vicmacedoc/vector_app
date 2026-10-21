package com.vm.vector.data.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vm.vector.MainActivity

/** Notifications for Drive backups and Brain sync that need a decision from the user. */
object BackupNotifier {
    private const val CHANNEL_ID = "vector_sync"
    private const val BACKUP_CONFLICT_ID = 4100
    private const val BRAIN_MERGE_ID = 4101
    private const val DAILY_MERGE_ID = 4102

    fun notifyConflict(context: Context) = post(
        context,
        BACKUP_CONFLICT_ID,
        "Backup paused",
        "Drive has a backup this phone would overwrite. Open Vector to choose what to keep.",
    )

    fun cancelConflict(context: Context) = manager(context).cancel(BACKUP_CONFLICT_ID)

    fun notifyBrainMerge(context: Context) = post(
        context,
        BRAIN_MERGE_ID,
        "Brain changed on two devices",
        "Open Brain to choose how to merge phone and desktop changes.",
    )

    fun cancelBrainMerge(context: Context) = manager(context).cancel(BRAIN_MERGE_ID)

    fun notifyDailyMerge(context: Context) = post(
        context,
        DAILY_MERGE_ID,
        "Calendar changed on two devices",
        "Open Settings → Sync to choose how to merge phone and desktop changes.",
    )

    fun cancelDailyMerge(context: Context) = manager(context).cancel(DAILY_MERGE_ID)

    private fun post(context: Context, id: Int, title: String, body: String) {
        val nm = manager(context)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sync & backups", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Drive backup and Brain sync decisions"
            }
        )
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(id, notification) }
    }

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
