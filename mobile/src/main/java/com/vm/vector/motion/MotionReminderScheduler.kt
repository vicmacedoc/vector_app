package com.vm.vector.motion

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vm.core.models.MotionTask
import com.vm.core.models.MotionTaskStatus

object MotionReminderScheduler {

    const val ACTION_REMINDER = "com.vm.vector.MOTION_REMINDER"
    const val ACTION_DONE = "com.vm.vector.MOTION_REMINDER_DONE"
    const val ACTION_SNOOZE = "com.vm.vector.MOTION_REMINDER_SNOOZE"
    const val EXTRA_TASK_ID = "task_id"

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun requestCodeFor(taskId: String): Int = taskId.hashCode()

    fun schedule(context: Context, task: MotionTask) {
        cancel(context, task.id)
        val dueAt = task.dueAt
        if (task.status != MotionTaskStatus.OPEN || dueAt == null) return

        val triggerAt = dueAt - task.leadMinutes * 60_000L
        if (triggerAt <= System.currentTimeMillis()) return

        val pi = pendingIntent(context, task.id, ACTION_REMINDER)
        scheduleExact(context, triggerAt, pi)
    }

    fun cancel(context: Context, taskId: String) {
        val pi = pendingIntent(context, taskId, ACTION_REMINDER)
        alarmManager(context).cancel(pi)
    }

    fun rescheduleAll(context: Context, tasks: List<MotionTask>) {
        tasks.forEach { schedule(context, it) }
    }

    internal fun pendingIntent(
        context: Context,
        taskId: String,
        action: String,
        requestCodeOffset: Int = 0,
    ): PendingIntent {
        val intent = Intent(context, MotionReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(taskId) + requestCodeOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleExact(context: Context, triggerAtMillis: Long, operation: PendingIntent) {
        val alarmManager = alarmManager(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation,
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operation,
            )
        } else {
            @Suppress("DEPRECATION")
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }
}
