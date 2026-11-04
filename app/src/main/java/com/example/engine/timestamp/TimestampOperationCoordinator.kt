package com.example.engine.timestamp

import android.content.Context
import com.example.data.db.OperationRecordDao
import com.example.data.db.OperationRecordEntity
import com.example.data.db.RegaliaDatabase
import com.example.data.model.CandidateStatus
import com.example.data.model.DateCandidate
import com.example.data.model.FileItem
import com.example.data.model.MetadataGroup
import com.example.data.model.OperationStatus
import com.example.data.model.TimestampTask
import com.example.engine.metadata.MetadataEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs

data class BatchProgress(
    val isRunning: Boolean = false,
    val totalCount: Int = 0,
    val currentProgress: Int = 0,
    val currentFileName: String = "",
    val successCount: Int = 0,
    val unsupportedCount: Int = 0,
    val failedCount: Int = 0
)

class TimestampOperationCoordinator(
    private val context: Context,
    private val metadataEngine: MetadataEngine = MetadataEngine(),
    private val timestampEngine: TimestampEngine = TimestampEngine(context),
    private val database: RegaliaDatabase = RegaliaDatabase.getInstance(context)
) {

    private val _tasks = MutableStateFlow<List<TimestampTask>>(emptyList())
    val tasks = _tasks.asStateFlow()

    private val _progress = MutableStateFlow(BatchProgress())
    val progress = _progress.asStateFlow()

    private val operationDao: OperationRecordDao get() = database.operationRecordDao()

    fun addFiles(files: List<FileItem>) {
        val current = _tasks.value.toMutableList()
        val existingUris = current.map { it.fileItem.uri.toString() }.toSet()

        val newTasks = files
            .filter { !it.isDirectory && it.uri.toString() !in existingUris }
            .map { fileItem ->
                TimestampTask(
                    id = UUID.randomUUID().toString(),
                    fileItem = fileItem,
                    currentTimestamp = fileItem.lastModified,
                    proposedTimestamp = null,
                    selectedCandidate = null,
                    status = OperationStatus.IDLE
                )
            }

        _tasks.value = current + newTasks
    }

    fun removeTasks(taskIds: Set<String>) {
        _tasks.value = _tasks.value.filter { it.id !in taskIds }
    }

    fun clearAll() {
        _tasks.value = emptyList()
    }

    /**
     * Scans metadata for all pending/idle files and prepares timestamp preview
     */
    suspend fun scanAllMetadata() = withContext(Dispatchers.IO) {
        val currentTasks = _tasks.value
        val updated = currentTasks.map { task ->
            val metadata = metadataEngine.extractMetadata(context, task.fileItem)
            val recommended = metadata.recommendedCandidate

            val status = when {
                recommended == null || recommended.parsedEpochMillis == null || recommended.parsedEpochMillis <= 0 -> {
                    OperationStatus.NO_USABLE_DATE
                }
                abs(recommended.parsedEpochMillis - task.currentTimestamp) <= 2000L -> {
                    OperationStatus.UP_TO_DATE
                }
                else -> {
                    OperationStatus.PREVIEW_READY
                }
            }

            task.copy(
                proposedTimestamp = recommended?.parsedEpochMillis,
                selectedCandidate = recommended,
                allCandidates = metadata.dateCandidates,
                status = status,
                diagnosticMessage = when (status) {
                    OperationStatus.UP_TO_DATE -> "Current filesystem timestamp already matches ${recommended?.tagName} (within ±2s)."
                    OperationStatus.NO_USABLE_DATE -> "No valid date metadata found or parsable for this file."
                    OperationStatus.PREVIEW_READY -> "Ready to apply timestamp correction from ${recommended?.tagName}."
                    else -> null
                }
            )
        }

        _tasks.value = updated
    }

    /**
     * Manually overrides chosen candidate for a task
     */
    fun selectCandidateForTask(taskId: String, candidate: DateCandidate) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                val proposed = candidate.parsedEpochMillis
                val status = if (proposed == null || proposed <= 0) {
                    OperationStatus.NO_USABLE_DATE
                } else if (abs(proposed - task.currentTimestamp) <= 2000L) {
                    OperationStatus.UP_TO_DATE
                } else {
                    OperationStatus.PREVIEW_READY
                }

                task.copy(
                    proposedTimestamp = proposed,
                    selectedCandidate = candidate.copy(status = CandidateStatus.RECOMMENDED),
                    status = status,
                    diagnosticMessage = "Candidate manually selected: ${candidate.tagName}"
                )
            } else {
                task
            }
        }
    }

    /**
     * Executes timestamp updates for all PREVIEW_READY tasks
     */
    suspend fun applyChanges(onlyReady: Boolean = true): BatchProgress = withContext(Dispatchers.IO) {
        val batchId = UUID.randomUUID().toString()
        val targets = if (onlyReady) {
            _tasks.value.filter { it.status == OperationStatus.PREVIEW_READY }
        } else {
            _tasks.value.filter { it.proposedTimestamp != null && it.proposedTimestamp > 0 }
        }

        if (targets.isEmpty()) return@withContext BatchProgress()

        _progress.value = BatchProgress(
            isRunning = true,
            totalCount = targets.size,
            currentProgress = 0
        )

        var successCount = 0
        var unsupportedCount = 0
        var failedCount = 0

        val updatedMap = _tasks.value.associateBy { it.id }.toMutableMap()
        val historyRecords = mutableListOf<OperationRecordEntity>()

        for ((index, task) in targets.withIndex()) {
            _progress.value = _progress.value.copy(
                currentProgress = index + 1,
                currentFileName = task.fileItem.name
            )

            // Mark processing
            updatedMap[task.id] = task.copy(status = OperationStatus.PROCESSING)
            _tasks.value = updatedMap.values.toList()

            val result = timestampEngine.applyTimestamp(task)

            when (result.status) {
                OperationStatus.SUCCESS -> successCount++
                OperationStatus.UNSUPPORTED_PROVIDER -> unsupportedCount++
                else -> failedCount++
            }

            val finishedTask = task.copy(
                status = result.status,
                diagnosticMessage = result.diagnosticMessage,
                verifiedTimestamp = result.verifiedTimestamp
            )
            updatedMap[task.id] = finishedTask
            _tasks.value = updatedMap.values.toList()

            // Save history record
            historyRecords.add(
                OperationRecordEntity(
                    batchId = batchId,
                    fileName = task.fileItem.name,
                    fileUriString = task.fileItem.uri.toString(),
                    originalTimestamp = result.originalTimestamp,
                    proposedTimestamp = result.proposedTimestamp,
                    appliedTimestamp = result.proposedTimestamp,
                    verifiedTimestamp = result.verifiedTimestamp,
                    metadataSource = task.selectedCandidate?.tagName ?: "Unknown",
                    status = result.status.name,
                    diagnosticMessage = result.diagnosticMessage
                )
            )
        }

        if (historyRecords.isNotEmpty()) {
            operationDao.insertAll(historyRecords)
        }

        val finalProgress = BatchProgress(
            isRunning = false,
            totalCount = targets.size,
            currentProgress = targets.size,
            successCount = successCount,
            unsupportedCount = unsupportedCount,
            failedCount = failedCount
        )
        _progress.value = finalProgress
        return@withContext finalProgress
    }

    /**
     * Rollback a previously applied operation from history
     */
    suspend fun rollbackOperation(record: OperationRecordEntity): Boolean = withContext(Dispatchers.IO) {
        val fileItem = FileItem(
            uri = android.net.Uri.parse(record.fileUriString),
            name = record.fileName,
            mimeType = "application/octet-stream",
            size = 0L,
            lastModified = record.appliedTimestamp ?: record.proposedTimestamp
        )

        val result = timestampEngine.rollback(fileItem, record.originalTimestamp)
        if (result.status == OperationStatus.ROLLED_BACK || result.status == OperationStatus.SUCCESS) {
            val updated = record.copy(
                status = OperationStatus.ROLLED_BACK.name,
                diagnosticMessage = "Rolled back: ${result.diagnosticMessage}"
            )
            operationDao.updateRecord(updated)
            return@withContext true
        }
        return@withContext false
    }
}
