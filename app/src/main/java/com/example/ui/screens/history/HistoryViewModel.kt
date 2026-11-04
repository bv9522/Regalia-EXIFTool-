package com.example.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.OperationRecordEntity
import com.example.data.db.RegaliaDatabase
import com.example.data.model.DateCandidate
import com.example.engine.timestamp.TimestampOperationCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryFilter(val label: String) {
    ALL("All Records"),
    SUCCESS("Success"),
    UNSUPPORTED("Unsupported"),
    FAILED("Failed"),
    ROLLED_BACK("Rolled Back")
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = RegaliaDatabase.getInstance(application)
    private val coordinator = TimestampOperationCoordinator(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow(HistoryFilter.ALL)
    val statusFilter = _statusFilter.asStateFlow()

    private val _rollbackMessage = MutableStateFlow<String?>(null)
    val rollbackMessage = _rollbackMessage.asStateFlow()

    val records: StateFlow<List<OperationRecordEntity>> = combine(
        database.operationRecordDao().getAllRecords(),
        _searchQuery,
        _statusFilter
    ) { list, query, filter ->
        var res = list
        if (query.isNotBlank()) {
            res = res.filter {
                it.fileName.contains(query, ignoreCase = true) ||
                it.metadataSource.contains(query, ignoreCase = true)
            }
        }
        res = when (filter) {
            HistoryFilter.ALL -> res
            HistoryFilter.SUCCESS -> res.filter { it.status == "SUCCESS" }
            HistoryFilter.UNSUPPORTED -> res.filter { it.status == "UNSUPPORTED_PROVIDER" }
            HistoryFilter.FAILED -> res.filter { it.status == "FAILED" }
            HistoryFilter.ROLLED_BACK -> res.filter { it.status == "ROLLED_BACK" }
        }
        res
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun rollback(record: OperationRecordEntity) {
        viewModelScope.launch {
            val success = coordinator.rollbackOperation(record)
            _rollbackMessage.value = if (success) {
                "Successfully rolled back timestamp for ${record.fileName}"
            } else {
                "Rollback attempt completed. Check record notes."
            }
        }
    }

    fun clearRollbackMessage() {
        _rollbackMessage.value = null
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            database.operationRecordDao().deleteRecord(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            database.operationRecordDao().clearAll()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: HistoryFilter) {
        _statusFilter.value = filter
    }

    fun buildDiagnosticReport(list: List<OperationRecordEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("=== REGALIA METADATA OPERATION REPORT ===")
        sb.appendLine("Generated at: ${DateCandidate.formatEpoch(System.currentTimeMillis())}")
        sb.appendLine("Total Records: ${list.size}")
        sb.appendLine("=========================================\n")

        for (record in list) {
            sb.appendLine("File: ${record.fileName}")
            sb.appendLine("Status: ${record.status}")
            sb.appendLine("Source: ${record.metadataSource}")
            sb.appendLine("Original Date: ${DateCandidate.formatEpoch(record.originalTimestamp)}")
            sb.appendLine("Proposed Date: ${DateCandidate.formatEpoch(record.proposedTimestamp)}")
            sb.appendLine("Verified Date: ${DateCandidate.formatEpoch(record.verifiedTimestamp)}")
            sb.appendLine("Diagnostics: ${record.diagnosticMessage}")
            sb.appendLine("-----------------------------------------")
        }
        return sb.toString()
    }
}
