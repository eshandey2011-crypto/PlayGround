package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class DubbingProjectRepository(private val dao: DubbingProjectDao) {
    val allProjects: Flow<List<DubbingProject>> = dao.getAllProjects().flowOn(Dispatchers.IO)

    fun getRecentProjects(limit: Int = 10): Flow<List<DubbingProject>> =
        dao.getRecentProjects(limit).flowOn(Dispatchers.IO)

    suspend fun getProjectById(id: Long): DubbingProject? = withContext(Dispatchers.IO) {
        dao.getProjectById(id)
    }

    suspend fun getProjectByVideoUri(videoUri: String): DubbingProject? = withContext(Dispatchers.IO) {
        dao.getProjectByVideoUri(videoUri)
    }

    suspend fun getProjectCount(): Int = withContext(Dispatchers.IO) {
        dao.getProjectCount()
    }

    suspend fun insert(project: DubbingProject): Long = saveProject(project)

    suspend fun delete(project: DubbingProject) = deleteProject(project)

    suspend fun saveProject(project: DubbingProject): Long = withContext(Dispatchers.IO) {
        dao.insertProject(project)
    }

    suspend fun updateProject(project: DubbingProject) = withContext(Dispatchers.IO) {
        dao.updateProject(project)
    }

    suspend fun deleteProject(project: DubbingProject) = withContext(Dispatchers.IO) {
        dao.deleteProject(project)
    }

    suspend fun deleteProjectById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteProjectById(id)
    }
}
