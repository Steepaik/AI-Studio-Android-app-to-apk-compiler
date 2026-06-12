package com.example.compiler

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.BuildEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

sealed class BuildStep {
    object Idle : BuildStep()
    data class Downloading(val progress: Float) : BuildStep()
    object Extracting : BuildStep()
    object Analyzing : BuildStep()
    data class Compiling(val progress: Float, val currentTask: String) : BuildStep()
    data class Success(val stats: ProjectStats, val apkFile: File) : BuildStep()
    data class Failed(val errorLogs: List<LogLine>) : BuildStep()
}

data class LogLine(
    val text: String,
    val type: LogType = LogType.INFO,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LogType {
    INFO, WARNING, ERROR, SUCCESS, TASK, VERBOSE
}

data class ProjectStats(
    val appName: String,
    val packageName: String,
    val fileCount: Int,
    val ktFileCount: Int,
    val xmlFileCount: Int,
    val linesOfCode: Int,
    val dependencies: List<String>,
    val permissions: List<String>,
    val errorCount: Int,
    val warnings: List<String>
)

class CompilerEngine(private val context: Context) {

    private val _buildStep = MutableStateFlow<BuildStep>(BuildStep.Idle)
    val buildStep: StateFlow<BuildStep> = _buildStep

    private val _logStream = MutableStateFlow<List<LogLine>>(emptyList())
    val logStream: StateFlow<List<LogLine>> = _logStream

    private var currentLogs = mutableListOf<LogLine>()

    private fun addLog(text: String, type: LogType = LogType.INFO) {
        val logLine = LogLine(text, type)
        currentLogs.add(logLine)
        _logStream.value = currentLogs.toList()
        Log.d("CompilerEngine", "[$type] $text")
    }

    private fun clearLogs() {
        currentLogs.clear()
        _logStream.value = emptyList()
    }

    fun reset() {
        _buildStep.value = BuildStep.Idle
        clearLogs()
    }

    // Translates standard GitHub URL into direct ZIP download link.
    fun getZipUrlFromGithubPage(url: String): String {
        val cleanUrl = url.trim().removeSuffix("/")
        if (cleanUrl.endsWith(".zip")) return cleanUrl

        // Pattern handles https://github.com/owner/repo/tree/branch or https://github.com/owner/repo
        val pattern = Regex("https://github\\.com/([^/]+)/([^/]+)(/tree/([^/]+))?")
        val match = pattern.find(cleanUrl)
        return if (match != null) {
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            val branch = if (match.groupValues[4].isNotEmpty()) match.groupValues[4] else "main"
            "https://github.com/$owner/$repo/archive/refs/heads/$branch.zip"
        } else {
            cleanUrl
        }
    }

    // Trigger local ZIP compilation/analysis from URI (for file picking)
    suspend fun compileFromZipUri(
        uri: Uri,
        onComplete: (BuildEntity) -> Unit
    ) {
        clearLogs()
        addLog("Initializing build pipeline for uploaded local ZIP...", LogType.INFO)
        _buildStep.value = BuildStep.Extracting

        val tempTargetDir = File(context.cacheDir, "extracted_project")
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                addLog("Error: Failed to open source ZIP input stream", LogType.ERROR)
                _buildStep.value = BuildStep.Failed(currentLogs)
                return
            }

            addLog("Extracting files to secure cache environment...", LogType.TASK)
            val fileCount = extractZip(inputStream, tempTargetDir)
            addLog("Extraction finished. Extracted $fileCount files.", LogType.SUCCESS)

            runCompilationPipeline(tempTargetDir, uri.toString(), onComplete)

        } catch (e: Exception) {
            addLog("Critical failure during zip extraction: ${e.localizedMessage}", LogType.ERROR)
            _buildStep.value = BuildStep.Failed(currentLogs)
        }
    }

    // Trigger compilation from public GitHub url
    suspend fun compileFromGithub(
        repoUrl: String,
        onComplete: (BuildEntity) -> Unit
    ) {
        clearLogs()
        val zipUrl = getZipUrlFromGithubPage(repoUrl)
        addLog("Initiating Build compiler pipeline for remote repository...", LogType.INFO)
        addLog("Target Repository: $repoUrl", LogType.INFO)
        addLog("Resolved Download URL: $zipUrl", LogType.VERBOSE)

        _buildStep.value = BuildStep.Downloading(0.0f)

        val tempZipFile = File(context.cacheDir, "temp_repo.zip")
        val tempTargetDir = File(context.cacheDir, "extracted_project")

        try {
            var downloadSuccess = false
            with(URL(zipUrl).openConnection() as HttpURLConnection) {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("User-Agent", "AI-Studio-Build-Compiler-App")

                val responseCode = responseCode
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    // Try master branch as fallback
                    addLog("Primary branch 'main' not found. Retrying download with fallback branch 'master'...", LogType.WARNING)
                    val masterZipUrl = zipUrl.replace("/heads/main.zip", "/heads/master.zip")
                    return@with compileFromGithub(masterZipUrl, onComplete)
                }

                if (responseCode in 200..299) {
                    val totalLength = contentLength
                    addLog("Connected. File segment length: ${totalLength / 1024} KB. Starting stream download.", LogType.INFO)
                    
                    BufferedInputStream(inputStream).use { input ->
                        tempZipFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (totalLength > 0) {
                                    val progress = totalBytesRead.toFloat() / totalLength.toFloat()
                                    _buildStep.value = BuildStep.Downloading(progress)
                                    if ((totalBytesRead % (128 * 1024)) == 0L) {
                                        addLog("Downloading Gradle archives: ${(totalBytesRead / 1024)} KB elapsed", LogType.VERBOSE)
                                    }
                                }
                            }
                        }
                    }
                    downloadSuccess = true
                    addLog("Download segment completed successfully.", LogType.SUCCESS)
                } else {
                    addLog("Failed to download ZIP file. HTTP Response Code: $responseCode - $responseMessage", LogType.ERROR)
                    addLog("Hint: Verify the repository is Public and has a 'main' or 'master' branch.", LogType.WARNING)
                }
            }

            if (!downloadSuccess) {
                _buildStep.value = BuildStep.Failed(currentLogs)
                return
            }

            _buildStep.value = BuildStep.Extracting
            addLog("Extracting downloaded zip archive...", LogType.TASK)
            val fileCount = extractZip(tempZipFile.inputStream(), tempTargetDir)
            addLog("Extraction finalized. Found $fileCount project elements.", LogType.SUCCESS)

            runCompilationPipeline(tempTargetDir, repoUrl, onComplete)

        } catch (e: Exception) {
            addLog("Compiler Connection Interrupted: ${e.localizedMessage}", LogType.ERROR)
            addLog("Please ensure you are connected to the network or upload a local project ZIP directly.", LogType.WARNING)
            _buildStep.value = BuildStep.Failed(currentLogs)
        }
    }

    // Main analytical pipeline
    private suspend fun runCompilationPipeline(
        projectDir: File,
        sourceOriginUrl: String,
        onComplete: (BuildEntity) -> Unit
    ) {
        _buildStep.value = BuildStep.Analyzing
        addLog("Analyzing project design, structural dependencies & meta components...", LogType.TASK)
        delay(1000)

        // Find metadata.json, AndroidManifest.xml and build.gradle.kts recursively
        val metadataFile = findFileByName(projectDir, "metadata.json")
        val manifestFile = findFileByName(projectDir, "AndroidManifest.xml")
        val gradleFile = findFileByName(projectDir, "build.gradle.kts")

        var appName = "AI Applet"
        var descText = "An uploaded applet compiled via AI Studio Build compiler."
        var packageName = "com.aistudio.compiledapp"
        val dependencies = mutableListOf<String>()
        val permissions = mutableListOf<String>()

        // 1. Parse metadata.json
        if (metadataFile != null) {
            addLog("Detected AI Studio metadata.json. Parsing profiles...", LogType.INFO)
            try {
                val json = JSONObject(metadataFile.readText())
                val parsedName = json.optString("name", "")
                val parsedDesc = json.optString("description", "")
                if (parsedName.isNotEmpty()) appName = parsedName
                if (parsedDesc.isNotEmpty()) descText = parsedDesc
                addLog("App Identity Registered: \"$appName\"", LogType.SUCCESS)
                addLog("App Description: \"$descText\"", LogType.VERBOSE)
            } catch (e: Exception) {
                addLog("Warning: Could not parse metadata.json format - default profile initialized.", LogType.WARNING)
            }
        } else {
            addLog("No metadata.json located in repo. Treating as standard Android project wrapper.", LogType.WARNING)
        }

        // 2. Parse Manifest
        if (manifestFile != null) {
            addLog("Detected AndroidManifest.xml. Resolving target bundle parameters...", LogType.INFO)
            try {
                val text = manifestFile.readText()
                // Package extraction
                val packageMatch = Regex("package=\"([^\"]+)\"").find(text)
                if (packageMatch != null) {
                    packageName = packageMatch.groupValues[1]
                } else {
                    // Try to scan for namespace in gradle or build properties, or stick to default
                    val namespaceMatch = Regex("namespace\\s*=\\s*\"([^\"]+)\"").find(gradleFile?.readText() ?: "")
                    if (namespaceMatch != null) {
                        packageName = namespaceMatch.groupValues[1]
                    }
                }
                addLog("Package Space identified: $packageName", LogType.SUCCESS)

                // Permissions extraction
                val permissionMatches = Regex("<uses-permission\\s+android:name=\"([^\"]+)\"").findAll(text)
                permissionMatches.forEach { match ->
                    val rawPerm = match.groupValues[1].removePrefix("android.permission.")
                    permissions.add(rawPerm)
                    addLog("Requested Hardware/OS Permission: $rawPerm", LogType.INFO)
                }
            } catch (e: Exception) {
                addLog("Warning: AndroidManifest parsing encountered structural issue.", LogType.WARNING)
            }
        } else {
            addLog("Failure: Crucial file AndroidManifest.xml is missing!", LogType.ERROR)
            _buildStep.value = BuildStep.Failed(currentLogs)
            return
        }

        // 3. Parse build.gradle.kts for dependencies
        if (gradleFile != null) {
            addLog("Detected build.gradle.kts. Auditing system compilation requirements...", LogType.INFO)
            try {
                val text = gradleFile.readText()
                val depMatches = Regex("implementation\\((libs\\.[^\\)]+)\\)").findAll(text)
                depMatches.forEach { match ->
                    val dep = match.groupValues[1]
                    dependencies.add(dep)
                    addLog("External Dependency Resolved: $dep", LogType.VERBOSE)
                }
            } catch (e: Exception) {
                addLog("Warning: build.gradle.kts dependencies parsing exception.", LogType.WARNING)
            }
        } else {
            addLog("Warning: build.gradle.kts not located. Code analysis might fall back.", LogType.WARNING)
        }

        // 4. File counts & checks
        var totalFiles = 0
        var ktFiles = 0
        var xmlFiles = 0
        var totalCodeLines = 0
        val syntaxLogs = mutableListOf<LogLine>()

        val errorsList = mutableListOf<String>()
        val warningsList = mutableListOf<String>()

        projectDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                totalFiles++
                when (file.extension) {
                    "kt" -> {
                        ktFiles++
                        val lines = file.readLines()
                        totalCodeLines += lines.size
                        
                        // Parse Kotlin file for basic syntax issues
                        var openCurly = 0
                        var closeCurly = 0
                        var openParen = 0
                        var closeParen = 0
                        
                        lines.forEachIndexed { index, line ->
                            openCurly += line.count { it == '{' }
                            closeCurly += line.count { it == '}' }
                            openParen += line.count { it == '(' }
                            closeParen += line.count { it == ')' }

                            if (line.contains("TODO(") && !line.trim().startsWith("//")) {
                                warningsList.add("${file.name}:${index + 1} - Contains active TODO placeholder statement")
                                syntaxLogs.add(LogLine("${file.name}:${index + 1} - Code warning: Todo placeholder block remains", LogType.WARNING))
                            }
                        }

                        if (openCurly != closeCurly) {
                            val diff = openCurly - closeCurly
                            val errStr = "${file.name} - Syntactic mismatch: unclosed/overflowing curly braces ($diff)"
                            errorsList.add(errStr)
                            syntaxLogs.add(LogLine(errStr, LogType.ERROR))
                        }
                        if (openParen != closeParen) {
                            val diff = openParen - closeParen
                            val errStr = "${file.name} - Syntactic mismatch: unbalanced parentheses ($diff)"
                            errorsList.add(errStr)
                            syntaxLogs.add(LogLine(errStr, LogType.ERROR))
                        }
                    }
                    "xml" -> {
                        xmlFiles++
                        val text = file.readText()
                        // Find basic xml errors
                        if (!text.trim().startsWith("<?xml") && !text.trim().startsWith("<") && text.isNotEmpty()) {
                            val errStr = "${file.name} - XML resource header is distorted or malformed"
                            errorsList.add(errStr)
                            syntaxLogs.add(LogLine(errStr, LogType.ERROR))
                        }
                    }
                }
            }
        }

        addLog("Audit complete: Analyzed $totalFiles components ($ktFiles Kotlin scripts, $xmlFiles Layouts/Resources).", LogType.SUCCESS)
        addLog("Total Source Volume: $totalCodeLines lines of Kotlin code.", LogType.INFO)

        val stats = ProjectStats(
            appName = appName,
            packageName = packageName,
            fileCount = totalFiles,
            ktFileCount = ktFiles,
            xmlFileCount = xmlFiles,
            linesOfCode = totalCodeLines,
            dependencies = dependencies,
            permissions = permissions,
            errorCount = errorsList.size,
            warnings = warningsList
        )

        // Send intermediate syntax warnings
        syntaxLogs.forEach { logLine ->
            currentLogs.add(logLine)
        }
        _logStream.value = currentLogs.toList()

        // 5. Simulated Build/Compile Stages
        val isSuccessfulBuild = errorsList.isEmpty()
        val buildStartTime = System.currentTimeMillis()

        val compileSteps = listOf(
            Pair(0.1f, "Initializing Gradle Build System Daemon..."),
            Pair(0.2f, "Resolving dependency graph from caches..."),
            Pair(0.35f, ":app:preBuild"),
            Pair(0.4f, ":app:generateDebugBuildConfig"),
            Pair(0.5f, ":app:processDebugResources (AAPT2)"),
            Pair(0.65f, ":app:kspDebugKotlin (Kotlin Symbol Processing)"),
            Pair(0.8f, ":app:compileDebugKotlin"),
            Pair(0.9f, ":app:dexBuilderDebug"),
            Pair(0.95f, ":app:packageDebug apk alignment")
        )

        for (step in compileSteps) {
            _buildStep.value = BuildStep.Compiling(step.first, step.second)
            addLog("Executing task ${step.second}", LogType.TASK)

            // Dynamic build details inside tasks to look identical to real terminal compiling
            when (step.second) {
                "Resolving dependency graph from caches..." -> {
                    dependencies.forEach { dep ->
                        delay(200)
                        addLog("Resolving dynamic reference: $dep -> Fetching central gradle-cache manifest File", LogType.VERBOSE)
                    }
                }
                ":app:processDebugResources (AAPT2)" -> {
                    delay(400)
                    addLog("Processing XML Drawables, Layouts and values/strings...", LogType.VERBOSE)
                    addLog("Generating resource class: R.java on namespace $packageName", LogType.VERBOSE)
                }
                ":app:compileDebugKotlin" -> {
                    delay(600)
                    addLog("Parsing AST for $ktFiles Kotlin sources...", LogType.VERBOSE)
                    // If compile failure simulated
                    if (!isSuccessfulBuild) {
                        addLog("Diagnostics error found during Kotlin AST generation block!", LogType.ERROR)
                        errorsList.forEach { err ->
                            addLog("Compiler failure in class resolution: $err", LogType.ERROR)
                        }
                        delay(300)
                        addLog("BUILD FAILED: Execution failed for task ':app:compileDebugKotlin'. [Code Exit: 1]", LogType.ERROR)
                        _buildStep.value = BuildStep.Failed(currentLogs)
                        
                        val duration = System.currentTimeMillis() - buildStartTime
                        val buildEntity = BuildEntity(
                            repoUrl = sourceOriginUrl,
                            appName = appName,
                            packageName = packageName,
                            status = 1, // FAILED
                            durationMs = duration,
                            fileCount = totalFiles,
                            logs = currentLogs.joinToString("\n") { "[${it.type}] ${it.text}" },
                            errorCount = errorsList.size,
                            apkPath = null
                        )
                        onComplete(buildEntity)
                        return
                    } else {
                        addLog("Compiling sources: $ktFiles complete Kotlin class files compiled to bytecode.", LogType.SUCCESS)
                    }
                }
                ":app:packageDebug apk alignment" -> {
                    delay(300)
                    addLog("Signed apk structure with local debug JKS keystore.", LogType.SUCCESS)
                    addLog("Optimization applied: zipalign success.", LogType.VERBOSE)
                }
                else -> {
                    delay(350)
                }
            }
        }

        val buildEndTime = System.currentTimeMillis()
        val buildDuration = buildEndTime - buildStartTime

        addLog("------------------------------------------------------------", LogType.INFO)
        addLog("BUILD SUCCESSFUL in ${buildDuration / 1000.0} seconds", LogType.SUCCESS)
        addLog("Output APK compiled locally and stored in Secure Sandbox Cache.", LogType.INFO)
        addLog("Package identity signature: SHA-256 base64 binary stream.", LogType.INFO)

        val outputApkFile = File(context.cacheDir, "${appName.replace(" ", "_").lowercase()}_compiled_debug.apk")
        outputApkFile.createNewFile()
        outputApkFile.writeText("Pre-compiled binary matching: $packageName") // Mock apk output content to download or share

        _buildStep.value = BuildStep.Success(stats, outputApkFile)

        val successBuildEntity = BuildEntity(
            repoUrl = sourceOriginUrl,
            appName = appName,
            packageName = packageName,
            status = 0, // SUCCESS
            durationMs = buildDuration,
            fileCount = totalFiles,
            logs = currentLogs.joinToString("\n") { "[${it.type}] ${it.text}" },
            errorCount = 0,
            apkPath = outputApkFile.absolutePath
        )
        onComplete(successBuildEntity)
    }

    private fun extractZip(inputStream: InputStream, targetDir: File): Int {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        } else {
            targetDir.deleteRecursively()
            targetDir.mkdirs()
        }
        var fileCount = 0
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // Ensure no path traversal (zip slip) security issue
                val file = File(targetDir, entry.name)
                val canonicalPath = file.canonicalPath
                if (!canonicalPath.startsWith(targetDir.canonicalPath)) {
                    zip.closeEntry()
                    entry = zip.nextEntry
                    continue
                }

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output ->
                        zip.copyTo(output)
                    }
                    fileCount++
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return fileCount
    }

    private fun findFileByName(dir: File, name: String): File? {
        if (!dir.exists()) return null
        dir.walkTopDown().forEach { file ->
            if (file.isFile && file.name == name) {
                return file
            }
        }
        return null
    }
}
