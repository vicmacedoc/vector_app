package com.vm.vector.audio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.vm.core.audio.M4aRepair
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vm.core.models.AudioBookmark
import com.vm.core.models.AudioProgress
import com.vm.vector.VectorApplication
import com.vm.vector.data.BrainFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(UnstableApi::class)
class BrainPlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var periodicSaveJob: Job? = null
    private var bookmarks: List<AudioBookmark> = emptyList()
    private var currentAssetId: String? = null
    private var loadGeneration = 0

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(BrainPlayerCommands.COMMAND_SEEK_BACK_15)
                .add(BrainPlayerCommands.COMMAND_SEEK_FORWARD_30)
                .add(BrainPlayerCommands.COMMAND_SET_SPEED)
                .add(BrainPlayerCommands.COMMAND_ADD_BOOKMARK)
                .add(BrainPlayerCommands.COMMAND_DELETE_BOOKMARK)
                .add(BrainPlayerCommands.COMMAND_SAVE_CHECKPOINT)
                .add(BrainPlayerCommands.COMMAND_CLOSE)
                .build()
            // Empty player commands block play, pause, and seek from the in-app controller.
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val p = player ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            when (customCommand.customAction) {
                BrainPlayerCommands.SEEK_BACK_15 -> {
                    p.seekTo((p.currentPosition - 15_000L).coerceAtLeast(0L))
                }
                BrainPlayerCommands.SEEK_FORWARD_30 -> {
                    val duration = p.duration
                    val target = p.currentPosition + 30_000L
                    p.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
                }
                BrainPlayerCommands.SET_SPEED -> {
                    val speed = args.getFloat(BrainPlayerCommands.ARG_SPEED, 1f)
                    p.setPlaybackSpeed(speed)
                }
                BrainPlayerCommands.ADD_BOOKMARK -> {
                    val label = args.getString(BrainPlayerCommands.ARG_LABEL).orEmpty()
                    val positionMs = args.getLong(BrainPlayerCommands.ARG_POSITION_MS, p.currentPosition)
                    addBookmark(label, positionMs)
                }
                BrainPlayerCommands.DELETE_BOOKMARK -> {
                    val id = args.getString(BrainPlayerCommands.ARG_BOOKMARK_ID).orEmpty()
                    if (id.isNotBlank()) deleteBookmark(id)
                }
                BrainPlayerCommands.SAVE_CHECKPOINT -> {
                    serviceScope.launch { saveProgress() }
                }
                BrainPlayerCommands.CLOSE -> closePlayback(p)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startPeriodicSave()
            } else {
                stopPeriodicSave()
                serviceScope.launch { saveProgress() }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                serviceScope.launch { saveProgress(completed = true) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val extractors = DefaultExtractorsFactory().setMp4ExtractorFlags(
            Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS,
        )
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, extractors))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(playerListener) }
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(sessionCallback)
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .build(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val assetId = intent?.getStringExtra(EXTRA_ASSET_ID)
        val autoPlay = intent?.getBooleanExtra(EXTRA_AUTO_PLAY, true) ?: true
        if (!assetId.isNullOrBlank()) {
            serviceScope.launch { loadAndPlay(assetId, autoPlay) }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        stopPeriodicSave()
        serviceScope.launch {
            saveProgress()
        }
        val exoPlayer = player
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        player = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private suspend fun loadAndPlay(assetId: String, autoPlay: Boolean) {
        val generation = ++loadGeneration
        val app = application as VectorApplication
        val asset = withContext(Dispatchers.IO) {
            app.database.brainAssetDao().getByIdSync(assetId)
        } ?: return
        val progress = withContext(Dispatchers.IO) {
            app.database.audioProgressDao().getByAssetIdSync(assetId)
        }
        bookmarks = progress?.bookmarks.orEmpty()
        currentAssetId = assetId

        val p = player ?: return
        if (p.currentMediaItem?.mediaId == assetId &&
            (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING)
        ) {
            return
        }

        val file = BrainFileStore(app, app.database).absoluteFile(asset.localRelativePath)
        if (!file.exists()) return
        withContext(Dispatchers.IO) { M4aRepair.repairIfNeeded(file) }
        if (generation != loadGeneration) return

        val noteTitle = withContext(Dispatchers.IO) {
            app.database.memoryNoteDao().getByAssetIdSync(assetId).firstOrNull()?.title?.takeIf { it.isNotBlank() }
        }
        val title = noteTitle ?: asset.fileName.ifBlank { file.name }

        val uri = Uri.fromFile(file)
        val mediaItem = MediaItem.Builder()
            .setMediaId(assetId)
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()

        p.playWhenReady = false
        p.setMediaItem(mediaItem)
        p.prepare()
        armResume(p, progress, generation, autoPlay)
        BrainPlayerController.onServiceStateChanged(
            assetId = assetId,
            title = title,
            bookmarks = bookmarks,
        )
    }

    private fun armResume(
        p: ExoPlayer,
        progress: AudioProgress?,
        generation: Int,
        autoPlay: Boolean,
    ) {
        fun apply() {
            if (generation != loadGeneration) return
            val actual = p.duration.takeIf { it > 0L } ?: 0L
            val inflated = progress != null && AudioPlayback.timelineWasInflated(progress.durationMs, actual)
            val effective = if (inflated) {
                progress.copy(positionMs = 0L, completed = false, durationMs = actual)
            } else {
                progress
            }
            val start = AudioPlayback.startPosition(
                positionMs = effective?.positionMs ?: 0L,
                durationMs = actual,
                completed = effective?.completed == true,
            )
            if (start > 0L) p.seekTo(start)
            p.setPlaybackSpeed(AudioPlayback.nearestSpeed(effective?.speed ?: 1f))
            if (autoPlay) p.play()
        }
        if (p.playbackState == Player.STATE_READY) {
            apply()
            return
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    p.removeListener(this)
                    apply()
                }
            }
        }
        p.addListener(listener)
    }

    /**
     * Saves the position, then unloads the item so the notification goes away.
     * serviceScope runs on Main.immediate, so saveProgress reads the player before it is cleared.
     */
    private fun closePlayback(p: ExoPlayer) {
        stopPeriodicSave()
        serviceScope.launch { saveProgress() }
        loadGeneration++
        currentAssetId = null
        bookmarks = emptyList()
        p.playWhenReady = false
        p.stop()
        p.clearMediaItems()
    }

    private fun addBookmark(label: String, positionMs: Long) {
        val bookmark = AudioBookmark(
            id = UUID.randomUUID().toString(),
            positionMs = positionMs,
            label = label.ifBlank { AudioPlayback.formatTime(positionMs) },
        )
        bookmarks = bookmarks + bookmark
        serviceScope.launch { saveProgress() }
        currentAssetId?.let { id ->
            BrainPlayerController.onServiceStateChanged(
                assetId = id,
                bookmarks = bookmarks,
            )
        }
    }

    private fun deleteBookmark(id: String) {
        bookmarks = bookmarks.filterNot { it.id == id }
        serviceScope.launch { saveProgress() }
        currentAssetId?.let { assetId ->
            BrainPlayerController.onServiceStateChanged(assetId = assetId, bookmarks = bookmarks)
        }
    }

    private fun startPeriodicSave() {
        periodicSaveJob?.cancel()
        periodicSaveJob = serviceScope.launch {
            while (isActive) {
                delay(PERIODIC_SAVE_MS)
                saveProgress()
            }
        }
    }

    private fun stopPeriodicSave() {
        periodicSaveJob?.cancel()
        periodicSaveJob = null
    }

    private suspend fun saveProgress(completed: Boolean = false) {
        val p = player ?: return
        val assetId = p.currentMediaItem?.mediaId ?: currentAssetId ?: return
        val duration = p.duration
        val progress = AudioProgress(
            assetId = assetId,
            positionMs = p.currentPosition.coerceAtLeast(0L),
            durationMs = if (duration > 0) duration else 0L,
            speed = p.playbackParameters.speed,
            completed = completed || (duration > 0 && p.currentPosition >= duration - 500),
            bookmarks = bookmarks,
            updatedAt = System.currentTimeMillis(),
        )
        withContext(Dispatchers.IO) {
            (application as VectorApplication).database.audioProgressDao().insert(progress)
        }
    }

    companion object {
        const val EXTRA_ASSET_ID = "asset_id"
        const val EXTRA_AUTO_PLAY = "auto_play"
        const val NOTIFICATION_CHANNEL_ID = "vector_brain_playback"
        private const val PERIODIC_SAVE_MS = 5_000L
    }
}
