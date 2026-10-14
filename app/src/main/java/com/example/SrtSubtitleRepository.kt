package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repository adhering to Clean Architecture to abstract Room database operations
 * for SRT subtitle documents and cue timeline queries.
 */
class SrtSubtitleRepository(private val dao: SrtSubtitleDao) {
    val allFiles: Flow<List<SrtFileEntity>> = dao.getAllFiles().flowOn(Dispatchers.IO)

    fun getFileById(fileId: Long): Flow<SrtFileEntity?> =
        dao.getFileById(fileId).flowOn(Dispatchers.IO)

    suspend fun getFileByIdDirect(fileId: Long): SrtFileEntity? = withContext(Dispatchers.IO) {
        dao.getFileByIdDirect(fileId)
    }

    suspend fun getFileByVideoUri(videoUri: String): SrtFileEntity? = withContext(Dispatchers.IO) {
        dao.getFileByVideoUri(videoUri)
    }

    fun getFileWithCues(fileId: Long): Flow<SrtFileWithCues?> =
        dao.getFileWithCues(fileId).flowOn(Dispatchers.IO)

    suspend fun getFileWithCuesDirect(fileId: Long): SrtFileWithCues? = withContext(Dispatchers.IO) {
        dao.getFileWithCuesDirect(fileId)
    }

    fun getCuesForFile(fileId: Long): Flow<List<SrtCueEntity>> =
        dao.getCuesForFile(fileId).flowOn(Dispatchers.IO)

    suspend fun getCuesForFileDirect(fileId: Long): List<SrtCueEntity> = withContext(Dispatchers.IO) {
        dao.getCuesForFileDirect(fileId)
    }

    suspend fun saveFileAndCues(file: SrtFileEntity, cues: List<SrtCueEntity>): Long = withContext(Dispatchers.IO) {
        dao.insertFileAndCues(file, cues)
    }

    suspend fun updateFile(file: SrtFileEntity) = withContext(Dispatchers.IO) {
        dao.updateFile(file)
    }

    suspend fun updateOffset(fileId: Long, offsetMs: Long) = withContext(Dispatchers.IO) {
        dao.updateOffset(fileId, offsetMs)
    }

    suspend fun replaceCues(fileId: Long, cues: List<SrtCueEntity>) = withContext(Dispatchers.IO) {
        dao.replaceCues(fileId, cues)
    }

    suspend fun deleteFile(fileId: Long) = withContext(Dispatchers.IO) {
        dao.deleteFile(fileId)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAllFiles()
    }

    suspend fun findActiveCue(fileId: Long, effectivePositionMs: Long): SrtCueEntity? = withContext(Dispatchers.IO) {
        dao.findActiveCue(fileId, effectivePositionMs)
    }

    fun observeActiveCue(fileId: Long, effectivePositionMs: Long): Flow<SrtCueEntity?> =
        dao.observeActiveCue(fileId, effectivePositionMs).flowOn(Dispatchers.IO)

    suspend fun findNextCue(fileId: Long, effectivePositionMs: Long): SrtCueEntity? = withContext(Dispatchers.IO) {
        dao.findNextCue(fileId, effectivePositionMs)
    }

    suspend fun findPreviousCue(fileId: Long, effectivePositionMs: Long): SrtCueEntity? = withContext(Dispatchers.IO) {
        dao.findPreviousCue(fileId, effectivePositionMs)
    }

    suspend fun getCuesInRange(fileId: Long, startMs: Long, endMs: Long): List<SrtCueEntity> = withContext(Dispatchers.IO) {
        dao.getCuesInRange(fileId, startMs, endMs)
    }
}
