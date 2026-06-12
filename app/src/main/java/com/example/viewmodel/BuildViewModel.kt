package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.compiler.BuildStep
import com.example.compiler.CompilerEngine
import com.example.compiler.LogLine
import com.example.data.AppDatabase
import com.example.data.BuildEntity
import com.example.data.BuildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExampleRepo(
    val name: String,
    val url: String,
    val description: String,
    val iconName: String
)

class BuildViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BuildRepository
    val buildHistory: StateFlow<List<BuildEntity>>

    val compilerEngine = CompilerEngine(application.applicationContext)
    val buildStep: StateFlow<BuildStep> = compilerEngine.buildStep
    val logStream: StateFlow<List<LogLine>> = compilerEngine.logStream

    // UI Input states
    val inputRepoUrl = MutableStateFlow("")
    val selectedLocalZipUri = MutableStateFlow<Uri?>(null)
    val selectedLocalZipName = MutableStateFlow("")

    // Selected historical build details modal state
    val activeBuildDetail = MutableStateFlow<BuildEntity?>(null)

    val templates = listOf(
        ExampleRepo(
            name = "Dolphin DSU Remote",
            url = "https://github.com/Steepaik/ai-studio-Dolphin-DSU-remote",
            description = "A gaming controller overlay for DSU servers mapping screen inputs to Dolphin emulator motions.",
            iconName = "sports_esports"
        ),
        ExampleRepo(
            name = "Weather Live Tracker",
            url = "https://github.com/Steepaik/ai-studio-weather-tracker",
            description = "An adaptive Weather forecasting widget styled with responsive glassmorphism Material 3 panels.",
            iconName = "cloudy"
        ),
        ExampleRepo(
            name = "Zen Minimalist Checklist",
            url = "https://github.com/Steepaik/ai-studio-zen-checklist",
            description = "A single-screen offline-first focus list with UTC timer, custom statistics, and local database.",
            iconName = "spa"
        )
    )

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BuildRepository(database.buildDao())
        buildHistory = repository.allBuilds.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun setInputUrl(url: String) {
        inputRepoUrl.value = url
    }

    fun setLocalZip(uri: Uri, name: String) {
        selectedLocalZipUri.value = uri
        selectedLocalZipName.value = name
        // Clear URL if local zip is selected to avoid confusion
        inputRepoUrl.value = ""
    }

    fun clearLocalZip() {
        selectedLocalZipUri.value = null
        selectedLocalZipName.value = ""
    }

    fun runCompile() {
        viewModelScope.launch {
            val uri = selectedLocalZipUri.value
            val urlStr = inputRepoUrl.value.trim()

            if (uri != null) {
                compilerEngine.compileFromZipUri(uri) { completedBuild ->
                    saveBuildToHistory(completedBuild)
                }
            } else if (urlStr.isNotEmpty()) {
                compilerEngine.compileFromGithub(urlStr) { completedBuild ->
                    saveBuildToHistory(completedBuild)
                }
            }
        }
    }

    fun cancelOrResetBuild() {
        compilerEngine.reset()
    }

    private fun saveBuildToHistory(build: BuildEntity) {
        viewModelScope.launch {
            repository.insert(build)
        }
    }

    fun deleteBuild(build: BuildEntity) {
        viewModelScope.launch {
            repository.delete(build)
            if (activeBuildDetail.value?.id == build.id) {
                activeBuildDetail.value = null
            }
        }
    }

    fun clearBuildHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun viewBuildDetails(build: BuildEntity) {
        activeBuildDetail.value = build
    }

    fun closeBuildDetails() {
        activeBuildDetail.value = null
    }
}
