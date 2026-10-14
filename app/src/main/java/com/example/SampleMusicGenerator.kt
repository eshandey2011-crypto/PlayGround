package com.example

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates sample background music WAV files for zero-setup BGM testing and mixing.
 */
object SampleMusicGenerator {
    private const val TAG = "SampleMusicGenerator"
    private const val SAMPLE_RATE = 44100
    private const val NUM_CHANNELS = 2 // Stereo

    fun getOrCreateSampleBgmFile(context: Context, styleName: String = "cinematic_bed"): File? {
        val fileName = "sample_bgm_${styleName}.wav"
        val file = File(context.cacheDir, fileName)
        if (file.exists() && file.length() > 44) {
            return file
        }
        return try {
            generateWavFile(file, styleName)
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate sample BGM WAV: ${e.message}", e)
            null
        }
    }

    private fun generateWavFile(outputFile: File, styleName: String) {
        val durationSeconds = 12.0
        val totalSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val bytesPerSample = 2
        val dataSize = totalSamples * NUM_CHANNELS * bytesPerSample

        val fos = FileOutputStream(outputFile)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(NUM_CHANNELS.toShort())
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * NUM_CHANNELS * bytesPerSample)
        header.putShort((NUM_CHANNELS * bytesPerSample).toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray())
        header.putInt(dataSize)
        fos.write(header.array())

        val (chordFrequencies, tempoBpm, styleType) = when (styleName.lowercase()) {
            "lofi_chill" -> Triple(
                listOf(
                    floatArrayOf(146.83f, 220.00f, 261.63f, 329.63f),
                    floatArrayOf(98.00f, 196.00f, 246.94f, 329.63f),
                    floatArrayOf(130.81f, 196.00f, 246.94f, 329.63f),
                    floatArrayOf(110.00f, 164.81f, 220.00f, 277.18f)
                ),
                80.0,
                1
            )
            "meditation_peace" -> Triple(
                listOf(
                    floatArrayOf(185.00f, 277.18f, 329.63f, 440.00f),
                    floatArrayOf(123.47f, 185.00f, 246.94f, 329.63f),
                    floatArrayOf(164.81f, 246.94f, 311.13f, 392.00f),
                    floatArrayOf(138.59f, 207.65f, 277.18f, 329.63f)
                ),
                50.0,
                2
            )
            "documentary_acoustic" -> Triple(
                listOf(
                    floatArrayOf(98.00f, 146.83f, 196.00f, 246.94f, 293.66f),
                    floatArrayOf(82.41f, 123.47f, 164.81f, 196.00f, 293.66f),
                    floatArrayOf(130.81f, 164.81f, 196.00f, 246.94f, 293.66f),
                    floatArrayOf(146.83f, 220.00f, 293.66f, 369.99f)
                ),
                100.0,
                3
            )
            else -> Triple(
                listOf(
                    floatArrayOf(130.81f, 196.00f, 246.94f, 293.66f, 329.63f),
                    floatArrayOf(110.00f, 164.81f, 196.00f, 246.94f, 261.63f),
                    floatArrayOf(87.31f, 130.81f, 164.81f, 220.00f, 261.63f),
                    floatArrayOf(98.00f, 146.83f, 196.00f, 261.63f, 293.66f)
                ),
                65.0,
                0
            )
        }

        val chordDuration = totalSamples / chordFrequencies.size
        val sampleBuffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until totalSamples) {
            val chordIndex = (i / chordDuration).coerceIn(0, chordFrequencies.size - 1)
            val freqs = chordFrequencies[chordIndex]
            val t = i.toDouble() / SAMPLE_RATE

            val chordLocalSample = i % chordDuration
            val chordProgress = chordLocalSample.toDouble() / chordDuration
            val chordEnvelope = sin(PI * chordProgress.coerceIn(0.0, 1.0))
            val lfoStereo = sin(2 * PI * 0.15 * t)
            val tremolo = 0.75 + 0.25 * sin(2 * PI * (tempoBpm / 60.0) * t)

            var voice = 0.0
            when (styleType) {
                1 -> {
                    for ((idx, f) in freqs.withIndex()) {
                        val harmonic = sin(2 * PI * f * t)
                        val overtone = 0.3 * sin(2 * PI * f * 2.0 * t)
                        val sub = 0.4 * sin(2 * PI * (f * 0.5) * t)
                        val detune = 0.15 * sin(2 * PI * (f * 1.003) * t)
                        voice += (harmonic + overtone + sub + detune) / (freqs.size * (1.0 + idx * 0.15))
                    }
                }
                2 -> {
                    for (f in freqs) {
                        val fundamental = sin(2 * PI * f * t)
                        val fifthHarmonic = 0.4 * sin(2 * PI * f * 1.5 * t)
                        val bellShimmer = 0.2 * sin(2 * PI * f * 3.0 * t)
                        voice += (fundamental + fifthHarmonic + bellShimmer) / freqs.size
                    }
                }
                3 -> {
                    val arpeggioRate = (tempoBpm / 60.0) * 2.0
                    val arpStep = ((t * arpeggioRate).toInt()) % freqs.size
                    val arpFreq = freqs[arpStep]
                    val arpVoice = 0.5 * sin(2 * PI * arpFreq * t)
                    var padVoice = 0.0
                    for (f in freqs) {
                        padVoice += 0.2 * sin(2 * PI * f * t)
                    }
                    voice = (arpVoice + padVoice)
                }
                else -> {
                    for (f in freqs) {
                        val osc = sin(2 * PI * f * t)
                        val sub = 0.40 * sin(2 * PI * (f * 0.5) * t)
                        val shimmer = 0.20 * sin(2 * PI * (f * 2.0) * t)
                        voice += (osc + sub + shimmer) / freqs.size
                    }
                }
            }

            val masterGain = 0.50 * (0.6 + 0.4 * chordEnvelope) * tremolo
            val left = (voice * (1.0 - 0.25 * lfoStereo) * masterGain).coerceIn(-0.95, 0.95)
            val right = (voice * (1.0 + 0.25 * lfoStereo) * masterGain).coerceIn(-0.95, 0.95)

            val leftShort = (left * Short.MAX_VALUE).toInt().toShort()
            val rightShort = (right * Short.MAX_VALUE).toInt().toShort()

            sampleBuffer.putShort(leftShort)
            sampleBuffer.putShort(rightShort)

            if (!sampleBuffer.hasRemaining()) {
                fos.write(sampleBuffer.array())
                sampleBuffer.clear()
            }
        }

        if (sampleBuffer.position() > 0) {
            fos.write(sampleBuffer.array(), 0, sampleBuffer.position())
        }
        fos.flush()
        fos.close()
    }
}
