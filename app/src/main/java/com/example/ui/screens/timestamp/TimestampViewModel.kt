package com.example.ui.screens.timestamp

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CandidateStatus
import com.example.data.model.DateCandidate
import com.example.data.model.FileItem
import com.example.data.model.MetadataGroup
import com.example.data.model.OperationStatus
import com.example.data.model.TimestampTask
import com.example.data.repository.SampleFilesRepository
import com.example.engine.timestamp.BatchProgress
import com.example.engine.timestamp.StorageAccessProvider
import com.example.engine.timestamp.TimestampOperationCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TimestampFilter(val label: String) {
    ALL("All Files"),
    READY_TO_APPLY("Ready to Apply"),
    SAMSUNG_FALLBACK("Samsung Fallback"),
    NO_DATE("No Usable Date"),
    UNSUPPORTED("Unsupported Provider"),
    ERRORS("Errors & Mismatches")
}

class TimestampViewModel(application: Application) : AndroidViewModel(application) {

    private val coordinator = TimestampOperationCoordinator(application)
    private val storageProvider = StorageAccessProvider(application)
    private val sampleRepo = SampleFilesRepository(application)

    val tasks: StateFlow<List<TimestampTask>> = coordinator.tasks
    val progress: StateFlow<BatchProgress> = coordinator.progress

    private val _selectedFilter = MutableStateFlow(TimestampFilter.ALL)
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _selectedTaskIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTaskIds = _selectedTaskIds.asStateFlow()

    private val _inspectingTask = MutableStateFlow<TimestampTask?>(null)
    val inspectingTask = _inspectingTask.asStateFlow()

    private val _showConfirmDialog = MutableStateFlow(false)
    val showConfirmDialog = _showConfirmDialog.asStateFlow()

    private val _showPreviewSummary = MutableStateFlow(false)
    val showPreviewSummary = _showPreviewSummary.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner = _statusBanner.asStateFlow()

    val filteredTasks: StateFlow<List<TimestampTask>> = combine(
        coordinator.tasks,
        _selectedFilter
    ) { allTasks, filter ->
        when (filter) {
            TimestampFilter.ALL -> allTasks
            TimestampFilter.READY_TO_APPLY -> allTasks.filter { it.status == OperationStatus.PREVIEW_READY }
            TimestampFilter.SAMSUNG_FALLBACK -> allTasks.filter {
                it.selectedCandidate?.metadataGroup == MetadataGroup.FILENAME
            }
            TimestampFilter.NO_DATE -> allTasks.filter { it.status == OperationStatus.NO_USABLE_DATE }
            TimestampFilter.UNSUPPORTED -> allTasks.filter { it.status == OperationStatus.UNSUPPORTED_PROVIDER }
            TimestampFilter.ERRORS -> allTasks.filter { it.status == OperationStatus.FAILED }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addFiles(files: List<FileItem>) {
        if (files.isEmpty()) return
        coordinator.addFiles(files)
        scanMetadata()
    }

    fun addUrisDirectly(uris: List<Uri>) {
        viewModelScope.launch {
            _isScanning.value = true
            val resolved = uris.mapNotNull { uri ->
                storageProvider.takePersistablePermission(uri)
                storageProvider.resolveFileItem(uri)
            }
            addFiles(resolved)
        }
    }

    fun addFolderTree(treeUri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            storageProvider.takePersistablePermission(treeUri)
            val files = storageProvider.listChildren(treeUri)
            addFiles(files)
        }
    }

    fun loadSampleFiles() {
        viewModelScope.launch {
            val samples = sampleRepo.getOrCreateSampleFiles()
            addFiles(samples)
            _statusBanner.value = "Loaded ${samples.size} demonstration sample files."
        }
    }

    fun toggleTaskSelection(id: String) {
        val current = _selectedTaskIds.value.toMutableSet()
        if (id in current) current.remove(id) else current.add(id)
        _selectedTaskIds.value = current
    }

    fun removeSelected() {
        val selected = _selectedTaskIds.value
        coordinator.removeTasks(selected)
        _selectedTaskIds.value = emptySet()
    }

    fun clearAll() {
        coordinator.clearAll()
        _selectedTaskIds.value = emptySet()
        _statusBanner.value = null
    }

    fun scanMetadata() {
        viewModelScope.launch {
            _isScanning.value = true
            coordinator.scanAllMetadata()
            _isScanning.value = false
            val current = coordinator.tasks.value
            val ready = current.count { it.status == OperationStatus.PREVIEW_READY }
            val match = current.count { it.status == OperationStatus.UP_TO_DATE }
            _statusBanner.value = "Preview generated: $ready ready to correct, $match already matching."
        }
    }

    fun openPreviewSummary() {
        viewModelScope.launch {
            if (_tasksHaveUnscanned()) {
                scanMetadata()
            }
            _showPreviewSummary.value = true
        }
    }

    fun closePreviewSummary() {
        _showPreviewSummary.value = false
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    private fun _tasksHaveUnscanned(): Boolean {
        return coordinator.tasks.value.any { it.status == OperationStatus.IDLE || it.proposedTimestamp == null }
    }

    fun setFilter(filter: TimestampFilter) {
        _selectedFilter.value = filter
    }

    fun setInspectingTask(task: TimestampTask?) {
        _inspectingTask.value = task
    }

    fun selectCandidateForTask(taskId: String, candidate: DateCandidate) {
        coordinator.selectCandidateForTask(taskId, candidate)
    }

    fun showConfirmation(show: Boolean) {
        _showConfirmDialog.value = show
    }

    fun applyChanges() {
        _showConfirmDialog.value = false
        _showPreviewSummary.value = false
        viewModelScope.launch {
            coordinator.applyChanges(onlyReady = false)
        }
    }
}
