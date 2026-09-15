package com.example.ui.screens.files

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CloudDownloadTask
import com.example.data.model.FileItem
import com.example.data.repository.CloudDownloadManager
import com.example.data.repository.HttpServerRepository
import com.example.domain.repository.ServerRepository
import com.example.network.ServerConfig
import com.example.network.ServerConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FileSortOrder(val label: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    DATE_NEWEST("Date (Newest)"),
    SIZE_LARGEST("Size (Largest)")
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FilesViewModel(
    private val repository: ServerRepository
) : ViewModel() {

    private val _currentPath = MutableStateFlow("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0L)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(FileSortOrder.NAME_ASC)
    val sortOrder: StateFlow<FileSortOrder> = _sortOrder.asStateFlow()

    private val _uploadStatus = MutableStateFlow<String?>(null)
    val uploadStatus: StateFlow<String?> = _uploadStatus.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    val cloudDownloads: StateFlow<List<CloudDownloadTask>> = CloudDownloadManager.downloads
    val downloads: StateFlow<List<CloudDownloadTask>> = CloudDownloadManager.downloads
    val uploads: StateFlow<List<CloudDownloadTask>> = CloudDownloadManager.uploads
    val allTransfers: StateFlow<List<CloudDownloadTask>> = CloudDownloadManager.tasks

    init {
        viewModelScope.launch {
            // Automatically refresh files whenever ServerConfig baseUrl becomes available or connection status changes
            combine(ServerConfig.baseUrl, ServerConnectionManager.isConnected) { url, connected ->
                url to connected
            }.collect { (url, connected) ->
                if (url.isNotBlank() && connected) {
                    refresh()
                }
            }
        }
        refresh()
    }

    fun startCloudDownload(url: String, filename: String, destinationFolder: String) {
        CloudDownloadManager.startDownload(
            url = url,
            destinationFolder = destinationFolder,
            customFilename = filename,
            onComplete = {
                refresh()
            }
        )
    }

    fun cancelCloudDownload(taskId: String) {
        CloudDownloadManager.cancelTask(taskId)
    }

    fun pauseTransfer(taskId: String) {
        CloudDownloadManager.pauseTask(taskId)
    }

    fun resumeTransfer(taskId: String) {
        CloudDownloadManager.resumeTask(taskId) {
            refresh()
        }
    }

    fun retryTransfer(taskId: String) {
        CloudDownloadManager.retryTask(taskId) {
            refresh()
        }
    }

    fun pauseAllTransfers() {
        CloudDownloadManager.pauseAll()
    }

    fun resumeAllTransfers() {
        CloudDownloadManager.resumeAll()
    }

    fun clearCompletedDownloads(isUploadFilter: Boolean? = null) {
        CloudDownloadManager.clearCompleted(isUploadFilter)
    }

    fun createFolder(name: String, onResult: (Boolean, String) -> Unit) {
        val cleanName = name.trim().replace("/", "")
        if (cleanName.isBlank()) {
            onResult(false, "Folder name cannot be empty")
            return
        }
        viewModelScope.launch {
            val parent = _currentPath.value.trimEnd('/')
            val fullPath = if (parent.isEmpty()) "/$cleanName" else "$parent/$cleanName"
            val success = repository.createFolder(fullPath)
            if (success) {
                refresh()
                onResult(true, "Created folder: $cleanName")
            } else {
                onResult(false, "Failed to create folder on server")
            }
        }
    }

    fun deleteItem(item: FileItem, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = repository.deleteFile(item.path)
            if (success) {
                refresh()
                onResult(true, "Deleted ${item.name}")
            } else {
                onResult(false, "Failed to delete ${item.name} from server")
            }
        }
    }

    fun uploadFiles(context: Context, uris: List<Uri>, onResult: (Int, Int) -> Unit) {
        if (uris.isEmpty()) return
        val total = uris.size
        for (uri in uris) {
            CloudDownloadManager.startUpload(
                context = context,
                uri = uri,
                destinationFolder = _currentPath.value,
                onComplete = {
                    refresh()
                }
            )
        }
        onResult(total, total)
    }

    fun downloadFileToDevice(context: Context, item: FileItem): Boolean {
        return HttpServerRepository.downloadFileToDevice(context, item)
    }

    val displayedFiles: StateFlow<List<FileItem>> = combine(
        combine(_currentPath, _refreshTrigger) { path, _ -> path }
            .flatMapLatest { path -> repository.getFiles(path) },
        _searchQuery,
        _sortOrder
    ) { files, query, sort ->
        var filtered = if (query.isBlank()) {
            files
        } else {
            files.filter { it.name.contains(query, ignoreCase = true) }
        }

        when (sort) {
            FileSortOrder.NAME_ASC -> filtered.sortedWith(
                compareBy<FileItem> { !it.isFolder }.thenBy { it.name.lowercase() }
            )
            FileSortOrder.NAME_DESC -> filtered.sortedWith(
                compareBy<FileItem> { !it.isFolder }.thenByDescending { it.name.lowercase() }
            )
            FileSortOrder.DATE_NEWEST -> filtered.sortedWith(
                compareBy<FileItem> { !it.isFolder }.thenByDescending { it.modifiedDate }
            )
            FileSortOrder.SIZE_LARGEST -> filtered.sortedWith(
                compareBy<FileItem> { !it.isFolder }.thenByDescending { it.sizeBytes ?: 0L }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun navigateToFolder(path: String) {
        _searchQuery.value = ""
        _currentPath.value = path
    }

    fun navigateUp(): Boolean {
        val path = _currentPath.value
        if (path == "/") return false
        val parent = path.substringBeforeLast('/', "")
        _currentPath.value = if (parent.isEmpty()) "/" else parent
        _searchQuery.value = ""
        return true
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(sort: FileSortOrder) {
        _sortOrder.value = sort
    }

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }
}
