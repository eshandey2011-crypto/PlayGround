package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

enum class BgmStyle(val displayName: String, val description: String) {
    CINEMATIC_AMBIENT(
        "Cinematic Ambient",
        "Calm and atmospheric music - best for films and documentaries"
    ),
    LOFI_CHILL(
        "Lo-Fi Chill Chords",
        "Light and soothing melody - great for vlogs and stories"
    ),
    DOCUMENTARY_ACOUSTIC(
        "Soft Warm Pad (Documentary)",
        "Professional and clear background pad - suitable for presentations"
    ),
    MEDITATION_PEACE(
        "Meditative Drone",
        "Gentle and serene music - ideal for dialogue clarity"
    ),
    UPBEAT_INSPIRING(
        "Upbeat Acoustic & Rhythm",
        "Warm uplifting tempo - best for explainer and tutorial videos"
    ),
    DRAMATIC_SCORE(
        "Dramatic Film Atmosphere",
        "Deep cinematic strings - suitable for intense dialogue scenes"
    )
}

/**
 * Professional Background Music (BGM) Engine for Mixing with AI-Generated Dubbed Audio.
 */
class BgmAudioEngine(private val context: Context? = null) {
    companion object {
        private const val TAG = "BgmAudioEngine"
        private const val SAMPLE_RATE = 44100
        private const val NUM_CHANNELS = 2
    }

    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var isPlaying = false

    @Volatile
    private var targetVolume = 0.35f

    @Volatile
    private var currentVolume = 0.35f

    @Volatile
    private var currentMediaVolume = 0.35f

    @Volatile
    private var isDucked = false

    @Volatile
    private var duckRatio = 0.30f

    @Volatile
    private var isLooping = true

    @Volatile
    private var currentStyle: BgmStyle = BgmStyle.CINEMATIC_AMBIENT

    @Volatile
    var isCustomFileLoaded = false
        private set

    @Volatile
    var customAudioUri: Uri? = null
        private set

    @Volatile
    var customAudioTitle: String = "Preset Synthesizer"
        private set

    @Volatile
    var customAudioDurationMs: Long = 0L
        private set

    @Volatile
    private var seekPositionMs: Long? = null

    private var volumeFadeJob: Job? = null

