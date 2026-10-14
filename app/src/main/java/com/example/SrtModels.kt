package com.example

enum class SpeakerGender(val displayName: String, val iconLabel: String) {
    MALE("Male", "M"),
    FEMALE("Female", "F"),
    CHILD("Child", "C"),
    NARRATOR("Narrator", "N"),
    UNKNOWN("Unknown", "?")
}

enum class SpeakerRole(val title: String) {
    NARRATOR("Story Narrator"),
    PRIMARY_MALE("Lead Male Character"),
    PRIMARY_FEMALE("Lead Female Character"),
    CHILD("Child Character"),
    SECONDARY("Secondary Character"),
    UNKNOWN("General Dialogue")
}

data class SpeakerProfile(
    val id: String,
    val name: String,
    val gender: SpeakerGender = SpeakerGender.UNKNOWN,
    val role: SpeakerRole = SpeakerRole.UNKNOWN,
    val avatarColorHex: Long = 0xFF1976D2,
    val pitchMultiplier: Float = 1.0f,
    val speedMultiplier: Float = 1.0f,
    val isAiDetected: Boolean = false,
    val detectionTag: String = "Auto"
)

data class SrtItem(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val speaker: SpeakerProfile? = null,
    val cleanSpokenText: String = text,
    val speakerTag: String? = null,
    val customPitch: Float? = null,
    val customSpeed: Float? = null
) {
    val durationMs: Long get() = (endTimeMs - startTimeMs).coerceAtLeast(100L)

    fun formatTimeRange(): String {
        return "${formatMs(startTimeMs)} -> ${formatMs(endTimeMs)}"
    }

    companion object {
        fun formatMs(ms: Long): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val millis = ms % 1000
            return String.format(java.util.Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
        }
    }
}

data class VideoSource(
    val title: String,
    val description: String,
    val uri: String,
    val defaultSrt: String
)

enum class SubtitleFormat(
    val formatName: String,
    val defaultExtension: String,
    val mimeType: String,
    val badgeLabel: String,
    val iconEmoji: String
) {
    SRT("SubRip Subtitle", "srt", "application/x-subrip", "SRT", "📄"),
    VTT("WebVTT", "vtt", "text/vtt", "VTT", "🌐"),
    ASS("Advanced SubStation Alpha", "ass", "text/x-ssa", "ASS", "🎨"),
    SSA("SubStation Alpha", "ssa", "text/x-ssa", "SSA", "📜");

    val displayName: String get() = badgeLabel
    val extension: String get() = defaultExtension

    companion object {
        fun fromExtension(ext: String?): SubtitleFormat? {
            return when (ext?.lowercase()?.trimStart('.')) {
                "srt" -> SRT
                "vtt" -> VTT
                "ass" -> ASS
                "ssa" -> SSA
                else -> null
            }
        }

        fun fromFileName(fileName: String?): SubtitleFormat {
            val ext = fileName?.substringAfterLast('.', "")?.lowercase()
            return fromExtension(ext) ?: SRT
        }
    }
}

data class MxVideoItem(
    val id: String,
    val title: String,
    val folderName: String = "Videos",
    val uriString: String = "",
    val durationText: String = "0:00",
    val sizeText: String = "",
    val resolution: String = "1080p",
    val srtPreset: String? = null,
    val isHindiDubbed: Boolean = false,
    val isNew: Boolean = false,
    val dateAdded: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val isFromDevice: Boolean = true
)
