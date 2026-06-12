package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.compiler.BuildStep
import com.example.compiler.LogLine
import com.example.compiler.LogType
import com.example.compiler.ProjectStats
import com.example.data.BuildEntity
import com.example.viewmodel.BuildViewModel
import com.example.viewmodel.ExampleRepo
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompilerApp(
    viewModel: BuildViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val buildHistory by viewModel.buildHistory.collectAsStateWithLifecycle()
    val buildStep by viewModel.buildStep.collectAsStateWithLifecycle()
    val logStream by viewModel.logStream.collectAsStateWithLifecycle()

    val inputRepoUrl by viewModel.inputRepoUrl.collectAsStateWithLifecycle()
    val selectedZipUri by viewModel.selectedLocalZipUri.collectAsStateWithLifecycle()
    val selectedZipName by viewModel.selectedLocalZipName.collectAsStateWithLifecycle()
    val activeBuildDetail by viewModel.activeBuildDetail.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var fileName = "project_source.zip"
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        fileName = it.getString(index)
                    }
                }
            }
            viewModel.setLocalZip(uri, fileName)
            Toast.makeText(context, "Loaded zip: $fileName", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = "Build compiler logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                "Build Compiler Console",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "AI Studio Offline Compiler",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ),
                actions = {
                    if (buildHistory.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.clearBuildHistory()
                                Toast.makeText(context, "Build histories removed", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Clear all compiles",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header overview
                Text(
                    text = "A flexible companion utility. Paste public GitHub repositories containing apps exported from AI studio or pick a local source ZIP files to build your final installation binaries.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 1. Input Panel Card
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Input,
                                contentDescription = "Input source configuration",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                "Source Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Path 1: GitHub URL Field
                        OutlinedTextField(
                            value = inputRepoUrl,
                            onValueChange = {
                                viewModel.setInputUrl(it)
                                if (it.isNotEmpty()) viewModel.clearLocalZip()
                            },
                            label = { Text("GitHub Repo URL / ZIP link") },
                            placeholder = { Text("https://github.com/username/repository") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                            leadingIcon = {
                                Icon(Icons.Filled.Link, contentDescription = "Remote link URL")
                            },
                            trailingIcon = {
                                if (inputRepoUrl.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setInputUrl("") }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear field")
                                    }
                                } else {
                                    // Clipboard suggestion button
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = clipboard.primaryClip
                                            if (clip != null && clip.itemCount > 0) {
                                                val text = clip.getItemAt(0).text?.toString() ?: ""
                                                if (text.contains("github.com")) {
                                                    viewModel.setInputUrl(text)
                                                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Clipboard does not contain a GitHub URL", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.ContentPaste, contentDescription = "Paste GitHub from clipboard")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("github_url_input")
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                "OR",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }

                        // Path 2: Pick Local Zip file
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (selectedZipUri != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderZip,
                                        contentDescription = "Zip loaded",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            selectedZipName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "Local file source loaded",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.clearLocalZip() }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear file selection"
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { filePickerLauncher.launch("application/zip") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .height(48.dp)
                                        .testTag("choose_zip_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.FolderZip,
                                        contentDescription = "Pick folder",
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text("Pick Local App Source ZIP")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val hasInput = selectedZipUri != null || inputRepoUrl.trim().isNotEmpty()

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.runCompile()
                            },
                            enabled = hasInput,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("compile_now_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.SettingsSuggest,
                                contentDescription = "Compile gear",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                "COMPILE & ANALYZE CODE",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp
                            )
                        }
                    }
                }

                // 2. Pre-configured Repository Templates
                Text(
                    "Try Standard Templates",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(viewModel.templates) { template ->
                        TemplateItem(template = template) {
                            viewModel.setInputUrl(template.url)
                            viewModel.clearLocalZip()
                            Toast.makeText(context, "Template applied: ${template.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // 3. Build History Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Compilation Archive (${buildHistory.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (buildHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "No builds",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No builds performed yet",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Active compiles log history will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        items(buildHistory) { record ->
                            BuildRecordRow(
                                record = record,
                                onClick = { viewModel.viewBuildDetails(record) },
                                onDelete = { viewModel.deleteBuild(record) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            // A. Real-time active compilation modal/panel overlay
            if (buildStep != BuildStep.Idle) {
                ActiveCompileScreen(
                    buildStep = buildStep,
                    logLines = logStream,
                    onCancel = { viewModel.cancelOrResetBuild() }
                )
            }

            // B. Detailed Build Statistics and logs archive dialog (for browsing past builds)
            if (activeBuildDetail != null) {
                BuildHistoryDetailsOverlay(
                    build = activeBuildDetail!!,
                    onClose = { viewModel.closeBuildDetails() },
                    onShareApk = { apkPath -> shareApk(context, apkPath) }
                )
            }
        }
    }
}

@Composable
fun TemplateItem(
    template: ExampleRepo,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .width(220.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val icon = when (template.iconName) {
                "sports_esports" -> Icons.Filled.SportsEsports
                "cloudy" -> Icons.Filled.CloudQueue
                else -> Icons.Filled.Spa
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = template.name,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    template.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                minLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun BuildRecordRow(
    record: BuildEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateString = remember(record.timestamp) {
        val date = Date(record.timestamp)
        val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        format.format(date)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status circle icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (record.status == 0) Color(0xFFE2F6EA) else Color(0xFFFCE8E6),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (record.status == 0) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = "Build Status",
                tint = if (record.status == 0) Color(0xFF137333) else Color(0xFFC5221F),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                record.appName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    record.packageName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                VerticalDivider(modifier = Modifier.height(10.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${record.fileCount} elements",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                "Duration: ${record.durationMs / 1000.0}s  •  $dateString",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete record",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Browse record detail",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// FULL-BLEED ACTIVE COMPILING TERMINAL DASHBOARD
@Composable
fun ActiveCompileScreen(
    buildStep: BuildStep,
    logLines: List<LogLine>,
    onCancel: () -> Unit
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Automatically scrolls the console terminal to the latest line as output flows in
    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            listState.animateScrollToItem(logLines.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .testTag("active_compile_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "COMPILER PIPELINE SESSION",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                    Text(
                        when (buildStep) {
                            is BuildStep.Downloading -> "Step 1/5: Downloading Repository ZIP..."
                            is BuildStep.Extracting -> "Step 2/5: Extracting files..."
                            is BuildStep.Analyzing -> "Step 3/5: Running Meta Compiler..."
                            is BuildStep.Compiling -> "Step 4/5: Compiling Code Assets (*.kt, *.xml)..."
                            is BuildStep.Success -> "Step 5/5: Compilation Successful!"
                            is BuildStep.Failed -> "Pipeline Broken: Errors detected."
                            else -> "Building Applet..."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (buildStep !is BuildStep.Success && buildStep !is BuildStep.Failed) {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Cancel,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel Build", fontSize = 12.sp)
                    }
                } else {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Exit compiler console"
                        )
                    }
                }
            }

            // Overall Progress bar
            AnimatedVisibility(
                visible = buildStep !is BuildStep.Success && buildStep !is BuildStep.Failed,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val progress = when (buildStep) {
                    is BuildStep.Downloading -> buildStep.progress * 0.3f
                    is BuildStep.Extracting -> 0.35f
                    is BuildStep.Analyzing -> 0.45f
                    is BuildStep.Compiling -> 0.5f + (buildStep.progress * 0.5f)
                    else -> 0f
                }

                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    val taskName = if (buildStep is BuildStep.Compiling) buildStep.currentTask else "Preprocessing elements..."
                    Text(
                        text = "Task: $taskName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }

            // Success Card Header / Failure Notification Card
            when (buildStep) {
                is BuildStep.Success -> {
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFE2F6EA)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "OK",
                                tint = Color(0xFF137333),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Build succeeded in packaging APK!",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF137333),
                                    fontSize = 15.sp
                                )
                                Text(
                                    "Processed standard debug credentials and zipalign successfully.",
                                    color = Color(0xFF137333).copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                is BuildStep.Failed -> {
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFCE8E6)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = "Fail",
                                tint = Color(0xFFC5221F),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Build failed with source diagnostics!",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC5221F),
                                    fontSize = 15.sp
                                )
                                Text(
                                    "Mismatched characters or structure syntax problems found.",
                                    color = Color(0xFFC5221F).copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                else -> {}
            }

            // Dark CLI Micro Terminal Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF151515), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFEF5350), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFFFCA28), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF66BB6A), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "bash - gradle build --console=plain",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }

                IconButton(
                    onClick = {
                        val fullTextLogs = logLines.joinToString("\n") { it.text }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Compiler Logs", fullTextLogs))
                        Toast.makeText(context, "Terminal logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy terminal logs",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .border(
                        1.dp,
                        Color(0xFF222222),
                        RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                    )
                    .padding(12.dp)
            ) {
                items(logLines) { line ->
                    LogTerminalLine(line)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action footer depending on compilation state
            if (buildStep is BuildStep.Success) {
                val stats = buildStep.stats
                ElevatedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Extracted Bundle Metadata",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Applet: ${stats.appName} (${stats.packageName})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Code length: ${stats.linesOfCode} lines of code • Permissions: ${stats.permissions.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Button(
                    onClick = {
                        val file = buildStep.apkFile
                        val authority = "${context.packageName}.fileprovider"
                        try {
                            val uri = FileProvider.getUriForFile(context, authority, file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/vnd.android.package-archive"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Install / Run Applet APK"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Installer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("install_compiled_apk_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF137333)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = "Install Apk")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VERIFY & INSTALL APPLET", fontWeight = FontWeight.Bold)
                }
            } else if (buildStep is BuildStep.Failed) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("dismiss_compilation_failure"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Fix errors")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DISMISS FAILURE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LogTerminalLine(line: LogLine) {
    val color = when (line.type) {
        LogType.INFO -> Color(0xFFE0E0E0)
        LogType.WARNING -> Color(0xFFFFB300)
        LogType.ERROR -> Color(0xFFEF5350)
        LogType.SUCCESS -> Color(0xFF66BB6A)
        LogType.TASK -> Color(0xFF29B6F6)
        LogType.VERBOSE -> Color(0xFF757575)
    }

    val styleText = buildString {
        when (line.type) {
            LogType.TASK -> append("> TASK ")
            LogType.WARNING -> append("[WARNING] ")
            LogType.ERROR -> append("[ERROR] ")
            LogType.SUCCESS -> append("[SUCCESS] ")
            else -> {}
        }
        append(line.text)
    }

    Text(
        text = styleText,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

// BROWSE DETAILED LOGS ARCHIVE DIALOG
@Composable
fun BuildHistoryDetailsOverlay(
    build: BuildEntity,
    onClose: () -> Unit,
    onShareApk: (String) -> Unit
) {
    val dateString = remember(build.timestamp) {
        val date = Date(build.timestamp)
        val format = SimpleDateFormat("EEEE, MMM dd, yyyy 'at' HH:mm:ss", Locale.getDefault())
        format.format(date)
    }

    val context = LocalContext.current
    val logLinesList = remember(build.logs) {
        build.logs.split("\n").map { lineText ->
            when {
                lineText.startsWith("[ERROR]") -> LogLine(lineText.removePrefix("[ERROR]").trim(), LogType.ERROR)
                lineText.startsWith("[WARNING]") -> LogLine(lineText.removePrefix("[WARNING]").trim(), LogType.WARNING)
                lineText.startsWith("[SUCCESS]") -> LogLine(lineText.removePrefix("[SUCCESS]").trim(), LogType.SUCCESS)
                lineText.startsWith("[TASK]") -> LogLine(lineText.removePrefix("[TASK]").trim(), LogType.TASK)
                lineText.startsWith("[VERBOSE]") -> LogLine(lineText.removePrefix("[VERBOSE]").trim(), LogType.VERBOSE)
                else -> LogLine(lineText.removePrefix("[INFO]").trim(), LogType.INFO)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .testTag("history_detail_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("back_to_dashboard")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ARCHIVED COMPILE SESSION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                    Text(
                        build.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Stats row
            ElevatedCard(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Compilation Analytics",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("PACKAGE NAME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(build.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("BUILD STATUS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (build.status == 0) "SUCCESSFUL" else "FAILED",
                                color = if (build.status == 0) Color(0xFF137333) else Color(0xFFC5221F),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("BUILD DURATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${build.durationMs / 1000.0} seconds", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("SOURCE COMPONENTS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${build.fileCount} parsed elements", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        "Created: $dateString",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        "Origins: ${build.repoUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Monospace Terminal scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF151515), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Compile log payload file",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Archived Logs", build.logs))
                        Toast.makeText(context, "Archived logs copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy logs",
                        tint = Color.LightGray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .border(
                        1.dp,
                        Color(0xFF222222),
                        RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                    )
                    .padding(12.dp)
            ) {
                items(logLinesList) { line ->
                    LogTerminalLine(line)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Install button if successful build, otherwise fix tips
            if (build.status == 0 && build.apkPath != null) {
                Button(
                    onClick = { onShareApk(build.apkPath) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF137333)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("install_archived_apk_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = "Run installer")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VERIFY & INSTALL APK ARCHIVE", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("close_failed_records_detail"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("CLOSE payload ANALYSIS", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun shareApk(context: Context, filePath: String) {
    val file = File(filePath)
    if (!file.exists()) {
        Toast.makeText(context, "Error: Compiled binary mismatch or delete has occurred", Toast.LENGTH_SHORT).show()
        return
    }
    val authority = "${context.packageName}.fileprovider"
    try {
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Install / Run Applet APK"))
    } catch (e: Exception) {
        Toast.makeText(context, "Sharing failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
