package com.example.ui.screens.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileItem
import com.example.data.model.FileType
import com.example.data.repository.HttpServerRepository
import com.example.network.ServerConfig
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
    val allTransfers by viewModel.allTransfers.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val uploadStatus by viewModel.uploadStatus.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }
    var isSortMenuOpen by remember { mutableStateOf(false) }
    var isMoreMenuOpen by remember { mutableStateOf(false) }
    var isFabMenuOpen by remember { mutableStateOf(false) }
    var isCloudDownloadOpen by remember { mutableStateOf(false) }
    var isCreateFolderOpen by remember { mutableStateOf(false) }
    var selectedItemForOptions by remember { mutableStateOf<FileItem?>(null) }
    var itemToDelete by remember { mutableStateOf<FileItem?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // System File Picker for uploading files to the current server folder
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadFiles(context, uris) { total, _ ->
                scope.launch {
                    snackbarHostState.showSnackbar("Queued $total upload(s) with live progress & pause/resume")
                }
            }
        }
    }

    // Handle back button when inside a folder
    BackHandler(enabled = currentPath != "/") {
        viewModel.navigateUp()
    }

    // Cloud Web Download Dialog
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

    // Create Folder Dialog
    if (isCreateFolderOpen) {
        CreateFolderDialog(
            currentPath = currentPath,
            onDismiss = { isCreateFolderOpen = false },
            onConfirm = { folderName ->
                isCreateFolderOpen = false
                viewModel.createFolder(folderName) { success, msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            }
        )
    }

    // File / Folder Options Dialog
    if (selectedItemForOptions != null) {
        val item = selectedItemForOptions!!
        FileOptionsDialog(
            item = item,
            onDismiss = { selectedItemForOptions = null },
            onDownloadToDevice = {
                selectedItemForOptions = null
                val ok = viewModel.downloadFileToDevice(context, item)
                scope.launch {
                    if (ok) {
                        snackbarHostState.showSnackbar("Downloading ${item.name} to Downloads folder...")
                    } else {
                        snackbarHostState.showSnackbar("Download request failed")
                    }
                }
            },
            onOpenStream = {
                selectedItemForOptions = null
                openFileWithIntent(context, item, onVideoClick, scope, snackbarHostState)
            },
            onCopyLink = {
                selectedItemForOptions = null
                val link = item.downloadUrl ?: "http://${ServerConfig.serverHost.value}:${ServerConfig.serverPort.value}/api/files/download?path=${item.path}"
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Server Link", link))
                scope.launch {
                    snackbarHostState.showSnackbar("Link copied to clipboard")
                }
            },
            onDelete = {
                selectedItemForOptions = null
                itemToDelete = item
            }
        )
    }

    // Delete Confirmation Dialog
    if (itemToDelete != null) {
        val item = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    text = if (item.isFolder) "Delete Folder?" else "Delete File?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${item.name}\" from the server?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDelete = null
                        viewModel.deleteItem(item) { success, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
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

                        // Upload Files Action
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    isFabMenuOpen = false
                                    filePickerLauncher.launch("*/*")
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Upload Files",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Upload Files",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // New Folder Action
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    isFabMenuOpen = false
                                    isCreateFolderOpen = true
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "New Folder",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "New Folder",
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
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
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Quick Upload Button
                            IconButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                modifier = Modifier.testTag("quick_upload_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = "Upload Files",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Quick Create Folder Button
                            IconButton(
                                onClick = { isCreateFolderOpen = true },
                                modifier = Modifier.testTag("quick_create_folder_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = "New Folder",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Cloud Web Download Button
                            IconButton(onClick = { isCloudDownloadOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Cloud Download",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Search Button
                            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }

                            // Sort Menu
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

                            // More Menu
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
                                        text = { Text("Refresh list") },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                        onClick = {
                                            isMoreMenuOpen = false
                                            viewModel.refresh()
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Refreshing from server...")
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Upload files") },
                                        leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                                        onClick = {
                                            isMoreMenuOpen = false
                                            filePickerLauncher.launch("*/*")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("New folder") },
                                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                        onClick = {
                                            isMoreMenuOpen = false
                                            isCreateFolderOpen = true
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

            // Active Uploading Banner
            if (isUploading) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Uploading to DhilipHome Server",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = uploadStatus ?: "Transferring...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Active Transfers Card (Downloads & Uploads with Pause / Resume / Persistence)
            if (allTransfers.isNotEmpty()) {
                item {
                    ActiveCloudDownloadsCard(
                        tasks = allTransfers,
                        onCancelTask = { viewModel.cancelCloudDownload(it) },
                        onPauseTask = { viewModel.pauseTransfer(it) },
                        onResumeTask = { viewModel.resumeTransfer(it) },
                        onRetryTask = { viewModel.retryTransfer(it) },
                        onClearCompleted = { viewModel.clearCompletedDownloads() },
                        onPauseAll = { viewModel.pauseAllTransfers() },
                        onResumeAll = { viewModel.resumeAllTransfers() }
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
                            "No files found at $currentPath on ${ServerConfig.baseUrl.value.ifBlank { "server" }}.\nTap '+' to upload files or create folders."
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
                                HttpServerRepository.recordActivity(
                                    title = file.name,
                                    subtitle = file.formattedSize ?: "Opened file",
                                    type = file.type
                                )
                                openFileWithIntent(context, file, onVideoClick, scope, snackbarHostState)
                            }
                        },
                        onMoreClick = {
                            selectedItemForOptions = file
                        }
                    )
                }
            }
        }
    }
}

private fun openFileWithIntent(
    context: Context,
    file: FileItem,
    onVideoClick: ((url: String, title: String) -> Unit)?,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    if (file.downloadUrl.isNullOrBlank()) {
        scope.launch {
            snackbarHostState.showSnackbar("Selected: ${file.name}")
        }
        return
    }

    if (file.type == FileType.VIDEO && onVideoClick != null) {
        onVideoClick(file.downloadUrl, file.name)
        return
    }

    try {
        val mime = when (file.type) {
            FileType.VIDEO -> "video/*"
            FileType.AUDIO -> "audio/*"
            FileType.IMAGE -> "image/*"
            FileType.PDF -> "application/pdf"
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

@Composable
private fun CreateFolderDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Create New Folder", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "In directory: $currentPath",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        if (it.isNotBlank()) isError = false
                    },
                    label = { Text("Folder Name") },
                    placeholder = { Text("e.g. Documents, Movies") },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Please enter a folder name") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_folder_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isBlank()) {
                        isError = true
                    } else {
                        onConfirm(folderName.trim())
                    }
                },
                modifier = Modifier.testTag("create_folder_confirm_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FileOptionsDialog(
    item: FileItem,
    onDismiss: () -> Unit,
    onDownloadToDevice: () -> Unit,
    onOpenStream: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (item.isFolder) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.formattedSize ?: if (item.isFolder) "Folder" else "File",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                // Download to Device (for files)
                if (!item.isFolder) {
                    OptionRow(
                        icon = Icons.Default.Download,
                        title = "Download to Phone Storage",
                        subtitle = "Save directly to device Downloads",
                        onClick = onDownloadToDevice
                    )
                }

                // Open / Stream
                if (!item.isFolder) {
                    OptionRow(
                        icon = Icons.Default.OpenInNew,
                        title = "Open / Stream",
                        subtitle = "Launch in external player or viewer",
                        onClick = onOpenStream
                    )
                }

                // Copy Link
                OptionRow(
                    icon = Icons.Default.ContentCopy,
                    title = "Copy Server Link",
                    subtitle = "Copy direct HTTP address to clipboard",
                    onClick = onCopyLink
                )

                // Delete from Server
                OptionRow(
                    icon = Icons.Default.Delete,
                    title = "Delete from Server",
                    subtitle = "Permanently remove from DhilipHome",
                    isDestructive = true,
                    onClick = onDelete
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun OptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
