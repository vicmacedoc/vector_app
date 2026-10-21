package com.vm.core.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * MediaRecorder's MPEG-4/AAC files often ship a broken duration (edit list / timescale),
 * so a 15s take can report ~15:35 and only the start is audible. This rewrites AAC
 * access units onto a clean timeline of one frame per sample.
 */
object M4aRepair {

    private const val TAG = "M4aRepair"
    private const val AAC_LC_SAMPLES_PER_FRAME = 1024

    fun repairIfNeeded(file: File): Boolean {
        if (!file.exists() || file.length() < 32L) return false
        val probe = probe(file) ?: return false
        if (!probe.suspect) return false
        return rewrite(file, probe)
    }

    private data class Probe(
        val sampleRate: Int,
        val channelCount: Int,
        val declaredUs: Long,
        val lastSampleUs: Long,
        val suspect: Boolean,
    )

    private fun probe(file: File): Probe? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val track = audioTrack(extractor) ?: return null
            if (hasVideo(extractor)) return null
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (!mime.contains("mp4a") && !mime.contains("aac")) return null
            val sampleRate = format.intOr(MediaFormat.KEY_SAMPLE_RATE, 44_100).coerceAtLeast(1)
            val channels = format.intOr(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
            val declaredUs = format.longOr(MediaFormat.KEY_DURATION, 0L)
            extractor.selectTrack(track)
            extractor.seekTo(Long.MAX_VALUE, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val lastUs = extractor.sampleTime.coerceAtLeast(0L)
            val sizeCeilingUs = if (file.length() > 0L) {
                file.length() * 8L * 1_000_000L / 24_000L
            } else 0L
            val ratioSuspect = declaredUs > 0L && lastUs > 0L &&
                (declaredUs > lastUs * 5 / 4 || lastUs > declaredUs * 5 / 4)
            val sizeSuspect = (declaredUs > sizeCeilingUs * 3 / 2 && declaredUs > 3_000_000L) ||
                (lastUs > sizeCeilingUs * 3 / 2 && lastUs > 3_000_000L)
            Probe(
                sampleRate = sampleRate,
                channelCount = channels,
                declaredUs = declaredUs,
                lastSampleUs = lastUs,
                suspect = ratioSuspect || sizeSuspect || declaredUs <= 0L,
            )
        } catch (e: Exception) {
            Log.w(TAG, "probe failed for ${file.name}", e)
            null
        } finally {
            extractor.release()
        }
    }

    private fun rewrite(file: File, probe: Probe): Boolean {
        val temp = File(file.parentFile, "${file.name}.repair")
        val backup = File(file.parentFile, "${file.name}.bak")
        if (temp.exists()) temp.delete()
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(file.absolutePath)
            val track = audioTrack(extractor) ?: return false
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val clean = MediaFormat.createAudioFormat(
                format.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_AUDIO_AAC,
                probe.sampleRate,
                probe.channelCount,
            )
            format.getByteBuffer("csd-0")?.let { raw ->
                val copy = ByteBuffer.allocate(raw.capacity().coerceAtLeast(raw.remaining()))
                val view = raw.duplicate()
                view.clear()
                copy.put(view)
                copy.flip()
                clean.setByteBuffer("csd-0", copy)
            }
            if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                clean.setInteger(MediaFormat.KEY_BIT_RATE, format.getInteger(MediaFormat.KEY_BIT_RATE))
            }
            val created = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = created
            val muxTrack = created.addTrack(clean)
            created.start()
            val buffer = ByteBuffer.allocate(64 * 1024)
            val info = MediaCodec.BufferInfo()
            var index = 0
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                buffer.position(0)
                buffer.limit(size)
                info.offset = 0
                info.size = size
                info.presentationTimeUs = index * AAC_LC_SAMPLES_PER_FRAME * 1_000_000L / probe.sampleRate
                info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
                created.writeSampleData(muxTrack, buffer, info)
                index++
                extractor.advance()
            }
            created.stop()
            muxer = null
            created.release()
            val computedUs = index * AAC_LC_SAMPLES_PER_FRAME * 1_000_000L / probe.sampleRate
            val reference = listOf(probe.declaredUs, probe.lastSampleUs).filter { it > 0L }.minOrNull()
            val wouldCollapse = index < 2 ||
                (reference != null && computedUs < reference / 3 && index < 8)
            if (wouldCollapse || temp.length() < 32L) {
                temp.delete()
                Log.w(TAG, "refused repair of ${file.name} (samples=$index)")
                return false
            }
            if (backup.exists()) backup.delete()
            if (!file.renameTo(backup)) {
                temp.delete()
                return false
            }
            if (!temp.renameTo(file)) {
                backup.renameTo(file)
                temp.delete()
                return false
            }
            backup.delete()
            Log.i(TAG, "repaired ${file.name}: ${probe.declaredUs / 1000}ms header -> ${computedUs / 1000}ms")
            true
        } catch (e: Exception) {
            Log.w(TAG, "rewrite failed for ${file.name}", e)
            try { muxer?.release() } catch (_: Exception) {}
            temp.delete()
            if (!file.exists() && backup.exists()) backup.renameTo(file)
            false
        } finally {
            extractor.release()
        }
    }

    private fun audioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }

    private fun hasVideo(extractor: MediaExtractor): Boolean =
        (0 until extractor.trackCount).any { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        }

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    private fun MediaFormat.longOr(key: String, fallback: Long): Long =
        if (containsKey(key)) getLong(key) else fallback
}
