package com.vm.vector.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Receives wake-up alarm intents and starts AlarmRingingService to play
 * ringtone and show full-screen notification.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in listOf(
                SleepAlarmScheduler.ACTION_ALARM_MAIN,
                SleepAlarmScheduler.ACTION_ALARM_5MIN,
                SleepAlarmScheduler.ACTION_ALARM_10MIN,
                SleepAlarmScheduler.ACTION_ALARM_15MIN
            )
        ) return

        val ringtoneUri = intent.getStringExtra(SleepAlarmScheduler.EXTRA_RINGTONE_URI)
            ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI.toString()
        val wakeMinutes = intent.getIntExtra(SleepAlarmScheduler.EXTRA_WAKE_MINUTES, -1)

        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra(SleepAlarmScheduler.EXTRA_RINGTONE_URI, ringtoneUri)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Reschedule for next day so wake alarms repeat daily
        if (wakeMinutes in 0..(24 * 60 - 1)) {
            SleepAlarmScheduler(context).scheduleWakeAlarmsForNextDay(wakeMinutes, ringtoneUri)
        }
    }
}
