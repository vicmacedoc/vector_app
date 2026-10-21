package com.vm.vector.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.vm.vector.MainActivity
import com.vm.vector.R
import com.vm.vector.VectorApplication
import com.vm.vector.data.BrainSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Microphone foreground service for Brain voice quick capture. Keeps recording with the
 * screen off and shows pause / stop controls in a lock-screen-visible notification.
 * Stopping files the recording into Brain Inbox.
 */
class VoiceCaptureService : Service() {

    /** Not cancelled in [onDestroy] so a stop right before teardown still gets filed. */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var recorder: BrainAudioRecorder
    private var wakeLock: PowerManager.WakeLock? = null
    private var state = VoiceCaptureState()
    /** Memory tile the current recording files into; null = Inbox. */
    private var targetNodeId: String? = null

    override fun onCreate() {
        super.onCreate()
        recorder = BrainAudioRecorder(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent.getStringExtra(EXTRA_NODE_ID))
            ACTION_PAUSE -> pauseCapture()
            ACTION_RESUME -> resumeCapture()
            ACTION_STOP -> stopCapture()
            else -> if (!recorder.isRecording) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (recorder.isRecording) {
            val elapsed = state.elapsedMs()
            recorder.stop()?.let { fileRecording(it, elapsed) }
        }
        publish(VoiceCaptureState())
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startCapture(nodeId: String?) {
        if (recorder.isRecording) {
            startForegroundCompat()
            return
        }
        targetNodeId = nodeId
        state = VoiceCaptureState(isRecording = true, resumedAtElapsed = SystemClock.elapsedRealtime())
        if (!startForegroundCompat()) {
            state = VoiceCaptureState()
            stopSelf()
            return
        }
        val path = recorder.startInInbox { msg ->
            Toast.makeText(this, "Voice capture failed: $msg", Toast.LENGTH_SHORT).show()
        }
        if (path == null) {
            state = VoiceCaptureState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        acquireWakeLock()
        publish(state)
    }

    private fun pauseCapture() {
        if (!state.isRecording || state.isPaused) return
        val now = SystemClock.elapsedRealtime()
        if (!recorder.pause()) return
        state = state.copy(isPaused = true, accumulatedMs = state.elapsedMs(now))
        publish(state)
    }

    private fun resumeCapture() {
        if (!state.isRecording || !state.isPaused) return
        if (!recorder.resume()) return
        state = state.copy(isPaused = false, resumedAtElapsed = SystemClock.elapsedRealtime())
        publish(state)
    }

    private fun stopCapture() {
        val elapsed = state.elapsedMs()
        val path = recorder.stop()
        state = VoiceCaptureState()
        publish(state)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        path?.let { fileRecording(it, elapsed) }
        stopSelf()
    }

    private fun fileRecording(relativePath: String, elapsedMs: Long) {
        val app = application as VectorApplication
        val nodeId = targetNodeId
        targetNodeId = null
        saveScope.launch {
            val message = if (elapsedMs < MIN_RECORDING_MS) {
                File(app.filesDir, relativePath).delete()
                "Voice capture too short — discarded"
            } else {
                try {
                    val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    val repo = app.brainRepository
                    val tileName = nodeId?.let { repo.getNode(it)?.name }
                    repo.createAudioNoteFromRecording(
                        relativePath,
                        title = "Voice note $timeLabel",
                        nodeId = nodeId.takeIf { tileName != null },
                    )
                    BrainSyncWorker.enqueue(app)
                    if (tileName != null) "Voice note saved to $tileName" else "Voice note saved to Brain Inbox"
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to file voice capture", e)
                    "Couldn't save voice capture"
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun publish(newState: VoiceCaptureState) {
        VoiceCaptureController.publish(newState)
        if (newState.isRecording) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun startForegroundCompat(): Boolean = try {
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        true
    } catch (e: Exception) {
        Log.e(TAG, "startForeground failed", e)
        false
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleAction = if (state.isPaused) {
            action(R.drawable.ic_voice_play, "Resume", ACTION_RESUME, REQUEST_TOGGLE)
        } else {
            action(R.drawable.ic_voice_pause, "Pause", ACTION_PAUSE, REQUEST_TOGGLE)
        }
        val stopAction = action(R.drawable.ic_voice_stop, "Stop & save", ACTION_STOP, REQUEST_STOP)

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_voice_capture)
            .setContentTitle(if (state.isPaused) "Voice capture paused" else "Recording voice capture")
            .setContentIntent(openPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(NOTIFICATION_COLOR)
            .addAction(toggleAction)
            .addAction(stopAction)
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1))

        if (state.isPaused) {
            builder.setContentText("Paused at ${formatElapsed(state.accumulatedMs)}")
                .setShowWhen(false)
        } else {
            builder.setContentText(if (targetNodeId != null) "Files to your memory when stopped" else "Files to Brain Inbox when stopped")
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() - state.elapsedMs())
        }
        return builder.build()
    }

    private fun action(iconRes: Int, title: String, action: String, requestCode: Int): Notification.Action {
        val pi = PendingIntent.getService(
            this,
            requestCode,
            Intent(this, VoiceCaptureService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, iconRes), title, pi).build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voice capture",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ongoing Brain voice capture with pause and stop controls"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vector:VoiceCapture").apply {
            setReferenceCounted(false)
            acquire(MAX_RECORDING_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun formatElapsed(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    companion object {
        const val ACTION_START = "com.vm.vector.voice.START"
        const val ACTION_PAUSE = "com.vm.vector.voice.PAUSE"
        const val ACTION_RESUME = "com.vm.vector.voice.RESUME"
        const val ACTION_STOP = "com.vm.vector.voice.STOP"
        const val EXTRA_NODE_ID = "com.vm.vector.voice.NODE_ID"

        private const val TAG = "VoiceCaptureService"
        private const val CHANNEL_ID = "vector_voice_capture"
        private const val NOTIFICATION_ID = 2100
        private const val REQUEST_TOGGLE = 1
        private const val REQUEST_STOP = 2
        /** MediaRecorder writes an unplayable file for sub-second takes. */
        private const val MIN_RECORDING_MS = 1_000L
        private const val MAX_RECORDING_MS = 4L * 60 * 60 * 1000
        private const val NOTIFICATION_COLOR = 0xFF001F3F.toInt()
    }
}
