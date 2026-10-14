package com.example

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DubbingProjectDao {
    @Query("SELECT * FROM dubbing_projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<DubbingProject>>

    @Query("SELECT * FROM dubbing_projects ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentProjects(limit: Int): Flow<List<DubbingProject>>

    @Query("SELECT * FROM dubbing_projects WHERE id = :id")
    suspend fun getProjectById(id: Long): DubbingProject?

    @Query("SELECT * FROM dubbing_projects WHERE videoUri = :videoUri LIMIT 1")
    suspend fun getProjectByVideoUri(videoUri: String): DubbingProject?

    @Query("SELECT COUNT(*) FROM dubbing_projects")
    suspend fun getProjectCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: DubbingProject): Long

    @Update
    suspend fun updateProject(project: DubbingProject)

    @Delete
    suspend fun deleteProject(project: DubbingProject)

    @Query("DELETE FROM dubbing_projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)
}
