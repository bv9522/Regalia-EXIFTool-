package com.example.ui.screens.metadata

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FileItem
import com.example.data.model.NormalizedMetadata
import com.example.engine.metadata.MetadataEngine
import com.example.engine.timestamp.StorageAccessProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MetadataViewModel(application: Application) : AndroidViewModel(application) {

    private val metadataEngine = MetadataEngine()
    private val storageProvider = StorageAccessProvider(application)

    private val _activeFile = MutableStateFlow<FileItem?>(null)
    val activeFile = _activeFile.asStateFlow()

    private val _metadata = MutableStateFlow<NormalizedMetadata?>(null)
    val metadata = _metadata.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun inspectFile(fileItem: FileItem) {
        _activeFile.value = fileItem
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val data = metadataEngine.extractMetadata(getApplication(), fileItem)
                _metadata.value = data
            } catch (e: Exception) {
                _error.value = "Error inspecting metadata: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun pickFileUri(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                storageProvider.takePersistablePermission(uri)
                val item = storageProvider.resolveFileItem(uri)
                if (item != null) {
                    _activeFile.value = item
                    val data = metadataEngine.extractMetadata(getApplication(), item)
                    _metadata.value = data
                } else {
                    _error.value = "Unable to resolve selected file."
                }
            } catch (e: Exception) {
                _error.value = "Error reading file: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
