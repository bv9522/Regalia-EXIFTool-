package com.example.ui.screens.home

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.OperationRecordEntity
import com.example.data.db.RegaliaDatabase
import com.example.data.model.FileItem
import com.example.data.repository.SampleFilesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeStats(
    val totalInspected: Int = 0,
    val totalRepaired: Int = 0,
    val successCount: Int = 0,
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = RegaliaDatabase.getInstance(application)
    private val sampleRepo = SampleFilesRepository(application)

    val recentRecords: StateFlow<List<OperationRecordEntity>> = database.operationRecordDao()
        .getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val successCount: StateFlow<Int> = database.operationRecordDao()
        .getSuccessCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalOperationsCount: StateFlow<Int> = database.operationRecordDao()
        .getTotalCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _samplesLoaded = MutableStateFlow(false)
    val samplesLoaded = _samplesLoaded.asStateFlow()

    fun loadSampleSuite(onLoaded: (List<FileItem>) -> Unit) {
        viewModelScope.launch {
            val samples = sampleRepo.getOrCreateSampleFiles()
            _samplesLoaded.value = true
            onLoaded(samples)
        }
    }
}
