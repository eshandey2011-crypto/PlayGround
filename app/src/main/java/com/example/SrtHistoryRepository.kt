package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class SrtHistoryRepository(
    private val dao: SrtHistoryDao
) {
    val allHistory: Flow<List<SrtHistoryEntity>> = dao.getAllHistory().flowOn(Dispatchers.IO)
    val historyCount: Flow<Int> = dao.getHistoryCount().flowOn(Dispatchers.IO)

    fun getHistoryBySource(source: SrtHistorySource): Flow<List<SrtHistoryEntity>> =
        dao.getHistoryBySource(source.name).flowOn(Dispatchers.IO)

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
        val existing = dao.findRecentByFileNameAndSource(fileName, sourceType.name)
            ?: dao.findRecentByFileName(fileName)
        val now = System.currentTimeMillis()
        if (existing != null) {
            val updated = existing.copy(
                timestamp = now,
                sourceType = sourceType.name,
                rawContent = rawContent,
                cueCount = if (cueCount > 0) cueCount else existing.cueCount,
                durationMs = if (durationMs > 0L) durationMs else existing.durationMs,
                languageCode = languageCode,
                formatName = formatName,
                videoTitle = videoTitle ?: existing.videoTitle,
                videoUri = videoUri ?: existing.videoUri,
                filePathOrUri = filePathOrUri ?: existing.filePathOrUri,
                fileSizeBytes = rawContent.toByteArray(Charsets.UTF_8).size.toLong()
            )
            dao.insertHistory(updated)
            return@withContext updated.id
        }

        val entry = SrtHistoryEntity(
            fileName = fileName,
            sourceType = sourceType.name,
            rawContent = rawContent,
            cueCount = cueCount,
            durationMs = durationMs,
            languageCode = languageCode,
            formatName = formatName,
            videoTitle = videoTitle,
            videoUri = videoUri,
            filePathOrUri = filePathOrUri,
            fileSizeBytes = rawContent.toByteArray(Charsets.UTF_8).size.toLong(),
            timestamp = now
        )
        dao.insertHistory(entry)
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
