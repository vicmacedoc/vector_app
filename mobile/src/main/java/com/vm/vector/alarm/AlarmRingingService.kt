package com.vm.vector.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.vm.vector.MainActivity

/**
 * Foreground service that plays the alarm ringtone and shows a full-screen
 * notification to open the app and stop the alarm.
 * Opening the app stops this service; then MainActivity plays Daily Plan audio.
 */
class AlarmRingingService : Service() {

    private var alarmPlayer: MediaPlayer? = null

    companion object {
        const val CHANNEL_ID = "vector_alarm"
        const val NOTIFICATION_ID = 2000
    }

    override fun onCreate() {
        super.onCreate()
        wakeScreen()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        val ringtoneUri = intent?.getStringExtra(SleepAlarmScheduler.EXTRA_RINGTONE_URI)
            ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI.toString()

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        playRingtone(Uri.parse(ringtoneUri))

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRingtone()
        super.onDestroy()
    }

    private fun wakeScreen() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "Vector:AlarmWakeLock"
        )
        wakeLock.acquire(60_000L)  // auto-releases after 1 min
    }

    /**
     * Plays the alarm ringtone on the alarm stream so it is audible when the alarm fires
     * (e.g. from doze). Uses MediaPlayer with STREAM_ALARM for reliable playback.
     */
    private fun playRingtone(uri: Uri) {
        stopRingtone()
        try {
            alarmPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmRingingService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                @Suppress("DEPRECATION")
                setAudioStreamType(AudioManager.STREAM_ALARM)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            try {
                val defaultUri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
                alarmPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmRingingService, defaultUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (_: Exception) {
                alarmPlayer = null
            }
        }
    }

    private fun stopRingtone() {
        try {
            alarmPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        alarmPlayer = null
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY
            putExtra(MainActivity.EXTRA_FROM_ALARM, true)
        }
        val openPi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra(MainActivity.EXTRA_FROM_ALARM, true)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Vector – Wake up")
            .setContentText("Tap to open app and stop alarm")
            .setContentIntent(openPi)
            .setFullScreenIntent(fullScreenPi, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake-up Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Wake-up alarm with ringtone"
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }
}
