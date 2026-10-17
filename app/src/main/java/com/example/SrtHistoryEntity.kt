package com.example

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SrtHistorySource(val displayName: String, val badgeColorHex: Long) {
    DOWNLOADED("Downloaded", 0xFF00E676),
    EXTRACTED("Extracted", 0xFF00E5FF),
    IMPORTED("Imported", 0xFFFFB300),
    TRANSLATED("Translated", 0xFFB388FF),
    SAVED("Saved", 0xFF40C4FF),
    EXPORTED("Exported", 0xFFFF9933)
}

/**
 * Room Entity storing a historical record of extracted, downloaded,
 * imported, or translated SRT subtitle documents for quick access.
 */
@Entity(
    tableName = "srt_history_log",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["sourceType"]),
        Index(value = ["videoUri"])
    ]
)
data class SrtHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fileName: String,
    val sourceType: String = SrtHistorySource.DOWNLOADED.name,
    val rawContent: String,
    val cueCount: Int = 0,
    val durationMs: Long = 0L,
    val languageCode: String = "hi",
    val formatName: String = "SRT",
    val videoTitle: String? = null,
    val videoUri: String? = null,
    val filePathOrUri: String? = null,
    val fileSizeBytes: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    val sourceEnum: SrtHistorySource
        get() = try {
            SrtHistorySource.valueOf(sourceType)
        } catch (_: Exception) {
            SrtHistorySource.SAVED
        }

    val previewSnippet: String
        get() {
            if (rawContent.isBlank()) return "No content"
            val lines = rawContent.lines().filter {
                it.isNotBlank() && !it.trim().all { char -> char.isDigit() } && !it.contains("-->")
            }
            return lines.take(2).joinToString(" | ")
        }
}
