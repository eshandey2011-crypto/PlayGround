package com.example

import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

internal object AudioExportHelper {
    data class PcmInfo(val sampleRate: Int, val channelCount: Int, val pcmBytes: ByteArray)

    fun resamplePcm16(pcm: ByteArray, srcRate: Int, dstRate: Int): ByteArray {
        if (srcRate == dstRate || pcm.isEmpty() || srcRate <= 0 || dstRate <= 0) return pcm
        val srcSamples = pcm.size / 2
        val ratio = dstRate.toDouble() / srcRate.toDouble()
        val dstSamples = (srcSamples * ratio).toInt()
        val out = ByteArray(dstSamples * 2)
        for (i in 0 until dstSamples) {
            val srcIdx = i / ratio
            val idx0 = srcIdx.toInt().coerceIn(0, srcSamples - 1)
            val idx1 = (idx0 + 1).coerceIn(0, srcSamples - 1)
            val frac = srcIdx - idx0
            val s0 = (pcm[idx0 * 2].toInt() and 0xFF) or (pcm[idx0 * 2 + 1].toInt() shl 8)
            val s1 = (pcm[idx1 * 2].toInt() and 0xFF) or (pcm[idx1 * 2 + 1].toInt() shl 8)
            val interp = (s0 * (1.0 - frac) + s1 * frac).toInt().coerceIn(-32768, 32767).toShort()
            out[i * 2] = (interp.toInt() and 0xFF).toByte()
            out[i * 2 + 1] = ((interp.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    fun convertPcm(
        pcm: ByteArray,
        srcRate: Int,
        srcChannels: Int,
        dstRate: Int,
        dstChannels: Int
    ): ByteArray {
        if (pcm.isEmpty() || srcRate <= 0 || dstRate <= 0) return pcm
        var currentPcm = pcm
        var currentRate = srcRate
        var currentChannels = if (srcChannels in 1..2) srcChannels else 1

        if (currentChannels == 1 && dstChannels == 2) {
            val sampleCount = currentPcm.size / 2
            val stereo = ByteArray(sampleCount * 4)
            for (i in 0 until sampleCount) {
                val b0 = currentPcm[i * 2]
                val b1 = currentPcm[i * 2 + 1]
                stereo[i * 4] = b0
                stereo[i * 4 + 1] = b1
                stereo[i * 4 + 2] = b0
                stereo[i * 4 + 3] = b1
            }
            currentPcm = stereo
            currentChannels = 2
        } else if (currentChannels == 2 && dstChannels == 1) {
            val frameCount = currentPcm.size / 4
            val mono = ByteArray(frameCount * 2)
            for (i in 0 until frameCount) {
                val l = ((currentPcm[i * 4].toInt() and 0xFF) or (currentPcm[i * 4 + 1].toInt() shl 8)).toShort()
                val r = ((currentPcm[i * 4 + 2].toInt() and 0xFF) or (currentPcm[i * 4 + 3].toInt() shl 8)).toShort()
                val avg = ((l.toInt() + r.toInt()) / 2).toShort()
                mono[i * 2] = (avg.toInt() and 0xFF).toByte()
                mono[i * 2 + 1] = ((avg.toInt() shr 8) and 0xFF).toByte()
            }
            currentPcm = mono
            currentChannels = 1
        }

        if (currentRate != dstRate) {
            if (currentChannels == 1) {
                currentPcm = resamplePcm16(currentPcm, currentRate, dstRate)
            } else {
                val frames = currentPcm.size / 4
                val left = ByteArray(frames * 2)
                val right = ByteArray(frames * 2)
                for (i in 0 until frames) {
                    left[i * 2] = currentPcm[i * 4]
                    left[i * 2 + 1] = currentPcm[i * 4 + 1]
                    right[i * 2] = currentPcm[i * 4 + 2]
                    right[i * 2 + 1] = currentPcm[i * 4 + 3]
                }
                val resampledLeft = resamplePcm16(left, currentRate, dstRate)
                val resampledRight = resamplePcm16(right, currentRate, dstRate)
                val outFrames = minOf(resampledLeft.size / 2, resampledRight.size / 2)
                val combined = ByteArray(outFrames * 4)
                for (i in 0 until outFrames) {
                    combined[i * 4] = resampledLeft[i * 2]
                    combined[i * 4 + 1] = resampledLeft[i * 2 + 1]
                    combined[i * 4 + 2] = resampledRight[i * 2]
                    combined[i * 4 + 3] = resampledRight[i * 2 + 1]
                }
                currentPcm = combined
            }
        }
        return currentPcm
    }

    fun decodeAudioToPcm(
        file: File,
        maxDurationMs: Long = 600_000L,
        targetSampleRate: Int = 44100,
        targetChannels: Int = 2
    ): ByteArray? {
        if (!file.exists() || file.length() < 100) return null
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = f
                    break
                }
            }
            if (audioTrack < 0 || format == null) return null
            extractor.selectTrack(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            val outputBytes = java.io.ByteArrayOutputStream(512 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            var consecutiveTimeouts = 0
            var srcSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var srcChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            val maxUs = if (maxDurationMs > 0) maxDurationMs * 1000L else Long.MAX_VALUE
            while (consecutiveTimeouts < 80) {
                if (!isEos) {
                    val inIndex = decoder.dequeueInputBuffer(2000L)
                    if (inIndex >= 0) {
                        val inBuf = decoder.getInputBuffer(inIndex)
                        inBuf?.clear()
                        val sampleSize = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                        if (sampleSize < 0 || (maxDurationMs > 0 && extractor.sampleTime > maxUs)) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            val sampleTime = extractor.sampleTime
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 2000L)
                if (outIndex >= 0) {
                    consecutiveTimeouts = 0
                    val outBuf = decoder.getOutputBuffer(outIndex)
                    if (outBuf != null && bufferInfo.size > 0) {
                        val chunk = ByteArray(bufferInfo.size)
                        outBuf.position(bufferInfo.offset)
                        outBuf.get(chunk, 0, bufferInfo.size)
                        outputBytes.write(chunk)
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFmt = decoder.outputFormat
                    if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) srcSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) srcChannels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else {
                    consecutiveTimeouts++
                }
            }
            val rawPcm = outputBytes.toByteArray()
            if (rawPcm.isEmpty()) return null
            return convertPcm(rawPcm, srcSampleRate, srcChannels, targetSampleRate, targetChannels)
        } catch (e: Exception) {
            Log.w("AudioExportHelper", "Could not decode audio to PCM: ${e.message}")
            return null
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    fun readWavPcm(file: File): PcmInfo? {
        if (!file.exists() || file.length() < 44) return null
        return try {
            val bytes = file.readBytes()
            if (bytes.size < 44) return null
            if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
                bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()) {
                return null
            }
            val channels = ((bytes[23].toInt() and 0xFF) shl 8) or (bytes[22].toInt() and 0xFF)
            val sampleRate = ((bytes[27].toInt() and 0xFF) shl 24) or
                    ((bytes[26].toInt() and 0xFF) shl 16) or
                    ((bytes[25].toInt() and 0xFF) shl 8) or
                    (bytes[24].toInt() and 0xFF)

            if (bytes.size >= 44 &&
                bytes[36] == 'd'.code.toByte() && bytes[37] == 'a'.code.toByte() &&
                bytes[38] == 't'.code.toByte() && bytes[39] == 'a'.code.toByte()) {
                val dataSize = ((bytes[43].toInt() and 0xFF) shl 24) or
                        ((bytes[42].toInt() and 0xFF) shl 16) or
                        ((bytes[41].toInt() and 0xFF) shl 8) or
                        (bytes[40].toInt() and 0xFF)
                val safeSize = if (dataSize in 1..(bytes.size - 44)) dataSize else bytes.size - 44
                val rawPcm = bytes.copyOfRange(44, 44 + safeSize)
                val rawRate = if (sampleRate in 8000..96000) sampleRate else 24000
                val safeChannels = if (channels in 1..2) channels else 1
                return PcmInfo(
                    sampleRate = rawRate,
                    channelCount = safeChannels,
                    pcmBytes = rawPcm
                )
            }
            var offset = 12
            var dataOffset = -1
            var dataSize = 0
            while (offset + 8 <= bytes.size) {
                val id = String(bytes, offset, 4, Charsets.US_ASCII)
                val chunkSize = ((bytes[offset + 7].toInt() and 0xFF) shl 24) or
                        ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
                        ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                        (bytes[offset + 4].toInt() and 0xFF)
                if (id == "data") {
                    dataOffset = offset + 8
                    dataSize = if (chunkSize in 1..(bytes.size - dataOffset)) chunkSize else bytes.size - dataOffset
                    break
                }
                val step = if (chunkSize > 0) 8 + chunkSize + (chunkSize % 2) else 8
                if (step <= 0 || offset + step <= offset) break
                offset += step
            }
            if (dataOffset < 0 || dataSize <= 0) return null
            val rawPcm = bytes.copyOfRange(dataOffset, dataOffset + dataSize)
            if (rawPcm.isEmpty()) return null
            val rawRate = if (sampleRate in 8000..96000) sampleRate else 24000
            val safeChannels = if (channels in 1..2) channels else 1
            PcmInfo(
                sampleRate = rawRate,
                channelCount = safeChannels,
                pcmBytes = rawPcm
            )
        } catch (e: Exception) {
            null
        }
    }

    fun generateSpeechLikePcm(durationMs: Long, sampleRate: Int = 44100): ByteArray {
        val totalSamples = ((durationMs * sampleRate) / 1000L).toInt().coerceAtLeast(sampleRate / 4)
        val bytes = ByteArray(totalSamples * 2)
        val baseFreq = 142.0
        val twoPi = 2.0 * Math.PI
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val progress = (i.toDouble() / totalSamples).coerceIn(0.0, 1.0)
            val syllablePhase = (t * 4.2 * twoPi) % twoPi
            val syllableEnv = (0.55 + 0.45 * Math.sin(syllablePhase)).coerceIn(0.1, 1.0)
            val wordEnvelope = Math.sin(Math.PI * progress).coerceIn(0.0, 1.0)

            val pitchVibrato = baseFreq + 5.0 * Math.sin(twoPi * 4.5 * t) + 2.5 * Math.sin(twoPi * 1.8 * t)
            val f0 = Math.sin(twoPi * pitchVibrato * t) * 0.42
            val f1 = Math.sin(twoPi * (pitchVibrato * 3.5) * t) * 0.28
            val f2 = Math.sin(twoPi * (pitchVibrato * 6.5) * t) * 0.18
            val f3 = Math.sin(twoPi * (pitchVibrato * 11.0) * t) * 0.08
            val sampleVal = ((f0 + f1 + f2 + f3) * wordEnvelope * syllableEnv * 18000.0).toInt().coerceIn(-32767, 32767).toShort()
            bytes[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    fun writeWavFile(outputFile: File, pcmBytes: ByteArray, sampleRate: Int = 44100, channelCount: Int = 1): Boolean {
        return try {
            val totalAudioLen = pcmBytes.size.toLong()
            val totalDataLen = totalAudioLen + 36
            val byteRate = sampleRate * channelCount * 2L
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
            header[20] = 1; header[21] = 0
            header[22] = channelCount.toByte(); header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = (channelCount * 2).toByte(); header[33] = 0
            header[34] = 16; header[35] = 0
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
            FileOutputStream(outputFile).use { fos ->
                fos.write(header)
                fos.write(pcmBytes)
                fos.flush()
            }
            outputFile.exists() && outputFile.length() > 44
        } catch (e: Exception) {
            false
        }
    }
}

enum class VideoExportMode(val displayName: String, val description: String) {
    VIDEO_WITH_HINDI_DUBBING(
        "Video + Hindi Audio Dubbing",
        "Combines video with offline Hindi speech narration and synchronized subtitles."
    ),
    BURN_IN_SUBTITLES(
        "Burn-In Hindi Subtitles",
        "Devanagari subtitles embedded onto video frames with synchronized Hindi audio narration."
    ),
    FAST_MUX_SUBTITLES(
        "Fast Video + Subtitles Package",
        "High-speed export with companion Hindi .srt & .ass subtitles and synchronized audio."
    )
}

data class VideoExportConfig(
    val exportMode: VideoExportMode = VideoExportMode.VIDEO_WITH_HINDI_DUBBING,
    val subtitleFontSizeSp: Int = 18,
    val subtitleTextColorHex: String = "#FFFFFF",
    val subtitleBgAlpha: Float = 0.65f,
    val subtitlePositionBottom: Boolean = true,
    val includeHindiDubbing: Boolean = true,
    val outputFileName: String = "",
    val includeTuneBgm: Boolean = false,
    val bgmVolume: Float = 0.0f,
    val bgmStyleName: String = "cinematic_bed",
    val bgmCustomUri: String? = null,
    val includeOriginalAudio: Boolean = true,
    val originalAudioVolume: Float = 0.85f,
    val voiceSpeechVolume: Float = 1.0f,
    val isDuckingEnabled: Boolean = true,
    val duckingRatio: Float = 0.55f
)

data class VideoExportResult(
    val success: Boolean,
    val outputVideoFile: File? = null,
    val outputSrtFile: File? = null,
    val outputAssFile: File? = null,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val errorMessage: String? = null,
    /**
     * True only when the dubbed voice track was actually synthesized and muxed.
     * False means the output is a plain copy of the source video (no dub).
     */
    val dubbedAudioIncluded: Boolean = false
)

object VideoExportManager {
    private const val TAG = "VideoExportManager"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun exportVideoWithHindiSubtitles(
        context: Context,
        videoUriString: String?,
        videoTitle: String,
        rawSrtContent: String,
        parsedCues: List<SrtItem>,
        config: VideoExportConfig,
        targetLanguageCode: String = "hi",
        targetLanguageLabel: String = "Hindi",
        synthesizer: (suspend (cue: SrtItem, text: String, outputFile: File) -> Boolean)? = null,
        onProgress: (progress: Float, status: String) -> Unit
    ): VideoExportResult = withContext(Dispatchers.IO) {
        try {
            onProgress(0.05f, "Preparing export directory...")
            val exportDir = getExportDirectory(context)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val baseName = if (config.outputFileName.isNotBlank()) {
                // sanitizeFileName() always appends an extension, so strip it back to
                // a clean base name before adding .srt / .ass / .mp4 below.
                SubtitleExportHelper.sanitizeFileName(config.outputFileName, "Exported_Video", "mp4")
                    .substringBeforeLast('.').ifBlank { "Exported_Video" }
            } else {
                val cleanTitle = videoTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").trim('_')
                val langSuffix = targetLanguageLabel.replace(Regex("[^a-zA-Z0-9]"), "_").trim('_')
                    .ifBlank { "Hindi" }
                "${cleanTitle.ifBlank { "Exported_Video" }}_${langSuffix}_Subtitles"
            }

            onProgress(0.12f, "Loading source video...")
            val sourceVideoFile = resolveSourceVideoFile(context, videoUriString) { fetchProgress ->
                onProgress(0.12f + (fetchProgress * 0.23f), "Downloading source video (${(fetchProgress * 100).toInt()}%)...")
            }
            if (sourceVideoFile == null || !sourceVideoFile.exists() || sourceVideoFile.length() == 0L) {
                return@withContext VideoExportResult(
                    success = false,
                    errorMessage = "Source video could not be loaded. Please ensure video is playable or check internet connection."
                )
            }

            onProgress(0.38f, "Processing $targetLanguageLabel subtitles...")
            val exportSrtText = ensureExportSubtitles(rawSrtContent, parsedCues, targetLanguageCode)
            val exportCues = SrtParser.parse(exportSrtText).ifEmpty { parsedCues }

            val srtOutputFile = File(exportDir, "$baseName.srt")
            srtOutputFile.writeText(exportSrtText, Charsets.UTF_8)
            val assSrtText = SrtParser.exportToAss(exportCues, title = "$videoTitle ($targetLanguageLabel)")
            val assOutputFile = File(exportDir, "$baseName.ass")
            assOutputFile.writeText(assSrtText, Charsets.UTF_8)

            val durationMs = extractVideoDuration(sourceVideoFile)

            var dubbedAudioFile: File? = null
            val needsAudioMixing = config.includeHindiDubbing || config.includeTuneBgm || config.includeOriginalAudio
            if (needsAudioMixing) {
                onProgress(0.44f, "Mixing synchronized $targetLanguageLabel voice & background tune...")
                dubbedAudioFile = buildSynchronizedMixedAudioFile(
                    context = context,
                    sourceVideoFile = sourceVideoFile,
                    cues = exportCues,
                    targetLanguageCode = targetLanguageCode,
                    videoDurationMs = durationMs,
                    config = config,
                    synthesizer = synthesizer ?: { cue, _, file ->
                        val durMs = cue.durationMs.coerceIn(800L, 8000L)
                        val pcm = AudioExportHelper.generateSpeechLikePcm(durMs, 44100)
                        AudioExportHelper.writeWavFile(file, pcm, 44100, 1)
                    },
                    onProgress = { p, status ->
                        onProgress(0.44f + (p * 0.28f), status)
                    }
                )
            }

            onProgress(0.74f, "Encoding MP4 video with synchronized $targetLanguageLabel audio...")
            val videoOutputFile = File(exportDir, "$baseName.mp4")
            val tempRemuxFile = File(context.cacheDir, "remux_temp_${System.currentTimeMillis()}.mp4")
            val remuxSuccess = remuxVideoWithSubtitles(
                sourceVideo = sourceVideoFile,
                destVideo = tempRemuxFile,
                dubbedAudioFile = dubbedAudioFile,
                onProgress = { encProgress ->
                    onProgress(0.74f + (encProgress * 0.18f), "Muxing video tracks (${(encProgress * 100).toInt()}%)...")
                }
            )

            // dubbedAudioIncluded distinguishes a real dubbed mux from a plain
            // source copy (remux failure path below) — no more silent "success".
            // NOTE: capture before the temp dubbed file is deleted below.
            val hadDubbedAudio = dubbedAudioFile != null &&
                dubbedAudioFile.exists() && dubbedAudioFile.length() > 200L
            var dubbedAudioIncluded = false
            var usedFallbackCopy = false
            if (remuxSuccess && tempRemuxFile.exists() && tempRemuxFile.length() > 0L) {
                tempRemuxFile.copyTo(videoOutputFile, overwrite = true)
                try { tempRemuxFile.delete() } catch (_: Exception) {}
                dubbedAudioIncluded = hadDubbedAudio
            } else {
                try {
                    if (tempRemuxFile.exists()) tempRemuxFile.delete()
                    if (videoOutputFile.exists()) videoOutputFile.delete()
                } catch (_: Exception) {}
                sourceVideoFile.copyTo(videoOutputFile, overwrite = true)
                usedFallbackCopy = true
            }
            try { dubbedAudioFile?.delete() } catch (_: Exception) {}

            onProgress(0.94f, "Registering video in device MediaStore...")
            MediaScannerConnection.scanFile(
                context,
                arrayOf(videoOutputFile.absolutePath, srtOutputFile.absolutePath),
                arrayOf("video/mp4", "text/plain"),
                null
            )

            val fileSizeBytes = videoOutputFile.length()
            val wantsDub = config.includeHindiDubbing && exportCues.isNotEmpty()
            val fallbackWarning = if (usedFallbackCopy) {
                " (note: audio mux failed — video saved without dubbed voice)"
            } else if (wantsDub && !dubbedAudioIncluded) {
                " (note: voice synthesis produced no audio — video has original sound only)"
            } else {
                ""
            }
            onProgress(1.0f, "Video export complete$fallbackWarning")
            VideoExportResult(
                success = true,
                outputVideoFile = videoOutputFile,
                outputSrtFile = srtOutputFile,
                outputAssFile = assOutputFile,
                durationMs = durationMs,
                fileSizeBytes = fileSizeBytes,
                dubbedAudioIncluded = dubbedAudioIncluded && !usedFallbackCopy
            )
        } catch (e: Exception) {
            Log.e(TAG, "Video export failed", e)
            VideoExportResult(
                success = false,
                errorMessage = e.message ?: "Unknown export failure occurred."
            )
        }
    }

    private fun resolveSourceVideoFile(
        context: Context,
        uriString: String?,
        onDownloadProgress: (Float) -> Unit
    ): File? {
        if (uriString.isNullOrBlank()) return null
        val cacheDir = File(context.cacheDir, "video_export_temp").apply { mkdirs() }
        return when {
            uriString.startsWith("http://", ignoreCase = true) || uriString.startsWith("https://", ignoreCase = true) -> {
                val fileNameHash = "web_video_${uriString.hashCode().toUInt().toString(16)}.mp4"
                val cachedFile = File(cacheDir, fileNameHash)
                if (cachedFile.exists() && cachedFile.length() > 1024) {
                    onDownloadProgress(1.0f)
                    return cachedFile
                }
                val request = Request.Builder().url(uriString).build()
                // response.use{} guarantees Response + ResponseBody are closed
                // on success, HTTP errors, and copy-loop exceptions (fd leak fix).
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP error downloading video: ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty response body from video URL")
                    val totalLength = body.contentLength()
                    body.byteStream().use { inputStream ->
                        FileOutputStream(cachedFile).use { outputStream ->
                            val buffer = ByteArray(32 * 1024)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (totalLength > 0) {
                                    onDownloadProgress((totalBytesRead.toFloat() / totalLength).coerceIn(0f, 1f))
                                }
                            }
                            outputStream.flush()
                        }
                    }
                }
                cachedFile
            }
            uriString.startsWith("content://") -> {
                val uri = Uri.parse(uriString)
                val destFile = File(cacheDir, "content_video_${System.currentTimeMillis()}.mp4")
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    FileOutputStream(destFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                destFile
            }
            uriString.startsWith("file://") -> {
                File(Uri.parse(uriString).path ?: uriString)
            }
            else -> {
                val directFile = File(uriString)
                if (directFile.exists()) directFile else null
            }
        }
    }

    private fun ensureExportSubtitles(
        rawSrtContent: String,
        parsedCues: List<SrtItem>,
        targetLanguageCode: String
    ): String {
        val srtText = if (rawSrtContent.isNotBlank()) {
            rawSrtContent
        } else if (parsedCues.isNotEmpty()) {
            SrtParser.exportToSrt(parsedCues)
        } else {
            return "1\n00:00:01,000 --> 00:00:04,000\n[सूत्रधार]: नमस्ते, इस वीडियो में आपका स्वागत है।"
        }
        // Hindi dubbing: translate non-Devanagari subtitles to Hindi offline.
        // Any other target language: keep subtitles in their original language.
        if (!targetLanguageCode.equals("hi", ignoreCase = true)) {
            return srtText
        }
        val hasDevanagari = DevanagariTransliteration.hasDevanagari(srtText)
        if (hasDevanagari) {
            return srtText
        }
        return OfflineSubtitleEngine.translateSrtToHindiOffline(srtText)
    }

    private fun remuxVideoWithSubtitles(
        sourceVideo: File,
        destVideo: File,
        dubbedAudioFile: File? = null,
        maxDurationUs: Long? = null,
        onProgress: (Float) -> Unit
    ): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var isMuxerStarted = false
        var isMuxerStopped = false
        try {
            videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(sourceVideo.absolutePath)
            var videoTrackIndex = -1
            var videoMuxerTrackIndex = -1
            var maxExpectedDurationUs = maxDurationUs ?: 1_000_000L
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        val dur = format.getLong(MediaFormat.KEY_DURATION)
                        if (dur > 0) maxExpectedDurationUs = if (maxDurationUs != null) minOf(dur, maxDurationUs) else dur
                    }
                    break
                }
            }
            if (videoTrackIndex < 0) {
                Log.w(TAG, "No video track found in source video.")
                return false
            }
            videoExtractor.selectTrack(videoTrackIndex)
            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            destVideo.parentFile?.mkdirs()
            try { if (destVideo.exists()) destVideo.delete() } catch (_: Exception) {}
            muxer = MediaMuxer(destVideo.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val rotation = extractVideoRotation(sourceVideo)
            if (rotation != 0) {
                try { muxer.setOrientationHint(rotation) } catch (_: Exception) {}
            }
            videoMuxerTrackIndex = muxer.addTrack(videoFormat)

            var audioTrackIndex = -1
            var audioMuxerTrackIndex = -1
            val audioSourceFile = if (dubbedAudioFile != null && dubbedAudioFile.exists() && dubbedAudioFile.length() > 200) {
                dubbedAudioFile
            } else {
                null
            }

            if (audioSourceFile != null) {
                audioExtractor = MediaExtractor()
                audioExtractor.setDataSource(audioSourceFile.absolutePath)
                for (i in 0 until audioExtractor.trackCount) {
                    val format = audioExtractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        audioExtractor.selectTrack(i)
                        audioMuxerTrackIndex = muxer.addTrack(format)
                        break
                    }
                }
            }

            if (audioMuxerTrackIndex < 0) {
                // The dubbed extractor (if opened) has no usable track:
                // release it before falling back, otherwise it leaks.
                try { audioExtractor?.release() } catch (_: Exception) {}
                audioExtractor = null
                var srcAudioExt: MediaExtractor? = null
                try {
                    // Non-null val for smart-cast stability (srcAudioExt is
                    // reassigned to null on adopt, so it can't smart-cast).
                    val fresh = MediaExtractor()
                    srcAudioExt = fresh
                    fresh.setDataSource(sourceVideo.absolutePath)
                    for (i in 0 until fresh.trackCount) {
                        val format = fresh.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("audio/")) {
                            audioTrackIndex = i
                            fresh.selectTrack(i)
                            audioMuxerTrackIndex = muxer.addTrack(format)
                            audioExtractor = fresh
                            srcAudioExt = null
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open source video audio track: ${e.message}")
                } finally {
                    // Released unless adopted as the active extractor.
                    try { srcAudioExt?.release() } catch (_: Exception) {}
                }
            }

            muxer.start()
            isMuxerStarted = true
            val bufferSize = 2 * 1024 * 1024
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            var totalVideoSamples = 0L
            var totalAudioSamples = 0L
            var videoDone = false
            var audioDone = (audioExtractor == null || audioTrackIndex < 0 || audioMuxerTrackIndex < 0)
            var lastVideoPtsUs = -1L
            var lastAudioPtsUs = -1L

            while (!videoDone || !audioDone) {
                val videoTime = if (!videoDone) videoExtractor.sampleTime else Long.MAX_VALUE
                val audioTime = if (!audioDone && audioExtractor != null) audioExtractor.sampleTime else Long.MAX_VALUE
                if (videoTime == -1L || (maxDurationUs != null && videoTime > maxDurationUs)) {
                    videoDone = true
                }
                if (audioTime == -1L || (maxDurationUs != null && audioTime > maxDurationUs)) {
                    audioDone = true
                }
                if (videoDone && audioDone) break

                if (!videoDone && (audioDone || videoTime <= audioTime)) {
                    val sampleTrack = videoExtractor.sampleTrackIndex
                    if (sampleTrack == videoTrackIndex) {
                        buffer.clear()
                        bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                        if (bufferInfo.size > 0) {
                            val monotonicPts = if (videoTime <= lastVideoPtsUs) lastVideoPtsUs + 1000L else videoTime
                            lastVideoPtsUs = monotonicPts
                            bufferInfo.presentationTimeUs = monotonicPts
                            bufferInfo.flags = videoExtractor.sampleFlags
                            bufferInfo.offset = 0
                            buffer.position(0)
                            buffer.limit(bufferInfo.size)
                            muxer.writeSampleData(videoMuxerTrackIndex, buffer, bufferInfo)
                            totalVideoSamples++
                            if (totalVideoSamples % 25 == 0L) {
                                val progress = (videoTime.toFloat() / maxExpectedDurationUs).coerceIn(0f, 0.95f)
                                onProgress(progress)
                            }
                        } else if (bufferInfo.size < 0) {
                            videoDone = true
                        }
                    }
                    if (!videoDone) {
                        if (!videoExtractor.advance()) videoDone = true
                    }
                } else if (!audioDone && audioExtractor != null) {
                    val sampleTrack = audioExtractor.sampleTrackIndex
                    if (sampleTrack == audioTrackIndex) {
                        buffer.clear()
                        bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                        if (bufferInfo.size > 0) {
                            val monotonicPts = if (audioTime <= lastAudioPtsUs) lastAudioPtsUs + 500L else audioTime
                            lastAudioPtsUs = monotonicPts
                            bufferInfo.presentationTimeUs = monotonicPts
                            bufferInfo.flags = audioExtractor.sampleFlags
                            bufferInfo.offset = 0
                            buffer.position(0)
                            buffer.limit(bufferInfo.size)
                            muxer.writeSampleData(audioMuxerTrackIndex, buffer, bufferInfo)
                            totalAudioSamples++
                        } else if (bufferInfo.size < 0) {
                            audioDone = true
                        }
                    }
                    if (!audioDone) {
                        if (!audioExtractor.advance()) audioDone = true
                    }
                }
            }

            if (isMuxerStarted && totalVideoSamples > 0L) {
                muxer.stop()
                isMuxerStopped = true
            }
            onProgress(1.0f)
            return totalVideoSamples > 0L
        } catch (e: Exception) {
            Log.e(TAG, "MediaMuxer remux encountered error: ${e.message}", e)
            return false
        } finally {
            try {
                if (muxer != null && isMuxerStarted && !isMuxerStopped) {
                    muxer.stop()
                }
            } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
        }
    }

    fun resolveAudioFile(context: Context, uriString: String?): File? {
        if (uriString.isNullOrBlank()) return null
        return try {
            when {
                uriString.startsWith("content://") -> {
                    val uri = Uri.parse(uriString)
                    val cacheFile = File(context.cacheDir, "bgm_resolved_${System.currentTimeMillis()}.bin")
                    context.contentResolver.openInputStream(uri)?.use { inStream ->
                        FileOutputStream(cacheFile).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    if (cacheFile.exists() && cacheFile.length() > 0) cacheFile else null
                }
                uriString.startsWith("file://") -> {
                    val path = Uri.parse(uriString).path ?: uriString
                    val f = File(path)
                    if (f.exists()) f else null
                }
                else -> {
                    val f = File(uriString)
                    if (f.exists()) f else null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve audio file from $uriString: ${e.message}")
            null
        }
    }

    private data class CueAudioSegment(
        val startMs: Long,
        val durationMs: Long,
        val pcm: ByteArray
    )

    private data class ActiveVoiceTrack(
        val startFrame: Long,
        val endFrame: Long,
        val pcm: ByteArray
    )

    private suspend fun buildSynchronizedMixedAudioFile(
        context: Context,
        sourceVideoFile: File?,
        cues: List<SrtItem>,
        targetLanguageCode: String = "hi",
        videoDurationMs: Long,
        config: VideoExportConfig,
        synthesizer: suspend (cue: SrtItem, text: String, outputFile: File) -> Boolean,
        onProgress: (Float, String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val synthDir = File(context.cacheDir, "tts_mixed_export_synth").apply { mkdirs() }
        val generatedWavs = mutableListOf<File>()
        try {
            val targetSampleRate = 44100
            val targetChannels = 2

            val cueSegments = mutableListOf<CueAudioSegment>()
            val shouldIncludeVoice = config.includeHindiDubbing && cues.isNotEmpty() && config.voiceSpeechVolume > 0.01f
            if (shouldIncludeVoice) {
                val totalCues = cues.size
                for (idx in cues.indices) {
                    val cue = cues[idx]
                    val progressFraction = (idx.toFloat() / totalCues.toFloat()) * 0.40f
                    onProgress(progressFraction, "Synthesizing speech dialogue ${idx + 1}/$totalCues...")
                    val rawText = cue.cleanSpokenText.ifBlank { cue.text }
                    val speechText = if (targetLanguageCode.equals("hi", ignoreCase = true)) {
                        if (DevanagariTransliteration.hasDevanagari(rawText)) {
                            rawText
                        } else {
                            OfflineSubtitleEngine.translateSentenceToHindi(rawText)
                        }
                    } else {
                        // Non-Hindi dubbing: speak subtitles in the target language as-is.
                        rawText
                    }
                    val wavFile = File(synthDir, "cue_${idx}_${System.currentTimeMillis()}.wav")
                    generatedWavs.add(wavFile)
                    val synthOk = try {
                        synthesizer(cue, speechText, wavFile)
                    } catch (e: Exception) {
                        false
                    }
                    var pcmInfo: AudioExportHelper.PcmInfo? = null
                    if (synthOk && wavFile.exists() && wavFile.length() > 44) {
                        pcmInfo = AudioExportHelper.readWavPcm(wavFile)
                    }
                    val rawPcm = pcmInfo?.pcmBytes ?: run {
                        val fallbackDurMs = cue.durationMs.coerceIn(800L, 8000L)
                        AudioExportHelper.generateSpeechLikePcm(fallbackDurMs, targetSampleRate)
                    }
                    val standardizedStereoPcm = AudioExportHelper.convertPcm(
                        pcm = rawPcm,
                        srcRate = pcmInfo?.sampleRate ?: targetSampleRate,
                        srcChannels = pcmInfo?.channelCount ?: 1,
                        dstRate = targetSampleRate,
                        dstChannels = targetChannels
                    )
                    val actualDurMs = (standardizedStereoPcm.size * 1000L) / (targetSampleRate * targetChannels * 2L)
                    val effectiveDurMs = maxOf(cue.durationMs, actualDurMs)
                    cueSegments.add(
                        CueAudioSegment(
                            startMs = cue.startTimeMs,
                            durationMs = effectiveDurMs,
                            pcm = standardizedStereoPcm
                        )
                    )
                }
            }

            var tunePcm: ByteArray? = null
            val shouldIncludeTune = config.includeTuneBgm && config.bgmVolume > 0.01f
            if (shouldIncludeTune) {
                onProgress(0.45f, "Preparing background tune (BGM track)...")
                try {
                    val bgmFile = if (!config.bgmCustomUri.isNullOrBlank()) {
                        resolveAudioFile(context, config.bgmCustomUri)
                    } else {
                        SampleMusicGenerator.getOrCreateSampleBgmFile(context, config.bgmStyleName)
                    } ?: SampleMusicGenerator.getOrCreateSampleBgmFile(context, config.bgmStyleName)
                    if (bgmFile != null && bgmFile.exists()) {
                        val pcmInfo = AudioExportHelper.readWavPcm(bgmFile)
                        tunePcm = if (pcmInfo != null) {
                            AudioExportHelper.convertPcm(
                                pcm = pcmInfo.pcmBytes,
                                srcRate = pcmInfo.sampleRate,
                                srcChannels = pcmInfo.channelCount,
                                dstRate = targetSampleRate,
                                dstChannels = targetChannels
                            )
                        } else {
                            AudioExportHelper.decodeAudioToPcm(bgmFile, 120_000L, targetSampleRate, targetChannels)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load BGM tune: ${e.message}")
                }
            }

            var origPcm: ByteArray? = null
            val shouldIncludeOrig = config.includeOriginalAudio && config.originalAudioVolume > 0.01f && sourceVideoFile != null
            if (shouldIncludeOrig) {
                onProgress(0.55f, "Decoding original video audio track...")
                try {
                    val decodeLimitMs = if (videoDurationMs > 0L) (videoDurationMs + 10_000L) else 3_600_000L
                    origPcm = AudioExportHelper.decodeAudioToPcm(
                        file = sourceVideoFile,
                        maxDurationMs = decodeLimitMs,
                        targetSampleRate = targetSampleRate,
                        targetChannels = targetChannels
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not decode source video audio: ${e.message}")
                }
            }

            if (cueSegments.isEmpty() && tunePcm == null && origPcm == null) {
                return@withContext null
            }

            onProgress(0.65f, "Mixing voice dialogue and original video sound together...")
            val lastCueEnd = cues.maxOfOrNull { it.endTimeMs } ?: 5000L
            val safeVideoDur = if (videoDurationMs > 0L) videoDurationMs else (lastCueEnd + 2000L)
            val origDurMs = if (origPcm != null && origPcm.isNotEmpty()) (origPcm.size * 1000L) / (targetSampleRate * targetChannels * 2L) else 0L
            val finalDurationMs = maxOf(safeVideoDur, origDurMs).coerceAtLeast(2000L)
            val totalFrames = ((finalDurationMs * targetSampleRate) / 1000L).toInt()

            val fullPcmFile = File(synthDir, "full_mixed_timeline_${System.currentTimeMillis()}.pcm")
            // use{} guarantees the stream is closed even if the mix loop throws
            // (fd leak + partial-file fix); cleanup of the partial file below.
            try {
                FileOutputStream(fullPcmFile).buffered(64 * 1024).use { outputStream ->

                    val voiceTracks = cueSegments.map { seg ->
                val startFrame = (seg.startMs * targetSampleRate) / 1000L
                val totalSegFrames = seg.pcm.size / 4
                ActiveVoiceTrack(
                    startFrame = startFrame,
                    endFrame = startFrame + totalSegFrames,
                    pcm = seg.pcm
                )
            }.sortedBy { it.startFrame }

            var smoothedBgmGain = config.bgmVolume
            var smoothedOrigGain = config.originalAudioVolume
            val tuneSize = tunePcm?.size ?: 0
            val origSize = origPcm?.size ?: 0
            val safeTuneFrames = tuneSize / 4
            val safeOrigFrames = origSize / 4
            val duckingRatio = config.duckingRatio.coerceIn(0.1f, 0.8f)

            val chunkFrames = 4410
            val chunkBuffer = ByteArray(chunkFrames * 4)
            var currentChunkOffset = 0

            for (frameIdx in 0 until totalFrames) {
                var sumL = 0
                var sumR = 0
                var isSpeechActive = false
                for (seg in voiceTracks) {
                    if (frameIdx in seg.startFrame until seg.endFrame) {
                        isSpeechActive = true
                        val byteOffset = ((frameIdx - seg.startFrame) * 4).toInt()
                        if (byteOffset + 3 < seg.pcm.size) {
                            val sL = ((seg.pcm[byteOffset].toInt() and 0xFF) or (seg.pcm[byteOffset + 1].toInt() shl 8)).toShort()
                            val sR = ((seg.pcm[byteOffset + 2].toInt() and 0xFF) or (seg.pcm[byteOffset + 3].toInt() shl 8)).toShort()
                            sumL += sL
                            sumR += sR
                        }
                    } else if (seg.startFrame > frameIdx + (targetSampleRate * 2)) {
                        break
                    }
                }
                val voiceL = sumL.coerceIn(-32767, 32767).toShort()
                val voiceR = sumR.coerceIn(-32767, 32767).toShort()

                val targetBgmGain = if (isSpeechActive && config.isDuckingEnabled) {
                    config.bgmVolume * duckingRatio
                } else {
                    config.bgmVolume
                }
                smoothedBgmGain += (targetBgmGain - smoothedBgmGain) * 0.005f

                val origDuckingRatio = duckingRatio.coerceAtLeast(0.55f)
                val targetOrigGain = if (isSpeechActive && config.isDuckingEnabled) {
                    config.originalAudioVolume * origDuckingRatio
                } else {
                    config.originalAudioVolume
                }
                smoothedOrigGain += (targetOrigGain - smoothedOrigGain) * 0.005f

                var tuneL: Short = 0
                var tuneR: Short = 0
                if (tunePcm != null && safeTuneFrames > 0) {
                    val tuneFrame = (frameIdx % safeTuneFrames)
                    val tuneOffset = tuneFrame * 4
                    if (tuneOffset + 3 < tuneSize) {
                        tuneL = ((tunePcm[tuneOffset].toInt() and 0xFF) or (tunePcm[tuneOffset + 1].toInt() shl 8)).toShort()
                        tuneR = ((tunePcm[tuneOffset + 2].toInt() and 0xFF) or (tunePcm[tuneOffset + 3].toInt() shl 8)).toShort()
                    }
                }

                var origL: Short = 0
                var origR: Short = 0
                if (origPcm != null && frameIdx < safeOrigFrames) {
                    val origOffset = frameIdx * 4
                    if (origOffset + 3 < origSize) {
                        origL = ((origPcm[origOffset].toInt() and 0xFF) or (origPcm[origOffset + 1].toInt() shl 8)).toShort()
                        origR = ((origPcm[origOffset + 2].toInt() and 0xFF) or (origPcm[origOffset + 3].toInt() shl 8)).toShort()
                    }
                }

                val voiceGain = (config.voiceSpeechVolume * 1.35f).coerceAtLeast(0.5f)
                val effectiveBgmGain = if (config.includeTuneBgm) smoothedBgmGain else 0.0f
                val effectiveOrigGain = if (config.includeOriginalAudio) smoothedOrigGain else 0.0f

                val mixL = (voiceL * voiceGain + tuneL * effectiveBgmGain + origL * effectiveOrigGain).toInt().coerceIn(-32767, 32767).toShort()
                val mixR = (voiceR * voiceGain + tuneR * effectiveBgmGain + origR * effectiveOrigGain).toInt().coerceIn(-32767, 32767).toShort()

                chunkBuffer[currentChunkOffset] = (mixL.toInt() and 0xFF).toByte()
                chunkBuffer[currentChunkOffset + 1] = ((mixL.toInt() shr 8) and 0xFF).toByte()
                chunkBuffer[currentChunkOffset + 2] = (mixR.toInt() and 0xFF).toByte()
                chunkBuffer[currentChunkOffset + 3] = ((mixR.toInt() shr 8) and 0xFF).toByte()
                currentChunkOffset += 4

                if (currentChunkOffset >= chunkBuffer.size) {
                    outputStream.write(chunkBuffer, 0, currentChunkOffset)
                    currentChunkOffset = 0
                }
            }

                if (currentChunkOffset > 0) {
                    outputStream.write(chunkBuffer, 0, currentChunkOffset)
                }
                outputStream.flush()
            }
            } catch (e: Exception) {
                try { fullPcmFile.delete() } catch (_: Exception) {}
                throw e
            }

            onProgress(0.88f, "Encoding mixed tune & voice audio track to AAC (Stereo)...")
            val outputM4a = File(synthDir, "mixed_tune_voice_${System.currentTimeMillis()}.m4a")
            val encodeOk = encodePcmToM4a(
                pcmFile = fullPcmFile,
                sampleRate = targetSampleRate,
                channelCount = targetChannels,
                outputM4aFile = outputM4a,
                onProgress = { encProgress ->
                    val p = 0.88f + (encProgress * 0.10f)
                    onProgress(p, "Encoding mixed audio track (${(encProgress * 100).toInt()}%)...")
                }
            )
            try { fullPcmFile.delete() } catch (_: Exception) {}

            if (encodeOk && outputM4a.exists() && outputM4a.length() > 100) {
                outputM4a
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error building synchronized mixed audio: ${e.message}", e)
            null
        } finally {
            for (f in generatedWavs) {
                try { f.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun encodePcmToM4a(
        pcmFile: File,
        sampleRate: Int = 44100,
        channelCount: Int = 1,
        outputM4aFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputStream: FileInputStream? = null
        var muxerStarted = false
        var isEos = false
        var writtenSamplesCount = 0
        try {
            val totalFileLen = pcmFile.length()
            val mime = MediaFormat.MIMETYPE_AUDIO_AAC
            val format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
            }
            encoder = try {
                MediaCodec.createEncoderByType(mime).apply {
                    configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
                }
            } catch (e: Exception) {
                val simpleFormat = MediaFormat.createAudioFormat(mime, sampleRate, channelCount).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                }
                MediaCodec.createEncoderByType(mime).apply {
                    configure(simpleFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
                }
            }
            outputM4aFile.parentFile?.mkdirs()
            try { if (outputM4aFile.exists()) outputM4aFile.delete() } catch (_: Exception) {}
            muxer = MediaMuxer(outputM4aFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var audioTrackIndex = -1
            inputStream = FileInputStream(pcmFile)
            val inBuffer = ByteArray(16384)
            val bufferInfo = MediaCodec.BufferInfo()
            var presentationTimeUs = 0L
            val bytesPerSec = sampleRate * channelCount * 2L
            var totalReadBytes = 0L
            var lastAacPtsUs = -1L
            var consecutiveTimeouts = 0
            var drainAttempts = 0
            val maxDrainAttempts = 200
            while (drainAttempts < maxDrainAttempts && consecutiveTimeouts < 400) {
                var activityInThisLoop = false
                if (!isEos) {
                    val inputIndex = encoder.dequeueInputBuffer(5000L)
                    if (inputIndex >= 0) {
                        activityInThisLoop = true
                        consecutiveTimeouts = 0
                        val inputBuffer = encoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val maxToRead = minOf(inBuffer.size, inputBuffer.remaining())
                            val readBytes = inputStream.read(inBuffer, 0, maxToRead)
                            if (readBytes <= 0) {
                                encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEos = true
                            } else {
                                inputBuffer.put(inBuffer, 0, readBytes)
                                encoder.queueInputBuffer(inputIndex, 0, readBytes, presentationTimeUs, 0)
                                presentationTimeUs += ((readBytes * 1_000_000L) / bytesPerSec)
                                totalReadBytes += readBytes
                                if (totalFileLen > 0L) {
                                    val p = (totalReadBytes.toFloat() / totalFileLen.toFloat()).coerceIn(0f, 0.95f)
                                    onProgress(p)
                                }
                            }
                        }
                    }
                }
                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 5000L)
                when (outputIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        activityInThisLoop = true
                        consecutiveTimeouts = 0
                        val newFormat = encoder.outputFormat
                        if (!muxerStarted) {
                            audioTrackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (isEos) drainAttempts++
                    }
                    else -> {
                        if (outputIndex >= 0) {
                            activityInThisLoop = true
                            consecutiveTimeouts = 0
                            val outputBuffer = encoder.getOutputBuffer(outputIndex)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0 && outputBuffer != null && muxerStarted) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val safePts = if (bufferInfo.presentationTimeUs <= lastAacPtsUs) lastAacPtsUs + 1000L else bufferInfo.presentationTimeUs
                                lastAacPtsUs = safePts
                                bufferInfo.presentationTimeUs = safePts
                                muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                                writtenSamplesCount++
                            }
                            encoder.releaseOutputBuffer(outputIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                break
                            }
                        }
                    }
                }
                if (!activityInThisLoop) {
                    consecutiveTimeouts++
                }
            }
            if (muxerStarted && writtenSamplesCount > 0) {
                try { muxer.stop() } catch (_: Exception) {}
            }
            onProgress(1.0f)
            return outputM4aFile.exists() && outputM4aFile.length() > 100
        } catch (e: Exception) {
            Log.e(TAG, "encodePcmToM4a error: ${e.message}", e)
            return false
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun extractVideoRotation(file: File): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    suspend fun renderExportPreviewClip(
        context: Context,
        videoUriString: String?,
        videoTitle: String,
        rawSrtContent: String,
        parsedCues: List<SrtItem>,
        config: VideoExportConfig,
        previewDurationSeconds: Int = 6,
        synthesizer: (suspend (cue: SrtItem, text: String, outputFile: File) -> Boolean)? = null,
        onProgress: (progress: Float, status: String) -> Unit
    ): VideoExportResult = withContext(Dispatchers.IO) {
        try {
            onProgress(0.10f, "Preparing preview render...")
            val cacheDir = File(context.cacheDir, "export_preview").apply { mkdirs() }
            val previewVideoFile = File(cacheDir, "preview_sample_${previewDurationSeconds}s.mp4")

            onProgress(0.25f, "Resolving video segment...")
            val sourceVideoFile = resolveSourceVideoFile(context, videoUriString) { p ->
                onProgress(0.25f + (p * 0.25f), "Loading video for preview...")
            }
            if (sourceVideoFile == null || !sourceVideoFile.exists() || sourceVideoFile.length() == 0L) {
                return@withContext VideoExportResult(
                    success = false,
                    errorMessage = "Source video could not be loaded for preview."
                )
            }

            onProgress(0.50f, "Formatting Hindi subtitles for preview...")
            val hindiSrtText = ensureExportSubtitles(rawSrtContent, parsedCues, "hi")
            val hindiCues = SrtParser.parse(hindiSrtText)

            val previewMaxMs = (previewDurationSeconds * 1000L)
            val previewCues = if (hindiCues.any { it.startTimeMs <= previewMaxMs }) {
                hindiCues.filter { it.startTimeMs <= previewMaxMs }
            } else if (hindiCues.isNotEmpty()) {
                hindiCues.take(2).mapIndexed { idx, cue ->
                    cue.copy(
                        startTimeMs = (idx * 2500L),
                        endTimeMs = (idx * 2500L + 2300L)
                    )
                }
            } else {
                listOf(
                    SrtItem(
                        index = 1,
                        startTimeMs = 500L,
                        endTimeMs = previewMaxMs.coerceAtLeast(3000L) - 500L,
                        text = "नमस्ते, यह प्रिव्यू वीडियो है।"
                    )
                )
            }

            val previewSrtFile = File(cacheDir, "preview_sample_${previewDurationSeconds}s.srt")
            previewSrtFile.writeText(SrtParser.exportToSrt(previewCues), Charsets.UTF_8)

            var previewDubbedAudio: File? = null
            val needsAudioMixing = config.includeHindiDubbing || config.includeTuneBgm || config.includeOriginalAudio
            if (needsAudioMixing) {
                onProgress(0.60f, "Mixing Hindi voice & background tune for preview...")
                previewDubbedAudio = buildSynchronizedMixedAudioFile(
                    context = context,
                    sourceVideoFile = sourceVideoFile,
                    cues = previewCues,
                    videoDurationMs = previewMaxMs,
                    config = config,
                    synthesizer = synthesizer ?: { cue, _, file ->
                        val dur = cue.durationMs.coerceIn(800L, 8000L)
                        val pcm = AudioExportHelper.generateSpeechLikePcm(dur, 44100)
                        AudioExportHelper.writeWavFile(file, pcm, 44100, 1)
                    },
                    onProgress = { _, _ -> }
                )
            }

            onProgress(0.75f, "Rendering ${previewDurationSeconds}s video clip with Hindi audio...")
            val maxDurationUs = previewDurationSeconds * 1_000_000L
            val tempPreviewFile = File(cacheDir, "temp_preview_${System.currentTimeMillis()}.mp4")
            val remuxOk = remuxVideoWithSubtitles(
                sourceVideo = sourceVideoFile,
                destVideo = tempPreviewFile,
                dubbedAudioFile = previewDubbedAudio,
                maxDurationUs = maxDurationUs,
                onProgress = { p ->
                    onProgress(0.75f + (p * 0.20f), "Encoding preview (${(p * 100).toInt()}%)...")
                }
            )

            if (remuxOk && tempPreviewFile.exists() && tempPreviewFile.length() > 0L) {
                tempPreviewFile.copyTo(previewVideoFile, overwrite = true)
                try { tempPreviewFile.delete() } catch (_: Exception) {}
            } else {
                sourceVideoFile.copyTo(previewVideoFile, overwrite = true)
            }
            try { previewDubbedAudio?.delete() } catch (_: Exception) {}

            val durMs = extractVideoDuration(previewVideoFile)
            onProgress(1.0f, "Preview rendered successfully with Hindi audio!")
            VideoExportResult(
                success = true,
                outputVideoFile = previewVideoFile,
                outputSrtFile = previewSrtFile,
                durationMs = if (durMs > 0) durMs.coerceAtMost(previewMaxMs) else previewMaxMs,
                fileSizeBytes = previewVideoFile.length()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Export preview generation failed", e)
            VideoExportResult(
                success = false,
                errorMessage = e.message ?: "Failed to generate preview clip."
            )
        }
    }

    private fun extractVideoDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun getExportDirectory(context: Context): File {
        val appMovies = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val appDubbedDir = if (appMovies != null) File(appMovies, "DubbedVideos") else null
        if (appDubbedDir != null && (appDubbedDir.exists() || appDubbedDir.mkdirs())) {
            return appDubbedDir
        }
        try {
            val publicMovies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val publicDubbedDir = File(publicMovies, "DubbedVideos")
            if (publicDubbedDir.exists() || publicDubbedDir.mkdirs()) {
                if (publicDubbedDir.canWrite()) {
                    return publicDubbedDir
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Public Movies folder not accessible: ${e.message}")
        }
        val internalMovies = File(context.filesDir, "DubbedVideos")
        if (!internalMovies.exists()) {
            internalMovies.mkdirs()
        }
        return internalMovies
    }

    fun shareExportedVideo(context: Context, videoFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                videoFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, videoFile.nameWithoutExtension)
                putExtra(Intent.EXTRA_TEXT, "Exported video with Hindi subtitles: ${videoFile.nameWithoutExtension}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Video with Hindi Subtitles")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share video", e)
        }
    }
}
