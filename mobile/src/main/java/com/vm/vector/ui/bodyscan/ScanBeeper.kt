package com.vm.vector.ui.bodyscan

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import kotlin.math.sin

/**
 * Short countdown ticks and a slightly longer end beep. Uses ToneGenerator first,
 * then a generated sine if the stream is silent or ToneGenerator fails.
 */
internal class ScanBeeper {
    private var toneGen: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    } catch (_: Exception) {
        null
    }

    fun tick() {
        if (!playTone(ToneGenerator.TONE_PROP_BEEP, 90)) {
            playSine(880.0, 90)
        }
    }

    /** Two quick higher beeps that mark the shutter; blocks until both have played. */
    fun doubleBeep() {
        repeat(2) { i ->
            if (playTone(ToneGenerator.TONE_PROP_BEEP, 80)) {
                Thread.sleep(80)
            } else {
                playSine(1320.0, 80)
            }
            if (i == 0) Thread.sleep(70)
        }
    }

    fun finalBeep() {
        if (!playTone(ToneGenerator.TONE_PROP_ACK, 280)) {
            playSine(660.0, 280)
        }
    }

    fun release() {
        try {
            toneGen?.release()
        } catch (_: Exception) {
        }
        toneGen = null
    }

    private fun playTone(tone: Int, durationMs: Int): Boolean {
        val gen = toneGen ?: return false
        return try {
            gen.stopTone()
            gen.startTone(tone, durationMs)
        } catch (_: Exception) {
            false
        }
    }

    private fun playSine(freqHz: Double, durationMs: Int) {
        val sampleRate = 22050
        val count = (sampleRate * durationMs / 1000).coerceAtLeast(1)
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val envelope = when {
                i < count / 10 -> i.toDouble() / (count / 10)
                i > count * 9 / 10 -> (count - i).toDouble() / (count / 10)
                else -> 1.0
            }
            samples[i] = (sin(2.0 * Math.PI * i * freqHz / sampleRate) * envelope * 28000).toInt().toShort()
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep(durationMs.toLong() + 20)
        } catch (_: Exception) {
        } finally {
            try {
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }
    }
}
