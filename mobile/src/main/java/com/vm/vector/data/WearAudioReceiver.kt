package com.vm.vector.data

import android.util.Log
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.vm.core.wear.WearPaths
import com.vm.vector.VectorApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Receives watch voice recordings over ChannelClient and files them into Brain Inbox.
 */
object WearAudioReceiver {

    private const val TAG = "WearAudioReceiver"

    suspend fun onChannelOpened(app: VectorApplication, channel: ChannelClient.Channel) {
        if (!channel.path.startsWith(WearPaths.AUDIO_CHANNEL_PREFIX)) return
        withContext(Dispatchers.IO) {
            val channelClient = Wearable.getChannelClient(app)
            try {
                val fileName = channel.path.removePrefix(WearPaths.AUDIO_CHANNEL_PREFIX)
                    .ifBlank { "watch_${System.currentTimeMillis()}.m4a" }
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val relativePath = "brain/Inbox/$fileName"
                val dest = File(app.filesDir, relativePath).also { it.parentFile?.mkdirs() }
                channelClient.getInputStream(channel).await().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                if (!dest.exists() || dest.length() == 0L) {
                    dest.delete()
                    Log.w(TAG, "Empty audio from watch")
                    return@withContext
                }
                val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val note = app.brainRepository.createAudioNoteFromRecording(
                    relativePath,
                    title = "Watch voice $timeLabel"
                )
                BrainSyncWorker.enqueue(app)
                app.wearActivityStore.record(
                    type = WearActivityEventType.AUDIO,
                    title = note.title,
                    noteId = note.id,
                )
                Log.d(TAG, "Watch audio filed as note ${note.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to receive watch audio", e)
            } finally {
                try {
                    channelClient.close(channel).await()
                } catch (_: Exception) { }
            }
        }
    }
}
