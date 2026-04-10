package com.vm.vector.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles recording and playback of daily plan audio.
 * Files are stored under filesDir/daily_plan_audio/YYYY-MM-DD.3gp
 * Path stored in DB is relative: "daily_plan_audio/YYYY-MM-DD.3gp"
 */
class DailyPlanAudioHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val TAG = "DailyPlanAudio"
        const val SUBDIR = "daily_plan_audio"
        const val EXT = ".3gp"

        fun todayPathSegment(): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return dateFormat.format(Date())
        }

        /** Relative path for today's recording (for DB storage). */
        fun relativePathForToday(): String = "$SUBDIR/${todayPathSegment()}$EXT"

        /** Diary audio: relative path for a given date (for Calendar Journal). */
        const val DIARY_SUBDIR = "diary_audio"
        const val DIARY_PLAYBACK_RELATIVE_PATH = "diary_playback/temp.3gp"
        fun relativePathForDiaryDate(date: String): String = "$DIARY_SUBDIR/$date$EXT"

        /** Absolute file for a relative path. */
        fun absoluteFile(context: Context, relativePath: String): File =
            File(context.filesDir, relativePath)
    }

    /** Writes bytes to the diary playback temp file and returns the relative path, or null on failure. */
    fun writeDiaryPlaybackBytes(bytes: ByteArray): String? {
        return try {
            val file = absoluteFile(context, DIARY_PLAYBACK_RELATIVE_PATH)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            DIARY_PLAYBACK_RELATIVE_PATH
        } catch (e: Exception) {
            Log.e(TAG, "writeDiaryPlaybackBytes failed", e)
            null
        }
    }

    fun absolutePath(relativePath: String): String = absoluteFile(context, relativePath).absolutePath

    fun startRecording(onError: (String) -> Unit): String? =
        startRecordingToPath(relativePathForToday(), onError)

    /** Records to a specific relative path (e.g. for diary by date). */
    fun startRecordingToPath(relativePath: String, onError: (String) -> Unit): String? {
        stopRecording()
        stopPlayback()
        val file = absoluteFile(context, relativePath)
        file.parentFile?.mkdirs()
        return try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            relativePath
        } catch (e: Exception) {
            Log.e(TAG, "startRecordingToPath failed", e)
            onError(e.message ?: "Recording failed")
            null
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                try { stop() } catch (_: Exception) {}
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
    }

    /**
     * @param volume Optional volume for playback (0.0f to 1.0f). If null, uses system volume.
     *              Use e.g. 0.5f for fixed middle volume when playing from alarm.
     */
    fun startPlayback(
        relativePath: String,
        onCompletion: () -> Unit,
        onError: (String) -> Unit,
        volume: Float? = null
    ) {
        stopPlayback()
        val path = absolutePath(relativePath)
        if (!File(path).exists()) {
            onError("File not found")
            return
        }
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    stopPlayback()
                    onCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    onError("Playback error: $what")
                    true
                }
                if (volume != null) {
                    val clamped = volume.coerceIn(0f, 1f)
                    setVolume(clamped, clamped)
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "startPlayback failed", e)
            onError(e.message ?: "Playback failed")
        }
    }

    fun pausePlayback() {
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {}
    }

    fun resumePlayback() {
        try {
            mediaPlayer?.start()
        } catch (_: Exception) {}
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                try { stop() } catch (_: Exception) {}
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    val isPlaying: Boolean
        get() = try {
            mediaPlayer?.isPlaying == true
        } catch (_: Exception) { false }

    val isPaused: Boolean
        get() = try {
            mediaPlayer != null && !mediaPlayer!!.isPlaying
        } catch (_: Exception) { false }

    fun deleteFile(relativePath: String): Boolean {
        stopPlayback()
        return try {
            absoluteFile(context, relativePath).delete()
        } catch (_: Exception) { false }
    }

    fun fileExists(relativePath: String): Boolean =
        absoluteFile(context, relativePath).exists()
}
