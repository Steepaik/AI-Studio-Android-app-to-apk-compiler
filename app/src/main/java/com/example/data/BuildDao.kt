package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildDao {
    @Query("SELECT * FROM build_history ORDER BY timestamp DESC")
    fun getAllBuilds(): Flow<List<BuildEntity>>

    @Query("SELECT * FROM build_history WHERE id = :id LIMIT 1")
    suspend fun getBuildById(id: Int): BuildEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuild(build: BuildEntity): Long

    @Update
    suspend fun updateBuild(build: BuildEntity): Int

    @Delete
    suspend fun deleteBuild(build: BuildEntity): Int

    @Query("DELETE FROM build_history")
    suspend fun clearHistory(): Int
}
