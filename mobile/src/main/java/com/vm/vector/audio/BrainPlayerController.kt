package com.vm.vector.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.vm.core.models.AudioBookmark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class BrainPlayerState(
    val assetId: String? = null,
    val title: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val bookmarks: List<AudioBookmark> = emptyList(),
    /** Epoch millis when playback should pause. Null when no timed sleep is set. */
    val sleepDeadlineEpochMs: Long? = null,
    /** Minutes chosen for the sleep timer, so the picker can show the selection. */
    val sleepMinutes: Int? = null,
    val sleepUntilEnd: Boolean = false,
)

object BrainPlayerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(BrainPlayerState())
    val state: StateFlow<BrainPlayerState> = _state.asStateFlow()

    private var mediaController: MediaController? = null
    private var tickJob: kotlinx.coroutines.Job? = null
    private val connectMutex = Mutex()
    /** Note/source title. Kept across player syncs so the raw file name doesn't replace it. */
    private var displayTitle: String? = null
    private var sleepJob: kotlinx.coroutines.Job? = null
    /** Set by [close] so a stale controller timeline can't bring the mini player back. */
    private var closedAssetId: String? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncFromPlayer()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncFromPlayer()
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            syncFromPlayer()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            syncFromPlayer()
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            _state.update { it.copy(speed = playbackParameters.speed) }
        }
    }

    /** Load the recording and resume its saved position without starting playback. */
    fun open(context: Context, assetId: String) = load(context, assetId, autoPlay = false)

    fun play(context: Context, assetId: String) = load(context, assetId, autoPlay = true)

    private fun load(context: Context, assetId: String, autoPlay: Boolean) {
        val appContext = context.applicationContext
        closedAssetId = null
        scope.launch {
            ensureConnected(appContext)
            val controller = mediaController
            val same = controller?.currentMediaItem?.mediaId == assetId
            if (!same) {
                displayTitle = null
                clearSleepTimer()
            }
            when {
                same && controller?.playbackState == Player.STATE_ENDED -> {
                    controller.seekTo(0)
                    if (autoPlay) controller.play()
                    syncFromPlayer()
                }
                same && (
                    controller?.playbackState == Player.STATE_READY ||
                        controller?.playbackState == Player.STATE_BUFFERING
                    ) -> syncFromPlayer()
                else -> {
                    val intent = Intent(appContext, BrainPlaybackService::class.java).apply {
                        putExtra(BrainPlaybackService.EXTRA_ASSET_ID, assetId)
                        putExtra(BrainPlaybackService.EXTRA_AUTO_PLAY, autoPlay)
                    }
                    // A foreground start must post a notification within seconds, which a paused
                    // player never does. Media3 promotes the service itself once playback starts.
                    appContext.startService(intent)
                }
            }
        }
    }

    fun setDisplayTitle(title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        displayTitle = trimmed
        _state.update { it.copy(title = trimmed) }
    }

    fun togglePlayPause(context: Context) {
        scope.launch {
            ensureConnected(context.applicationContext)
            val controller = mediaController ?: return@launch
            val playing = controller.playWhenReady &&
                controller.playbackState != Player.STATE_ENDED &&
                controller.playbackState != Player.STATE_IDLE
            if (playing) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_ENDED) controller.seekTo(0)
                controller.play()
            }
            syncFromPlayer()
        }
    }

    fun seekBy(context: Context, deltaMs: Long) {
        scope.launch {
            ensureConnected(context.applicationContext)
            val controller = mediaController ?: return@launch
            val duration = controller.duration
            val target = (controller.currentPosition + deltaMs).let { raw ->
                if (duration > 0L) raw.coerceIn(0L, duration) else raw.coerceAtLeast(0L)
            }
            controller.seekTo(target)
            _state.update { it.copy(positionMs = target) }
            syncFromPlayer()
        }
    }

    fun pause(context: Context) {
        scope.launch {
            ensureConnected(context.applicationContext)
            mediaController?.pause()
            syncFromPlayer()
        }
    }

    fun setSleepMinutes(minutes: Int) {
        sleepJob?.cancel()
        val deadline = System.currentTimeMillis() + minutes * 60_000L
        _state.update {
            it.copy(sleepDeadlineEpochMs = deadline, sleepMinutes = minutes, sleepUntilEnd = false)
        }
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            mediaController?.pause()
            _state.update { it.copy(sleepDeadlineEpochMs = null, sleepMinutes = null, sleepUntilEnd = false) }
            syncFromPlayer()
        }
    }

    fun setSleepUntilEnd() {
        sleepJob?.cancel()
        sleepJob = null
        _state.update { it.copy(sleepDeadlineEpochMs = null, sleepMinutes = null, sleepUntilEnd = true) }
    }

    fun clearSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _state.update { it.copy(sleepDeadlineEpochMs = null, sleepMinutes = null, sleepUntilEnd = false) }
    }

    fun setSpeed(context: Context, speed: Float) {
        scope.launch {
            ensureConnected(context.applicationContext)
            val level = AudioPlayback.nearestSpeed(speed)
            mediaController?.setPlaybackSpeed(level)
            _state.update { it.copy(speed = level) }
            syncFromPlayer()
        }
    }

    fun seekTo(context: Context, positionMs: Long) {
        scope.launch {
            ensureConnected(context.applicationContext)
            val controller = mediaController ?: return@launch
            val duration = controller.duration
            val target = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
            controller.seekTo(target)
            _state.update { it.copy(positionMs = target) }
            syncFromPlayer()
        }
    }

    fun deleteBookmark(context: Context, id: String) {
        sendCommand(
            context,
            BrainPlayerCommands.COMMAND_DELETE_BOOKMARK,
            BrainPlayerCommands.argsDeleteBookmark(id),
        )
    }

    fun addBookmark(context: Context, label: String, positionMs: Long? = null) {
        scope.launch {
            ensureConnected(context.applicationContext)
            val pos = positionMs ?: mediaController?.currentPosition ?: 0L
            val controller = mediaController ?: return@launch
            controller.sendCustomCommand(
                BrainPlayerCommands.COMMAND_ADD_BOOKMARK,
                BrainPlayerCommands.argsAddBookmark(label, pos),
            )
        }
    }

    fun saveCheckpoint(context: Context) {
        sendCommand(context, BrainPlayerCommands.COMMAND_SAVE_CHECKPOINT, Bundle.EMPTY)
    }

    /** Stops playback and hides the mini player; the service saves the position for the next open. */
    fun close(context: Context) {
        scope.launch {
            ensureConnected(context.applicationContext)
            closedAssetId = _state.value.assetId ?: mediaController?.currentMediaItem?.mediaId
            mediaController?.sendCustomCommand(BrainPlayerCommands.COMMAND_CLOSE, Bundle.EMPTY)
            displayTitle = null
            sleepJob?.cancel()
            sleepJob = null
            _state.value = BrainPlayerState()
        }
    }

    internal fun onServiceStateChanged(
        assetId: String? = null,
        title: String? = null,
        isPlaying: Boolean? = null,
        positionMs: Long? = null,
        durationMs: Long? = null,
        speed: Float? = null,
        bookmarks: List<AudioBookmark>? = null,
    ) {
        _state.update { current ->
            current.copy(
                assetId = assetId ?: current.assetId,
                title = title ?: current.title,
                isPlaying = isPlaying ?: current.isPlaying,
                positionMs = positionMs ?: current.positionMs,
                durationMs = durationMs ?: current.durationMs,
                speed = speed ?: current.speed,
                bookmarks = bookmarks ?: current.bookmarks,
            )
        }
    }

    private fun sendCommand(context: Context, command: androidx.media3.session.SessionCommand, args: Bundle) {
        scope.launch {
            ensureConnected(context.applicationContext)
            mediaController?.sendCustomCommand(command, args)
            syncFromPlayer()
        }
    }

    private suspend fun ensureConnected(context: Context) {
        if (mediaController != null) return
        connectMutex.withLock {
            if (mediaController != null) return
            val controller = buildController(context)
            controller.addListener(playerListener)
            mediaController = controller
            syncFromPlayer()
            ensureTicks()
        }
    }

    private suspend fun buildController(context: Context): MediaController =
        suspendCancellableCoroutine { cont ->
            val token = SessionToken(context, ComponentName(context, BrainPlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                MoreExecutors.directExecutor(),
            )
        }

    private fun syncFromPlayer() {
        val controller = mediaController ?: return
        val item = controller.currentMediaItem
        if (closedAssetId != null) {
            if (item == null || item.mediaId == closedAssetId) return
            closedAssetId = null
        }
        val reportedDuration = controller.duration
        val metaTitle = item?.mediaMetadata?.title?.toString().orEmpty()
        _state.update {
            val duration = if (reportedDuration > 0L) reportedDuration else it.durationMs
            val title = displayTitle?.takeIf { name -> name.isNotBlank() }
                ?: metaTitle.ifBlank { it.title }
            val ended = controller.playbackState == Player.STATE_ENDED
            it.copy(
                assetId = item?.mediaId ?: it.assetId,
                title = title,
                isPlaying = controller.playWhenReady &&
                    controller.playbackState != Player.STATE_ENDED &&
                    controller.playbackState != Player.STATE_IDLE,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                speed = controller.playbackParameters.speed,
                sleepUntilEnd = if (ended) false else it.sleepUntilEnd,
            )
        }
    }

    private fun ensureTicks() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                syncFromPlayer()
                delay(250L)
            }
        }
    }
}
