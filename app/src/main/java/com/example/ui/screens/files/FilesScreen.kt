package com.example.ui.screens.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ActiveCloudDownloadsCard
import com.example.ui.components.CloudDownloadDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.FileItemRow
import com.example.ui.components.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    onVideoClick: ((url: String, title: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val displayedFiles by viewModel.displayedFiles.collectAsStateWithLifecycle()
    val cloudDownloads by viewModel.cloudDownloads.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }
    var isSortMenuOpen by remember { mutableStateOf(false) }
    var isMoreMenuOpen by remember { mutableStateOf(false) }
    var isFabMenuOpen by remember { mutableStateOf(false) }
    var isCloudDownloadOpen by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Handle back button when inside a folder
    BackHandler(enabled = currentPath != "/") {
        viewModel.navigateUp()
    }

    if (isCloudDownloadOpen) {
        CloudDownloadDialog(
            currentFolder = currentPath,
            onDismiss = { isCloudDownloadOpen = false },
            onStartDownload = { url, filename, dest ->
                viewModel.startCloudDownload(url, filename, dest)
                scope.launch {
                    snackbarHostState.showSnackbar("Cloud download started: $filename")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("files_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(visible = isFabMenuOpen) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        // Cloud Web Download Action
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    isFabMenuOpen = false
                                    isCloudDownloadOpen = true
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Cloud Download",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Cloud Download",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // New Folder Action
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    isFabMenuOpen = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Create Folder: Coming in v0.4")
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "New Folder",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "New Folder",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Upload Action
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    isFabMenuOpen = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar("File Upload: Coming in v0.4")
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Upload File",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Upload",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { isFabMenuOpen = !isFabMenuOpen },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("files_fab")
                ) {
                    Icon(
                        imageVector = if (isFabMenuOpen) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "File Actions"
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Top Bar
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentPath != "/") {
                                IconButton(onClick = { viewModel.navigateUp() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Navigate Up"
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "My Files",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "storage: $currentPath",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isCloudDownloadOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Cloud Download",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }

                            Box {
                                IconButton(onClick = { isSortMenuOpen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort Files"
                                    )
                                }
                                DropdownMenu(
                                    expanded = isSortMenuOpen,
                                    onDismissRequest = { isSortMenuOpen = false }
                                ) {
                                    FileSortOrder.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = option.label,
                                                    fontWeight = if (sortOrder == option) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (sortOrder == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                viewModel.setSortOrder(option)
                                                isSortMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }

                            Box {
                                IconButton(onClick = { isMoreMenuOpen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More"
                                    )
                                }
                                DropdownMenu(
                                    expanded = isMoreMenuOpen,
                                    onDismissRequest = { isMoreMenuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Server: ${com.example.network.ServerConfig.baseUrl.value.ifBlank { "Not configured" }}") },
                                        onClick = {
                                            isMoreMenuOpen = false
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Target: ${com.example.network.ServerConfig.baseUrl.value.ifBlank { "Configure in Settings" }}")
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Refresh list") },
                                        onClick = {
                                            isMoreMenuOpen = false
                                            viewModel.refresh()
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Refreshing from server...")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Search input when active
                    AnimatedVisibility(visible = isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search in this folder...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .testTag("file_search_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Active Cloud Downloads Card
            if (cloudDownloads.isNotEmpty()) {
                item {
                    ActiveCloudDownloadsCard(
                        tasks = cloudDownloads,
                        onCancelTask = { viewModel.cancelCloudDownload(it) },
                        onClearCompleted = { viewModel.clearCompletedDownloads() }
                    )
                }
            }

            // Subfolders quick carousel if any folders exist
            val subfolders = displayedFiles.filter { it.isFolder }
            if (subfolders.isNotEmpty() && searchQuery.isEmpty()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SectionHeader(title = "Folders")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(subfolders) { folder ->
                                QuickFolderChip(
                                    name = folder.name,
                                    onClick = { viewModel.navigateToFolder(folder.path) }
                                )
                            }
                        }
                    }
                }
            }

            // File items section header
            item {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader(
                    title = if (currentPath == "/") "All Items" else currentPath.removePrefix("/"),
                    actionLabel = "${displayedFiles.size} items"
                )
            }

            if (displayedFiles.isEmpty()) {
                item {
                    EmptyState(
                        title = if (searchQuery.isNotEmpty()) "No matching files" else "No files found",
                        description = if (searchQuery.isNotEmpty()) {
                            "No results matching \"$searchQuery\" in $currentPath"
                        } else {
                            "No files found at $currentPath on ${com.example.network.ServerConfig.baseUrl.value.ifBlank { "server" }}.\nEnsure server is online and reachable on local Wi-Fi."
                        }
                    )
                }
            } else {
                items(displayedFiles, key = { it.id }) { file ->
                    FileItemRow(
                        fileItem = file,
                        onClick = {
                            if (file.isFolder) {
                                viewModel.navigateToFolder(file.path)
                            } else {
                                com.example.data.repository.HttpServerRepository.recordActivity(
                                    title = file.name,
                                    subtitle = file.formattedSize ?: "Opened file",
                                    type = file.type
                                )
                                if (!file.downloadUrl.isNullOrBlank()) {
                                    if (file.type == com.example.data.model.FileType.VIDEO && onVideoClick != null) {
                                        onVideoClick(file.downloadUrl, file.name)
                                        return@FileItemRow
                                    }
                                    try {
                                        val mime = when (file.type) {
                                            com.example.data.model.FileType.VIDEO -> "video/*"
                                            com.example.data.model.FileType.AUDIO -> "audio/*"
                                            com.example.data.model.FileType.IMAGE -> "image/*"
                                            com.example.data.model.FileType.PDF -> "application/pdf"
                                            else -> "*/*"
                                        }
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(file.downloadUrl), mime)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(file.downloadUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(browserIntent)
                                        } catch (e2: Exception) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Opening: ${file.downloadUrl}")
                                            }
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Selected: ${file.name}")
                                    }
                                }
                            }
                        },
                        onMoreClick = {
                            if (!file.downloadUrl.isNullOrBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("File URL", file.downloadUrl)
                                clipboard?.setPrimaryClip(clip)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Copied link: ${file.downloadUrl}")
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("${file.name} • ${file.formattedSize ?: "File"}")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickFolderChip(
    name: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = name,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
