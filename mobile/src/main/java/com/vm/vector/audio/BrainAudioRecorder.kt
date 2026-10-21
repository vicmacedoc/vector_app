package com.vm.vector.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * AAC / M4A recorder for Brain voice notes and Model audio captures.
 */
class BrainAudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentRelativePath: String? = null

    fun start(relativePath: String, onError: (String) -> Unit): String? {
        stop()
        val file = File(context.filesDir, relativePath)
        file.parentFile?.mkdirs()
        return try {
            mediaRecorder = createRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            currentRelativePath = relativePath
            relativePath
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            onError(e.message ?: "Recording failed")
            null
        }
    }

    fun startInInbox(onError: (String) -> Unit): String? {
        val name = "voice_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.m4a"
        return start("brain/Inbox/$name", onError)
    }

    fun stop(): String? {
        val path = currentRelativePath
        try {
            mediaRecorder?.apply {
                try { stop() } catch (_: Exception) {}
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
        currentRelativePath = null
        return path
    }

    fun pause(): Boolean = try {
        mediaRecorder?.pause()
        mediaRecorder != null
    } catch (e: Exception) {
        Log.e(TAG, "pause failed", e)
        false
    }

    fun resume(): Boolean = try {
        mediaRecorder?.resume()
        mediaRecorder != null
    } catch (e: Exception) {
        Log.e(TAG, "resume failed", e)
        false
    }

    val isRecording: Boolean get() = mediaRecorder != null

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    companion object {
        private const val TAG = "BrainAudioRecorder"
        const val MIME_M4A = "audio/mp4"
    }
}
