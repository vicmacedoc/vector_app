package com.vm.vector.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.vm.core.wear.WearPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Sends recorded voice files to the phone via ChannelClient.
 * Files live under filesDir/outbox/ until successfully sent.
 */
class WearAudioSender(private val context: Context) {

    private val channelClient: ChannelClient get() = Wearable.getChannelClient(context)
    private val nodeClient get() = Wearable.getNodeClient(context)

    fun outboxDir(): File = File(context.filesDir, "outbox").also { it.mkdirs() }

    fun pendingFiles(): List<File> =
        outboxDir().listFiles { f -> f.isFile && f.name.endsWith(".m4a", ignoreCase = true) }
            ?.sortedBy { it.lastModified() }
            ?: emptyList()

    /**
     * Send [file] to the phone. Deletes the file on success.
     * @return true if sent, false if no phone or transfer failed (file kept for retry).
     */
    suspend fun sendFile(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return@withContext false
        }
        try {
            val nodes = nodeClient.connectedNodes.await()
            val localNode = nodeClient.localNode.await()
            val remote = nodes.firstOrNull { it.id != localNode?.id } ?: return@withContext false
            val path = WearPaths.AUDIO_CHANNEL_PREFIX + file.name
            val channel = channelClient.openChannel(remote.id, path).await()
            try {
                channelClient.getOutputStream(channel).await().use { out ->
                    FileInputStream(file).use { input -> input.copyTo(out) }
                }
            } finally {
                try {
                    channelClient.close(channel).await()
                } catch (_: Exception) { }
            }
            file.delete()
            true
        } catch (e: Exception) {
            Log.w(TAG, "sendFile failed for ${file.name}", e)
            false
        }
    }

    /** Retry any leftover outbox recordings. Returns count successfully sent. */
    suspend fun flushOutbox(): Int {
        var sent = 0
        for (file in pendingFiles()) {
            if (sendFile(file)) sent++
        }
        return sent
    }

    companion object {
        private const val TAG = "WearAudioSender"
    }
}
