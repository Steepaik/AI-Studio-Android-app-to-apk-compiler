package com.example.data

import kotlinx.coroutines.flow.Flow

class BuildRepository(private val buildDao: BuildDao) {
    val allBuilds: Flow<List<BuildEntity>> = buildDao.getAllBuilds()

    suspend fun getBuildById(id: Int): BuildEntity? {
        return buildDao.getBuildById(id)
    }

    suspend fun insert(build: BuildEntity): Long {
        return buildDao.insertBuild(build)
    }

    suspend fun update(build: BuildEntity) {
        buildDao.updateBuild(build)
    }

    suspend fun delete(build: BuildEntity) {
        buildDao.deleteBuild(build)
    }

    suspend fun clearAll() {
        buildDao.clearHistory()
    }
}
