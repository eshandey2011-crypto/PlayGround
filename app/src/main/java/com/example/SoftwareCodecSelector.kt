package com.example

import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

/**
 * MediaCodecSelector that prefers on-device SOFTWARE decoders
 * (OMX.google.* / c2.android.*) over hardware codecs.
 *
 * Used for the "SW Decoder" mode so videos that fail on HW
 * (or show black screen) can still play via software decoding.
 */
object SoftwarePreferredSelector : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        val all = try {
            MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        } catch (_: Exception) {
            return MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType, requiresSecureDecoder, requiresTunnelingDecoder
            )
        }
        return all.sortedBy { info ->
            val n = info.name.lowercase()
            if (n.startsWith("omx.google.") || n.startsWith("c2.android.")) 0 else 1
        }
    }
}
