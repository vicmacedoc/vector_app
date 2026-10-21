package com.vm.vector.audio

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceCaptureState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    /** Recorded time banked before the current (unpaused) run. */
    val accumulatedMs: Long = 0L,
    /** [SystemClock.elapsedRealtime] when the current run started or resumed. */
    val resumedAtElapsed: Long = 0L,
) {
    fun elapsedMs(now: Long = SystemClock.elapsedRealtime()): Long = when {
        !isRecording -> 0L
        isPaused -> accumulatedMs
        else -> accumulatedMs + (now - resumedAtElapsed)
    }
}

/**
 * Entry point for Brain voice quick capture. Recording runs in [VoiceCaptureService]
 * so it survives screen-off and exposes pause / stop on the lock screen.
 */
object VoiceCaptureController {

    private val _state = MutableStateFlow(VoiceCaptureState())
    val state: StateFlow<VoiceCaptureState> = _state.asStateFlow()

    /**
     * Caller must hold RECORD_AUDIO and be in the foreground (microphone FGS rule).
     * The recording files into the memory tile [nodeId], or the Inbox when null.
     */
    fun start(context: Context, nodeId: String? = null) {
        val appContext = context.applicationContext
        appContext.startForegroundService(
            Intent(appContext, VoiceCaptureService::class.java)
                .setAction(VoiceCaptureService.ACTION_START)
                .putExtra(VoiceCaptureService.EXTRA_NODE_ID, nodeId)
        )
    }

    fun togglePause(context: Context) {
        val current = _state.value
        if (!current.isRecording) return
        send(context, if (current.isPaused) VoiceCaptureService.ACTION_RESUME else VoiceCaptureService.ACTION_PAUSE)
    }

    fun stop(context: Context) {
        if (!_state.value.isRecording) return
        send(context, VoiceCaptureService.ACTION_STOP)
    }

    internal fun publish(state: VoiceCaptureState) {
        _state.value = state
    }

    private fun send(context: Context, action: String) {
        val appContext = context.applicationContext
        appContext.startService(Intent(appContext, VoiceCaptureService::class.java).setAction(action))
    }
}
