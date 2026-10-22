package com.example

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class SrtHistoryRepository(
    private val context: Context,
    private val dao: SrtHistoryDao
) {
    /** Rows this big blow past SQLite's CursorWindow (~2 MB) on read. */
    private val maxInlineBytes = 1_000_000

    /**
     * Large SRT files (4 MB+) are kept on disk and only a preview lives in
     * Room, otherwise reading history throws SQLiteBlobTooBigException.
     * Returns the raw content to store and a path for the full file.
     */
    private fun spoolLargeContent(fileName: String, rawContent: String): Pair<String, String?> {
        val bytes = rawContent.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxInlineBytes) return Pair(rawContent, null)
        return try {
            val dir = File(context.filesDir, "srt_history").apply { mkdirs() }
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
            val file = File(dir, "${System.currentTimeMillis()}_$safeName")
            file.writeText(rawContent, Charsets.UTF_8)
            // Keep only a short snippet inline for the history list preview.
            Pair(rawContent.take(4000), file.absolutePath)
        } catch (_: Exception) {
            Pair(rawContent.take(4000), null)
        }
    }
    // A single oversized row throws SQLiteBlobTooBigException; never let that
    // crash the history collector or the UI.
    val allHistory: Flow<List<SrtHistoryEntity>> = dao.getAllHistory()
        .catch { emit(emptyList()) }
        .flowOn(Dispatchers.IO)
    val historyCount: Flow<Int> = dao.getHistoryCount().flowOn(Dispatchers.IO)

    fun getHistoryBySource(source: SrtHistorySource): Flow<List<SrtHistoryEntity>> =
        dao.getHistoryBySource(source.name)
            .catch { emit(emptyList()) }
            .flowOn(Dispatchers.IO)

    suspend fun recordHistory(
        fileName: String,
        sourceType: SrtHistorySource,
        rawContent: String,
        cueCount: Int = 0,
        durationMs: Long = 0L,
        languageCode: String = "hi",
        formatName: String = "SRT",
        videoTitle: String? = null,
        videoUri: String? = null,
        filePathOrUri: String? = null
    ): Long = withContext(Dispatchers.IO) {
        if (rawContent.isBlank()) return@withContext 0L
        val (storedContent, spooledPath) = spoolLargeContent(fileName, rawContent)
        val existing = dao.findRecentByFileNameAndSource(fileName, sourceType.name)
            ?: dao.findRecentByFileName(fileName)
        val now = System.currentTimeMillis()
        if (existing != null) {
            val updated = existing.copy(
                timestamp = now,
                sourceType = sourceType.name,
                rawContent = storedContent,
                cueCount = if (cueCount > 0) cueCount else existing.cueCount,
                durationMs = if (durationMs > 0L) durationMs else existing.durationMs,
                languageCode = languageCode,
                formatName = formatName,
                videoTitle = videoTitle ?: existing.videoTitle,
                videoUri = videoUri ?: existing.videoUri,
                filePathOrUri = spooledPath ?: filePathOrUri ?: existing.filePathOrUri,
                fileSizeBytes = rawContent.toByteArray(Charsets.UTF_8).size.toLong()
            )
            dao.insertHistory(updated)
            return@withContext updated.id
        }

        val entry = SrtHistoryEntity(
            fileName = fileName,
            sourceType = sourceType.name,
            rawContent = storedContent,
            cueCount = cueCount,
            durationMs = durationMs,
            languageCode = languageCode,
            formatName = formatName,
            videoTitle = videoTitle,
            videoUri = videoUri,
            filePathOrUri = spooledPath ?: filePathOrUri,
            fileSizeBytes = rawContent.toByteArray(Charsets.UTF_8).size.toLong(),
            timestamp = now
        )
        dao.insertHistory(entry)
    }

    /**
     * Full subtitle text for a history entry: large files live on disk, small
     * ones are stored inline. Null when the spooled file went missing.
     */
    suspend fun fullContentFor(entry: SrtHistoryEntity): String? = withContext(Dispatchers.IO) {
        val path = entry.filePathOrUri
        if (!path.isNullOrBlank() && !path.startsWith("content://")) {
            val file = File(path)
            if (file.exists()) {
                return@withContext try {
                    file.readText(Charsets.UTF_8)
                } catch (_: Exception) {
                    null
                }
            }
        }
        entry.rawContent.takeIf { it.isNotBlank() }
    }

    suspend fun touchHistory(id: Long) = withContext(Dispatchers.IO) {
        dao.updateTimestamp(id, System.currentTimeMillis())
    }

    suspend fun getHistoryById(id: Long): SrtHistoryEntity? = withContext(Dispatchers.IO) {
        dao.getHistoryById(id)
    }

    suspend fun deleteHistory(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteHistoryById(id)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAllHistory()
    }

    suspend fun getRecentHistoryDirect(limit: Int = 10): List<SrtHistoryEntity> = withContext(Dispatchers.IO) {
        dao.getRecentHistoryDirect(limit)
    }
}
