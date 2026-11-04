package com.example.ui.screens.browse

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FileItem
import com.example.data.repository.SampleFilesRepository
import com.example.engine.timestamp.StorageAccessProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TypeFilter(val label: String) {
    ALL("All Files"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    DOCUMENTS("Docs"),
    AUDIO("Audio")
}

enum class SortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    DATE_DESC("Date (Newest)"),
    DATE_ASC("Date (Oldest)"),
    SIZE_DESC("Size (Largest)"),
    SIZE_ASC("Size (Smallest)"),
    TYPE("File Type")
}

data class BrowseBreadcrumb(
    val title: String,
    val docId: String? = null
)

class BrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val storageProvider = StorageAccessProvider(application)
    private val sampleRepo = SampleFilesRepository(application)

    private val _rawFiles = MutableStateFlow<List<FileItem>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _typeFilter = MutableStateFlow(TypeFilter.ALL)
    val typeFilter = _typeFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.NAME_ASC)
    val sortOption = _sortOption.asStateFlow()

    private val _selectedUris = MutableStateFlow<Set<String>>(emptySet())
    val selectedUris = _selectedUris.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _currentTreeUri = MutableStateFlow<Uri?>(null)
    val currentTreeUri = _currentTreeUri.asStateFlow()

    private val _breadcrumbs = MutableStateFlow<List<BrowseBreadcrumb>>(listOf(BrowseBreadcrumb("Root")))
    val breadcrumbs = _breadcrumbs.asStateFlow()

    private val _detailsFile = MutableStateFlow<FileItem?>(null)
    val detailsFile = _detailsFile.asStateFlow()

    val filteredFiles: StateFlow<List<FileItem>> = combine(
        _rawFiles,
        _searchQuery,
        _typeFilter,
        _sortOption
    ) { files, query, filter, sort ->
        var list = files

        // Filter by query
        if (query.isNotBlank()) {
            list = list.filter { it.name.contains(query, ignoreCase = true) }
        }

        // Filter by media type
        list = when (filter) {
            TypeFilter.ALL -> list
            TypeFilter.PHOTOS -> list.filter { it.isDirectory || it.mimeType.startsWith("image/") }
            TypeFilter.VIDEOS -> list.filter { it.isDirectory || it.mimeType.startsWith("video/") }
            TypeFilter.DOCUMENTS -> list.filter {
                it.isDirectory || it.extension in listOf("docx", "xlsx", "pptx", "pdf", "txt", "odt")
            }
            TypeFilter.AUDIO -> list.filter { it.isDirectory || it.mimeType.startsWith("audio/") }
        }

        // Sort
        list = when (sort) {
            SortOption.NAME_ASC -> list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            SortOption.NAME_DESC -> list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).reversed()
            SortOption.DATE_DESC -> list.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified })).reversed()
            SortOption.DATE_ASC -> list.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified }))
            SortOption.SIZE_DESC -> list.sortedWith(compareBy({ !it.isDirectory }, { it.size })).reversed()
            SortOption.SIZE_ASC -> list.sortedWith(compareBy({ !it.isDirectory }, { it.size }))
            SortOption.TYPE -> list.sortedWith(compareBy({ !it.isDirectory }, { it.extension }, { it.name.lowercase() }))
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openTree(treeUri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            storageProvider.takePersistablePermission(treeUri)
            _currentTreeUri.value = treeUri
            _breadcrumbs.value = listOf(BrowseBreadcrumb("Folder Root"))
            _selectedUris.value = emptySet()

            val items = storageProvider.listChildren(treeUri)
            _rawFiles.value = items
            _isLoading.value = false
        }
    }

    fun pickDirectFiles(uris: List<Uri>) {
        viewModelScope.launch {
            _isLoading.value = true
            val resolved = uris.mapNotNull { uri ->
                storageProvider.takePersistablePermission(uri)
                storageProvider.resolveFileItem(uri)
            }
            _currentTreeUri.value = null
            _breadcrumbs.value = listOf(BrowseBreadcrumb("Selected Files (${resolved.size})"))
            _rawFiles.value = resolved
            _selectedUris.value = emptySet()
            _isLoading.value = false
        }
    }

    fun loadSampleFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            val samples = sampleRepo.getOrCreateSampleFiles()
            _currentTreeUri.value = null
            _breadcrumbs.value = listOf(BrowseBreadcrumb("Sample Suite"))
            _rawFiles.value = samples
            _selectedUris.value = emptySet()
            _isLoading.value = false
        }
    }

    fun openSubFolder(folderItem: FileItem) {
        val treeUri = _currentTreeUri.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val items = storageProvider.listChildren(treeUri, folderItem.pathOrTree)
            val crumbs = _breadcrumbs.value.toMutableList()
            crumbs.add(BrowseBreadcrumb(folderItem.name, folderItem.pathOrTree))
            _breadcrumbs.value = crumbs
            _rawFiles.value = items
            _selectedUris.value = emptySet()
            _isLoading.value = false
        }
    }

    fun navigateToBreadcrumb(index: Int) {
        val treeUri = _currentTreeUri.value ?: return
        val crumbs = _breadcrumbs.value
        if (index !in crumbs.indices) return

        val targetCrumb = crumbs[index]
        viewModelScope.launch {
            _isLoading.value = true
            val items = storageProvider.listChildren(treeUri, targetCrumb.docId)
            _breadcrumbs.value = crumbs.take(index + 1)
            _rawFiles.value = items
            _selectedUris.value = emptySet()
            _isLoading.value = false
        }
    }

    fun toggleItemSelection(uri: Uri) {
        val str = uri.toString()
        val current = _selectedUris.value.toMutableSet()
        if (str in current) current.remove(str) else current.add(str)
        _selectedUris.value = current
    }

    fun toggleSelectAll() {
        val current = _selectedUris.value
        val allFileUris = _rawFiles.value.filter { !it.isDirectory }.map { it.uri.toString() }.toSet()
        if (current.size >= allFileUris.size && allFileUris.isNotEmpty()) {
            _selectedUris.value = emptySet()
        } else {
            _selectedUris.value = allFileUris
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTypeFilter(filter: TypeFilter) {
        _typeFilter.value = filter
    }

    fun setSortOption(sort: SortOption) {
        _sortOption.value = sort
    }

    fun showFileDetails(fileItem: FileItem?) {
        _detailsFile.value = fileItem
    }

    fun getSelectedFiles(): List<FileItem> {
        val selected = _selectedUris.value
        return _rawFiles.value.filter { it.uri.toString() in selected }
    }
}
