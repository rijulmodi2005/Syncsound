package com.example.sync

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generates a real 16-bit PCM WAV calibration chime file so the Admin can play
 * a synchronized test tone across all connected Android & Web speakers.
 */
object AudioChimeGenerator {
    private const val SAMPLE_RATE = 44100

    fun ensureTestChimeFile(cacheDir: File): File {
        val file = File(cacheDir, "syncsound_calibration_chime.wav")
        if (file.exists() && file.length() > 1000L) return file

        val durationSec = 1.4
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val pcmBytes = ByteArray(numSamples * 2)
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val note1Env = exp(-t * 4.5) * if (t < 0.01) t / 0.01 else 1.0
            val note1 = sin(2.0 * PI * 523.25 * t) * 0.45 + sin(2.0 * PI * 1046.5 * t) * 0.15

            val note2 = if (t >= 0.28) {
                val t2 = t - 0.28
                val note2Env = exp(-t2 * 3.8) * if (t2 < 0.01) t2 / 0.01 else 1.0
                (sin(2.0 * PI * 659.25 * t2) * 0.45 + sin(2.0 * PI * 783.99 * t2) * 0.30) * note2Env
            } else 0.0

            val sampleVal = ((note1 * note1Env + note2) * Short.MAX_VALUE * 0.75)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            buffer.putShort(sampleVal)
        }

        val wavBytes = wrapPcmInWavHeader(pcmBytes, SAMPLE_RATE, channels = 1, bitsPerSample = 16)
        FileOutputStream(file).use { it.write(wavBytes) }
        return file
    }

    private fun wrapPcmInWavHeader(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val out = ByteArrayOutputStream(44 + pcmData.size)

        fun writeIntLE(v: Int) {
            out.write(v and 0xff)
            out.write((v shr 8) and 0xff)
            out.write((v shr 16) and 0xff)
            out.write((v shr 24) and 0xff)
        }

        fun writeShortLE(v: Int) {
            out.write(v and 0xff)
            out.write((v shr 8) and 0xff)
        }

        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeIntLE(totalDataLen)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeIntLE(16)
        writeShortLE(1) // PCM
        writeShortLE(channels)
        writeIntLE(sampleRate)
        writeIntLE(byteRate)
        writeShortLE(channels * bitsPerSample / 8)
        writeShortLE(bitsPerSample)
        out.write("data".toByteArray(Charsets.US_ASCII))
        writeIntLE(pcmData.size)
        out.write(pcmData)
        return out.toByteArray()
    }
}
