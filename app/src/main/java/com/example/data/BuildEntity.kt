package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "build_history")
data class BuildEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val repoUrl: String,
    val appName: String,
    val packageName: String,
    val status: Int, // 0 = SUCCESS, 1 = FAILED, 2 = RUNNING
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val fileCount: Int = 0,
    val logs: String = "",
    val errorCount: Int = 0,
    val apkPath: String? = null
)