    fun loadAudioFile(uri: Uri, title: String? = null, durationMs: Long = 0L): Boolean {
        val ctx = context ?: run {
            Log.e(TAG, "Context is null; cannot load audio file URI")
            return false
        }
        try {
            pauseSynth()
            mediaPlayer?.release()
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) {
                    val file = java.io.File(path)
                    java.io.FileInputStream(file).use { fis ->
                        mp.setDataSource(fis.fd)
                    }
                } else {
                    mp.setDataSource(ctx, uri)
                }
            } else {
                var loadedViaAfd = false
                try {
                    val afd = ctx.contentResolver.openAssetFileDescriptor(uri, "r")
                    if (afd != null) {
                        afd.use {
                            if (it.declaredLength < 0) {
                                mp.setDataSource(it.fileDescriptor)
                            } else {
                                mp.setDataSource(it.fileDescriptor, it.startOffset, it.declaredLength)
                            }
                        }
                        loadedViaAfd = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load via AssetFileDescriptor: ${e.message}")
                }
                if (!loadedViaAfd) {
                    mp.setDataSource(ctx, uri)
                }
            }
            mp.isLooping = isLooping
            val effVol = getEffectiveVolume()
            mp.setVolume(effVol, effVol)
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer playback error: what=$what, extra=$extra")
                true
            }
            mp.prepare()
            val actualDuration = if (durationMs > 0L) durationMs else mp.duration.toLong()
            mediaPlayer = mp
            customAudioUri = uri
            customAudioTitle = title ?: (uri.lastPathSegment ?: "Background Music Track")
            customAudioDurationMs = actualDuration
            isCustomFileLoaded = true
            currentMediaVolume = effVol
            if (isPlaying) {
                mp.start()
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load audio file: ${e.message}", e)
            isCustomFileLoaded = false
            return false
        }
    }

    fun clearCustomAudioFile() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer: ${e.message}")
        }
        isCustomFileLoaded = false
        customAudioUri = null
        customAudioTitle = "Preset Synthesizer"
        customAudioDurationMs = 0L
        if (isPlaying) {
            startSynth()
        }
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 4).coerceAtLeast(SAMPLE_RATE * NUM_CHANNELS * 2 / 5)
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack for BGM: ${e.message}")
        }
    }

    fun play() {
        if (isPlaying) return
        isPlaying = true
        if (isCustomFileLoaded && mediaPlayer != null) {
            try {
                val mp = mediaPlayer
                if (mp != null) {
                    val effVol = getEffectiveVolume()
                    mp.setVolume(effVol, effVol)
                    mp.start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting MediaPlayer: ${e.message}")
                startSynth()
            }
        } else {
            startSynth()
        }
    }

    fun pause() {
        isPlaying = false
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing MediaPlayer: ${e.message}")
        }
        pauseSynth()
    }

    fun stop() {
        pause()
        try {
            mediaPlayer?.seekTo(0)
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking MediaPlayer: ${e.message}")
        }
    }

    fun seekTo(positionMs: Long) {
        val mp = mediaPlayer
        if (isCustomFileLoaded && mp != null) {
            try {
                val dur = mp.duration
                if (dur > 0) {
                    val target = if (isLooping) (positionMs % dur).toInt() else positionMs.toInt().coerceAtMost(dur)
                    mp.seekTo(target)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error seeking BGM MediaPlayer: ${e.message}")
            }
        } else {
            seekPositionMs = positionMs
        }
    }

    fun setVolume(volume: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
        applyVolumeSmooth()
    }

    fun setDucking(duck: Boolean) {
        if (isDucked == duck) return
        isDucked = duck
        applyVolumeSmooth()
    }

    fun setDuckRatio(ratio: Float) {
        duckRatio = ratio.coerceIn(0.05f, 0.8f)
        if (isDucked) {
            applyVolumeSmooth()
        }
    }

    fun setLooping(loop: Boolean) {
        isLooping = loop
        try {
            mediaPlayer?.isLooping = loop
        } catch (e: Exception) {
            Log.w(TAG, "Error updating looping state: ${e.message}")
        }
    }

    fun setStyle(style: BgmStyle) {
        currentStyle = style
    }

    fun getEffectiveVolume(): Float {
        val eff = if (isDucked) targetVolume * duckRatio else targetVolume
        return eff.coerceIn(0f, 1f)
    }

    private fun applyVolumeSmooth() {
        val eff = getEffectiveVolume()
        if (isCustomFileLoaded && mediaPlayer != null) {
            val mp = mediaPlayer ?: return
            volumeFadeJob?.cancel()
            volumeFadeJob = scope.launch {
                val startVol = currentMediaVolume
                val steps = 8
                for (step in 1..steps) {
                    val progress = step / steps.toFloat()
                    val v = (startVol + (eff - startVol) * progress).coerceIn(0f, 1f)
                    currentMediaVolume = v
                    try {
                        mp.setVolume(v, v)
                    } catch (e: Exception) {
                        break
                    }
                    delay(18)
                }
                currentMediaVolume = eff
                try {
                    mp.setVolume(eff, eff)
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun startSynth() {
        try {
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                initAudioTrack()
            }
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing AudioTrack: ${e.message}")
        }
        synthJob?.cancel()
        synthJob = scope.launch {
            synthesizeLoop()
        }
    }

    private fun pauseSynth() {
        synthJob?.cancel()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing AudioTrack: ${e.message}")
        }
    }

    private suspend fun synthesizeLoop() {
        val bufferSamples = 2048
        val shortBuffer = ShortArray(bufferSamples * NUM_CHANNELS)
        var sampleIndex = 0L

        val progressionCinematic = listOf(
            floatArrayOf(130.81f, 196.00f, 246.94f, 293.66f, 329.63f, 523.25f),
            floatArrayOf(110.00f, 164.81f, 196.00f, 246.94f, 261.63f, 440.00f),
            floatArrayOf(87.31f, 130.81f, 164.81f, 220.00f, 261.63f, 349.23f),
            floatArrayOf(98.00f, 146.83f, 196.00f, 261.63f, 293.66f, 392.00f)
        )
        val progressionLofi = listOf(
            floatArrayOf(146.83f, 220.00f, 261.63f, 329.63f, 440.00f),
            floatArrayOf(196.00f, 246.94f, 293.66f, 349.23f, 440.00f),
            floatArrayOf(130.81f, 196.00f, 246.94f, 329.63f, 392.00f),
            floatArrayOf(110.00f, 164.81f, 220.00f, 261.63f, 329.63f)
        )
        val progressionAcoustic = listOf(
            floatArrayOf(130.81f, 196.00f, 329.63f, 392.00f),
            floatArrayOf(110.00f, 164.81f, 261.63f, 329.63f),
            floatArrayOf(87.31f, 130.81f, 220.00f, 349.23f),
            floatArrayOf(98.00f, 146.83f, 293.66f, 392.00f)
        )
        val progressionMeditative = listOf(
            floatArrayOf(110.00f, 164.81f, 220.00f, 329.63f, 440.00f),
            floatArrayOf(110.00f, 146.83f, 220.00f, 293.66f, 440.00f)
        )
        val progressionUpbeat = listOf(
            floatArrayOf(130.81f, 164.81f, 196.00f, 261.63f),
            floatArrayOf(98.00f, 123.47f, 146.83f, 196.00f),
            floatArrayOf(110.00f, 130.81f, 164.81f, 220.00f),
            floatArrayOf(87.31f, 110.00f, 130.81f, 174.61f)
        )
        val progressionDramatic = listOf(
            floatArrayOf(110.00f, 130.81f, 164.81f, 220.00f),
            floatArrayOf(87.31f, 110.00f, 130.81f, 174.61f),
            floatArrayOf(73.42f, 110.00f, 146.83f, 220.00f),
            floatArrayOf(82.41f, 123.47f, 164.81f, 246.94f)
        )
        val chordDurationSamples = SAMPLE_RATE * 4.0

        while (scope.isActive && isPlaying && !isCustomFileLoaded) {
            val track = audioTrack
            if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                delay(60)
                continue
            }
            val targetSeek = seekPositionMs
            if (targetSeek != null) {
                sampleIndex = (targetSeek * SAMPLE_RATE / 1000).coerceAtLeast(0L)
                seekPositionMs = null
            }
            val effTarget = getEffectiveVolume()
            val activeStyle = currentStyle
            val activeProgression = when (activeStyle) {
                BgmStyle.CINEMATIC_AMBIENT -> progressionCinematic
                BgmStyle.LOFI_CHILL -> progressionLofi
                BgmStyle.DOCUMENTARY_ACOUSTIC -> progressionAcoustic
                BgmStyle.MEDITATION_PEACE -> progressionMeditative
                BgmStyle.UPBEAT_INSPIRING -> progressionUpbeat
                BgmStyle.DRAMATIC_SCORE -> progressionDramatic
            }
            val totalLoopSamples = (activeProgression.size * chordDurationSamples).toLong()

            for (i in 0 until bufferSamples) {
                currentVolume += (effTarget - currentVolume) * 0.002f
                val loopPos = (sampleIndex % totalLoopSamples)
                val chordIndex = (loopPos / chordDurationSamples).toInt().coerceIn(0, activeProgression.size - 1)
                val posInChord = (loopPos % chordDurationSamples).toDouble()
                val freqs = activeProgression[chordIndex]
                val nextFreqs = activeProgression[(chordIndex + 1) % activeProgression.size]

                val crossfadeWindow = SAMPLE_RATE * 0.8
                val crossfadeFactor = if (posInChord > chordDurationSamples - crossfadeWindow) {
                    (posInChord - (chordDurationSamples - crossfadeWindow)) / crossfadeWindow
                } else {
                    0.0
                }

                val t = sampleIndex.toDouble() / SAMPLE_RATE
                val lfo1 = 1.0 + 0.15 * sin(2 * PI * 0.25 * t)
                val lfo2 = sin(2 * PI * 0.1 * t)

                var monoVoice = 0.0
                for (idx in freqs.indices) {
                    val f = freqs[idx]
                    val osc1 = sin(2 * PI * f * t)
                    val sub = 0.3 * sin(2 * PI * (f * 0.5) * t)
                    val harmonic = 0.2 * sin(2 * PI * (f * 2.0) * t)
                    monoVoice += (osc1 + sub + harmonic) / freqs.size
                }

                if (crossfadeFactor > 0.0) {
                    var nextVoice = 0.0
                    for (idx in nextFreqs.indices) {
                        val f = nextFreqs[idx]
                        val osc1 = sin(2 * PI * f * t)
                        val sub = 0.3 * sin(2 * PI * (f * 0.5) * t)
                        nextVoice += (osc1 + sub) / nextFreqs.size
                    }
                    monoVoice = (1.0 - crossfadeFactor) * monoVoice + crossfadeFactor * nextVoice
                }

                val warmSignal = monoVoice * lfo1 * 0.5
                val left = (warmSignal * (1.0 - 0.2 * lfo2) * currentVolume).coerceIn(-0.95, 0.95)
                val right = (warmSignal * (1.0 + 0.2 * lfo2) * currentVolume).coerceIn(-0.95, 0.95)

                shortBuffer[i * 2] = (left * Short.MAX_VALUE).toInt().toShort()
                shortBuffer[i * 2 + 1] = (right * Short.MAX_VALUE).toInt().toShort()
                sampleIndex++
            }
            try {
                audioTrack?.write(shortBuffer, 0, shortBuffer.size)
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack write exception: ${e.message}")
                break
            }
        }
    }

    fun release() {
        stop()
        volumeFadeJob?.cancel()
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer: ${e.message}")
        }
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack: ${e.message}")
        }
    }
}
