package com.vm.vector.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar
import java.util.Random

/**
 * Schedules and cancels sleep-related alarms and notifications.
 *
 * - Bedtime notification: 30 min before Sleep Target Start (bedtime), prompts to fill daily inputs
 * - Wake-up alarm: at Sleep Target End (wake-up), plays ringtone, full-screen notification
 * - Follow-up alarms: +5, +10, +15 min after wake-up target
 *
 * When user saves with actual sleep times, all alarms are cleared and reconfigured
 * for the next day using Actual Start/End as new targets.
 */
class SleepAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val ACTION_ALARM_MAIN = "com.vm.vector.ALARM_MAIN"
        const val ACTION_ALARM_5MIN = "com.vm.vector.ALARM_5MIN"
        const val ACTION_ALARM_10MIN = "com.vm.vector.ALARM_10MIN"
        const val ACTION_ALARM_15MIN = "com.vm.vector.ALARM_15MIN"
        const val ACTION_BEDTIME_REMINDER = "com.vm.vector.BEDTIME_REMINDER"
        const val ACTION_FILLOUT_REMINDER = "com.vm.vector.FILLOUT_REMINDER"
        const val EXTRA_RINGTONE_URI = "ringtone_uri"
        const val EXTRA_BEDTIME_MINUTES = "bedtime_minutes"
        const val EXTRA_WAKE_MINUTES = "wake_minutes"
        const val REQUEST_BEDTIME = 1000
        const val REQUEST_FILLOUT_REMINDER = 1002
        const val REQUEST_MAIN = 2000
        const val REQUEST_5MIN = 2001
        const val REQUEST_10MIN = 2002
        const val REQUEST_15MIN = 2003
    }

    /**
     * Schedules recurrent wake-up alarms (at wakeMinutes, +5, +10, +15). Clears all wake alarms first.
     * Each alarm reschedules for the next day when it fires (see AlarmReceiver).
     */
    fun scheduleWakeAlarmsRecurrent(wakeMinutes: Int, ringtoneUri: String) {
        cancelWakeAlarms()
        scheduleWakeAlarmsForDate(wakeMinutes, ringtoneUri, Calendar.getInstance())
    }

    /**
     * Schedules wake alarms for the next calendar day. Used by AlarmReceiver to reschedule recurrently.
     */
    fun scheduleWakeAlarmsForNextDay(wakeMinutes: Int, ringtoneUri: String) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        scheduleWakeAlarmsForDate(wakeMinutes, ringtoneUri, cal)
    }

    /**
     * Cancels a single wake alarm by request code. Use REQUEST_MAIN, REQUEST_5MIN, REQUEST_10MIN, REQUEST_15MIN.
     */
    fun cancelWakeAlarm(requestCode: Int) {
        val action = when (requestCode) {
            REQUEST_MAIN -> ACTION_ALARM_MAIN
            REQUEST_5MIN -> ACTION_ALARM_5MIN
            REQUEST_10MIN -> ACTION_ALARM_10MIN
            REQUEST_15MIN -> ACTION_ALARM_15MIN
            else -> return
        }
        val intent = Intent(context, AlarmReceiver::class.java).apply { this.action = action }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    /**
     * Schedules the fill-out reminder notification at the given time (minutes since midnight). Repeats daily.
     */
    fun scheduleFilloutReminder(reminderMinutes: Int) {
        cancelFilloutReminder()
        scheduleFilloutReminderForDate(reminderMinutes, Calendar.getInstance())
    }

    /**
     * Schedules fill-out reminder for a specific date. Used by FilloutReminderReceiver to reschedule for tomorrow.
     */
    fun scheduleFilloutReminderForDate(reminderMinutes: Int, forDate: Calendar) {
        val cal = forDate.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, reminderMinutes / 60)
        cal.set(Calendar.MINUTE, reminderMinutes % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.before(Calendar.getInstance())) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, FilloutReminderReceiver::class.java).apply {
            action = ACTION_FILLOUT_REMINDER
            putExtra(EXTRA_BEDTIME_MINUTES, reminderMinutes) // reuse for reminder time
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_FILLOUT_REMINDER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExact(cal.timeInMillis, pi)
    }

    fun cancelFilloutReminder() {
        val intent = Intent(context, FilloutReminderReceiver::class.java).apply {
            action = ACTION_FILLOUT_REMINDER
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_FILLOUT_REMINDER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    /**
     * Schedules alarms for today based on target sleep times. Used by Home when saving with actual sleep.
     * Does NOT schedule bedtime notification (fill-out reminder is now set in Settings).
     */
    fun scheduleAlarmsForTarget(
        sleepTargetStartMinutes: Int,
        sleepTargetEndMinutes: Int
    ) {
        cancelAllAlarms()
        val ringtoneUri = pickRandomAlarmRingtone()
        scheduleWakeAlarms(sleepTargetEndMinutes, ringtoneUri)
    }

    /**
     * Cancels all wake alarms and schedules for the next day based on actual wake-up time.
     * Fill-out reminder is set separately in Settings.
     */
    fun rescheduleForNextDayFromActual(
        actualSleepStartMinutes: Int,
        actualSleepEndMinutes: Int
    ) {
        cancelWakeAlarms()
        stopAlarmIfPlaying()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val ringtoneUri = pickRandomAlarmRingtone()
        scheduleWakeAlarmsForDate(actualSleepEndMinutes, ringtoneUri, cal)
    }

    private fun scheduleBedtimeNotification(sleepTargetEndMinutes: Int) {
        scheduleBedtimeNotificationForDate(sleepTargetEndMinutes, Calendar.getInstance())
    }

    private fun scheduleBedtimeNotificationForDate(
        bedtimeMinutesParam: Int,
        baseDate: Calendar
    ) {
        // 30 min before bedtime
        val reminderMinutes = (bedtimeMinutesParam - 30 + 24 * 60) % (24 * 60)
        val cal = baseDate.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, reminderMinutes / 60)
        cal.set(Calendar.MINUTE, reminderMinutes % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.before(Calendar.getInstance())) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, BedtimeNotificationReceiver::class.java).apply {
            action = ACTION_BEDTIME_REMINDER
            putExtra(EXTRA_BEDTIME_MINUTES, bedtimeMinutesParam)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_BEDTIME,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExact(cal.timeInMillis, pi)
    }

    /**
     * Schedules the recurrent bedtime reminder for a specific date.
     * Used by BedtimeNotificationReceiver to reschedule for the next day so the reminder repeats daily.
     * Only the next save with a new bedtime will change the reminder time.
     */
    fun scheduleBedtimeReminderForDate(bedtimeMinutes: Int, forDate: Calendar) {
        scheduleBedtimeNotificationForDate(bedtimeMinutes, forDate)
    }

    private fun scheduleWakeAlarms(wakeMinutes: Int, ringtoneUri: String) {
        scheduleWakeAlarmsForDate(wakeMinutes, ringtoneUri, Calendar.getInstance())
    }

    private fun scheduleWakeAlarmsForDate(
        wakeMinutes: Int,
        ringtoneUri: String,
        baseDate: Calendar
    ) {
        val baseCal = baseDate.clone() as Calendar
        baseCal.set(Calendar.HOUR_OF_DAY, wakeMinutes / 60)
        baseCal.set(Calendar.MINUTE, wakeMinutes % 60)
        baseCal.set(Calendar.SECOND, 0)
        baseCal.set(Calendar.MILLISECOND, 0)
        if (baseCal.before(Calendar.getInstance())) {
            baseCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        fun makeWakeIntent(action: String): Intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_RINGTONE_URI, ringtoneUri)
            putExtra(EXTRA_WAKE_MINUTES, wakeMinutes)
        }

        val mainPi = PendingIntent.getBroadcast(
            context,
            REQUEST_MAIN,
            makeWakeIntent(ACTION_ALARM_MAIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExact(baseCal.timeInMillis, mainPi)

        listOf(
            Triple(5, ACTION_ALARM_5MIN, REQUEST_5MIN),
            Triple(10, ACTION_ALARM_10MIN, REQUEST_10MIN),
            Triple(15, ACTION_ALARM_15MIN, REQUEST_15MIN)
        ).forEach { (offset, action, requestCode) ->
            val cal = baseCal.clone() as Calendar
            cal.add(Calendar.MINUTE, offset)
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                makeWakeIntent(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleExact(cal.timeInMillis, pi)
        }
    }

    private fun scheduleExact(triggerAtMillis: Long, operation: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        } else {
            @Suppress("DEPRECATION")
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    fun pickRandomAlarmRingtone(): String {
        return try {
            val ringtoneManager = RingtoneManager(context)
            ringtoneManager.setType(RingtoneManager.TYPE_ALARM)
            val cursor = ringtoneManager.cursor ?: return defaultAlarmUri()
            val count = cursor.count
            if (count > 0) {
                val randomIndex = Random().nextInt(count)
                @Suppress("DEPRECATION")
                val uri = ringtoneManager.getRingtoneUri(randomIndex)
                uri?.toString() ?: defaultAlarmUri()
            } else {
                defaultAlarmUri()
            }
        } catch (e: Exception) {
            defaultAlarmUri()
        }
    }

    private fun defaultAlarmUri(): String = Settings.System.DEFAULT_ALARM_ALERT_URI.toString()

    fun cancelAllAlarms() {
        cancelBedtime()
        cancelWakeAlarms()
    }

    fun cancelWakeAlarms() {
        listOf(
            Pair(ACTION_ALARM_MAIN, REQUEST_MAIN),
            Pair(ACTION_ALARM_5MIN, REQUEST_5MIN),
            Pair(ACTION_ALARM_10MIN, REQUEST_10MIN),
            Pair(ACTION_ALARM_15MIN, REQUEST_15MIN)
        ).forEach { (action, requestCode) ->
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                this.action = action
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
    }

    private fun cancelBedtime() {
        val intent = Intent(context, BedtimeNotificationReceiver::class.java).apply {
            action = ACTION_BEDTIME_REMINDER
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_BEDTIME,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    fun stopAlarmIfPlaying() {
        context.stopService(Intent(context, AlarmRingingService::class.java))
    }
}
