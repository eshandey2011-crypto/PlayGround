package com.example

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for SRT Subtitle Files and Subtitle Cues.
 */
@Dao
interface SrtSubtitleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: SrtFileEntity): Long

    @Update
    suspend fun updateFile(file: SrtFileEntity)

    @Query("SELECT * FROM srt_files ORDER BY updatedAt DESC")
    fun getAllFiles(): Flow<List<SrtFileEntity>>

    @Query("SELECT * FROM srt_files WHERE id = :fileId")
    fun getFileById(fileId: Long): Flow<SrtFileEntity?>

    @Query("SELECT * FROM srt_files WHERE id = :fileId")
    suspend fun getFileByIdDirect(fileId: Long): SrtFileEntity?

    @Query("SELECT * FROM srt_files WHERE videoUri = :videoUri ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getFileByVideoUri(videoUri: String): SrtFileEntity?

    @Transaction
    @Query("SELECT * FROM srt_files WHERE id = :fileId")
    fun getFileWithCues(fileId: Long): Flow<SrtFileWithCues?>

    @Transaction
    @Query("SELECT * FROM srt_files WHERE id = :fileId")
    suspend fun getFileWithCuesDirect(fileId: Long): SrtFileWithCues?

    @Query("UPDATE srt_files SET offsetMs = :offsetMs, updatedAt = :updatedAt WHERE id = :fileId")
    suspend fun updateOffset(
        fileId: Long,
        offsetMs: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM srt_files WHERE id = :fileId")
    suspend fun deleteFile(fileId: Long)

    @Query("DELETE FROM srt_files")
    suspend fun clearAllFiles()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCues(cues: List<SrtCueEntity>)

    @Query("SELECT * FROM srt_cues WHERE fileId = :fileId ORDER BY cueIndex ASC")
    fun getCuesForFile(fileId: Long): Flow<List<SrtCueEntity>>

    @Query("SELECT * FROM srt_cues WHERE fileId = :fileId ORDER BY cueIndex ASC")
    suspend fun getCuesForFileDirect(fileId: Long): List<SrtCueEntity>

    @Query("DELETE FROM srt_cues WHERE fileId = :fileId")
    suspend fun deleteCuesForFile(fileId: Long)

    @Query("""
        SELECT * FROM srt_cues 
        WHERE fileId = :fileId 
          AND :effectiveTimeMs >= startTimeMs 
          AND :effectiveTimeMs <= endTimeMs 
        ORDER BY startTimeMs ASC 
        LIMIT 1
    """)
    suspend fun findActiveCue(fileId: Long, effectiveTimeMs: Long): SrtCueEntity?

    @Query("""
        SELECT * FROM srt_cues 
        WHERE fileId = :fileId 
          AND :effectiveTimeMs >= startTimeMs 
          AND :effectiveTimeMs <= endTimeMs 
        ORDER BY startTimeMs ASC 
        LIMIT 1
    """)
    fun observeActiveCue(fileId: Long, effectiveTimeMs: Long): Flow<SrtCueEntity?>

    @Query("""
        SELECT * FROM srt_cues 
        WHERE fileId = :fileId 
          AND startTimeMs > :effectiveTimeMs 
        ORDER BY startTimeMs ASC 
        LIMIT 1
    """)
    suspend fun findNextCue(fileId: Long, effectiveTimeMs: Long): SrtCueEntity?

    @Query("""
        SELECT * FROM srt_cues 
        WHERE fileId = :fileId 
          AND endTimeMs < :effectiveTimeMs 
        ORDER BY endTimeMs DESC 
        LIMIT 1
    """)
    suspend fun findPreviousCue(fileId: Long, effectiveTimeMs: Long): SrtCueEntity?

    @Query("""
        SELECT * FROM srt_cues 
        WHERE fileId = :fileId 
          AND startTimeMs <= :rangeEndMs 
          AND endTimeMs >= :rangeStartMs 
        ORDER BY startTimeMs ASC
    """)
    suspend fun getCuesInRange(fileId: Long, rangeStartMs: Long, rangeEndMs: Long): List<SrtCueEntity>

    @Transaction
    suspend fun insertFileAndCues(file: SrtFileEntity, cues: List<SrtCueEntity>): Long {
        val fileId = insertFile(file)
        val attachedCues = cues.map { it.copy(fileId = fileId) }
        insertCues(attachedCues)
        return fileId
    }

    @Transaction
    suspend fun replaceCues(fileId: Long, cues: List<SrtCueEntity>) {
        deleteCuesForFile(fileId)
        val attachedCues = cues.map { it.copy(fileId = fileId) }
        insertCues(attachedCues)
    }
}
