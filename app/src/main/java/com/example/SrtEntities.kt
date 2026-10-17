package com.example

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Room Entity representing a stored SRT Subtitle Document / File.
 */
@Entity(
    tableName = "srt_files",
    indices = [
        Index(value = ["videoUri"])
    ]
)
data class SrtFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val videoUri: String? = null,
    val videoTitle: String? = null,
    val languageCode: String = "en",
    val rawContent: String = "",
    val totalCues: Int = 0,
    val durationMs: Long = 0L,
    val offsetMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Room Entity representing an individual SRT Cue (timestamped dialogue item).
 * Linked with Foreign Key CASCADE deletion to [SrtFileEntity].
 */
@Entity(
    tableName = "srt_cues",
    foreignKeys = [
        ForeignKey(
            entity = SrtFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["fileId"]),
        Index(value = ["fileId", "startTimeMs", "endTimeMs"])
    ]
)
data class SrtCueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "fileId")
    val fileId: Long,
    val cueIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val cleanSpokenText: String = text,
    val speakerTag: String? = null,
    val customPitch: Float? = null,
    val customSpeed: Float? = null
) {
    val durationMs: Long get() = (endTimeMs - startTimeMs).coerceAtLeast(0L)

    fun toSrtItem(): SrtItem {
        return SrtItem(
            index = cueIndex,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            text = text,
            cleanSpokenText = cleanSpokenText,
            speakerTag = speakerTag,
            customPitch = customPitch,
            customSpeed = customSpeed
        )
    }

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

/**
 * One-to-many relationship: SRT File with all its parsed cues.
 */
data class SrtFileWithCues(
    @Embedded
    val file: SrtFileEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "fileId"
    )
    val cues: List<SrtCueEntity> = emptyList()
)
