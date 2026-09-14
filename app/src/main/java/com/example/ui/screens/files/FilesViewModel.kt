package com.example.ui.screens.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FileItem
import com.example.domain.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class FileSortOrder(val label: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    DATE_NEWEST("Date (Newest)"),
    SIZE_LARGEST("Size (Largest)")
}

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
