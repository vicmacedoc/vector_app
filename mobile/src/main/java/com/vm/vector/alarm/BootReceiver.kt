package com.vm.vector.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vm.vector.data.PreferenceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Reschedules wake-up alarms and fill-out reminder after device boot,
 * since AlarmManager cancels all alarms on reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        runBlocking {
            val prefManager = PreferenceManager(context)
            val scheduler = SleepAlarmScheduler(context)

            val baseMinutes = prefManager.wakeAlarmBaseMinutes.first()
            val offsets = prefManager.wakeAlarmOffsets.first()
            if (baseMinutes != null && offsets.isNotEmpty()) {
                val ringtoneUri = scheduler.pickRandomAlarmRingtone()
                scheduler.scheduleWakeAlarmsRecurrent(baseMinutes, ringtoneUri)
            }

            prefManager.filloutReminderMinutes.first()?.let { minutes ->
                scheduler.scheduleFilloutReminder(minutes)
            }
        }
    }
}
