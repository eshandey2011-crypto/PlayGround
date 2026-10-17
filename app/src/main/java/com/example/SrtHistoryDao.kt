package com.example

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SrtHistoryDao {
    @Query("SELECT * FROM srt_history_log ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<SrtHistoryEntity>>

    @Query("SELECT * FROM srt_history_log WHERE sourceType = :sourceType ORDER BY timestamp DESC")
    fun getHistoryBySource(sourceType: String): Flow<List<SrtHistoryEntity>>

    @Query("SELECT * FROM srt_history_log WHERE id = :id LIMIT 1")
    suspend fun getHistoryById(id: Long): SrtHistoryEntity?

    @Query("SELECT * FROM srt_history_log WHERE fileName = :fileName ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentByFileName(fileName: String): SrtHistoryEntity?

    @Query("SELECT * FROM srt_history_log WHERE fileName = :fileName AND sourceType = :sourceType ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentByFileNameAndSource(fileName: String, sourceType: String): SrtHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: SrtHistoryEntity): Long

    @Query("UPDATE srt_history_log SET timestamp = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: Long, timestamp: Long)

    @Query("DELETE FROM srt_history_log WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM srt_history_log")
    suspend fun clearAllHistory()

    @Query("SELECT COUNT(*) FROM srt_history_log")
    fun getHistoryCount(): Flow<Int>

    @Query("SELECT * FROM srt_history_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistoryDirect(limit: Int = 10): List<SrtHistoryEntity>
}
