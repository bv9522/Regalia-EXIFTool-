package com.example.engine.timestamp

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.example.data.model.DateCandidate
import com.example.data.model.FileItem
import com.example.data.model.OperationStatus
import com.example.data.model.TimestampTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

data class TimestampResult(
    val status: OperationStatus,
    val originalTimestamp: Long,
    val proposedTimestamp: Long,
    val verifiedTimestamp: Long?,
    val diagnosticMessage: String
)

class TimestampEngine(
    private val context: Context,
    private val storageAccessProvider: StorageAccessProvider = StorageAccessProvider(context)
) {

    /**
     * Executes timestamp correction with strict read-back verification
     */
    suspend fun applyTimestamp(
        task: TimestampTask
    ): TimestampResult = withContext(Dispatchers.IO) {
        val uri = task.fileItem.uri
        val proposed = task.proposedTimestamp
        val original = storageAccessProvider.readCurrentTimestamp(uri, task.fileItem.directFilePath)

        if (proposed == null || proposed <= 0) {
            return@withContext TimestampResult(
                status = OperationStatus.NO_USABLE_DATE,
                originalTimestamp = original,
                proposedTimestamp = 0L,
                verifiedTimestamp = original,
                diagnosticMessage = "No valid proposed date candidate available."
            )
        }

        // Validate timestamp safety constraints
        val now = System.currentTimeMillis()
        if (proposed > now + (24 * 3600 * 1000L)) {
            return@withContext TimestampResult(
                status = OperationStatus.FAILED,
                originalTimestamp = original,
                proposedTimestamp = proposed,
                verifiedTimestamp = original,
                diagnosticMessage = "Safety check failed: Proposed date (${DateCandidate.formatEpoch(proposed)}) is in the future."
            )
        }

        var writeAttempted = false
        val errors = mutableListOf<String>()

        // 1. Direct file write (Highest fidelity where available)
        val directPath = task.fileItem.directFilePath ?: if (uri.scheme == "file") uri.path else null
        if (directPath != null) {
            val file = File(directPath)
            if (file.exists()) {
                val success = file.setLastModified(proposed)
                writeAttempted = true
                if (!success) {
                    errors.add("File.setLastModified returned false on path: $directPath")
                }
            }
        }

        // 2. SAF FileDescriptor with Os.futimes (via reflection for runtime compatibility)
        if (!writeAttempted) {
            try {
                context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    try {
                        val osClass = Class.forName("android.system.Os")
                        val structTimevalClass = Class.forName("android.system.StructTimeval")
                        val fromMillis = structTimevalClass.getMethod("fromMillis", Long::class.javaPrimitiveType)
                        val timeval = fromMillis.invoke(null, proposed)
                        val array = java.lang.reflect.Array.newInstance(structTimevalClass, 2)
                        java.lang.reflect.Array.set(array, 0, timeval)
                        java.lang.reflect.Array.set(array, 1, timeval)
                        val futimesMethod = osClass.getMethod("futimes", java.io.FileDescriptor::class.java, array.javaClass)
                        futimesMethod.invoke(null, pfd.fileDescriptor, array)
                        writeAttempted = true
                    } catch (osEx: Throwable) {
                        errors.add("futimes invocation: ${osEx.message}")
                    }
                }
            } catch (pfdEx: Throwable) {
                errors.add("openFileDescriptor(rw): ${pfdEx.message}")
            }
        }

        // 3. SAF DocumentsContract ContentResolver.update attempt
        try {
            val values = ContentValues().apply {
                put(DocumentsContract.Document.COLUMN_LAST_MODIFIED, proposed)
            }
            val rows = context.contentResolver.update(uri, values, null, null)
            if (rows > 0) {
                writeAttempted = true
            }
        } catch (updateEx: Throwable) {
            errors.add("ContentResolver.update error: ${updateEx.message}")
        }

        // 4. Strict Read-Back Verification (MANDATORY)
        val verified = storageAccessProvider.readCurrentTimestamp(uri, task.fileItem.directFilePath)
        val delta = abs(verified - proposed)

        // Tolerance: FAT32 has 2-second timestamp resolution; ext4/f2fs has sub-second
        return@withContext if (delta <= 2000L) {
            TimestampResult(
                status = OperationStatus.SUCCESS,
                originalTimestamp = original,
                proposedTimestamp = proposed,
                verifiedTimestamp = verified,
                diagnosticMessage = "Filesystem modified date successfully updated and verified (Readback: ${DateCandidate.formatEpoch(verified)})."
            )
        } else if (abs(verified - original) <= 2000L && delta > 2000L) {
            // Timestamp did not change at all
            val provider = uri.authority ?: "SAF Storage Provider"
            TimestampResult(
                status = OperationStatus.UNSUPPORTED_PROVIDER,
                originalTimestamp = original,
                proposedTimestamp = proposed,
                verifiedTimestamp = verified,
                diagnosticMessage = "Storage provider ($provider) did not persist modification timestamps. Android Storage Access Framework does not require document providers to support setting COLUMN_LAST_MODIFIED."
            )
        } else {
            // Write resulted in partial or mismatched time
            TimestampResult(
                status = OperationStatus.FAILED,
                originalTimestamp = original,
                proposedTimestamp = proposed,
                verifiedTimestamp = verified,
                diagnosticMessage = "Timestamp write mismatch. Expected: ${DateCandidate.formatEpoch(proposed)}, but read back: ${DateCandidate.formatEpoch(verified)}. Notes: ${errors.joinToString("; ")}"
            )
        }
    }

    /**
     * Attempts rollback to restore previous original timestamp
     */
    suspend fun rollback(
        fileItem: FileItem,
        originalTimestamp: Long
    ): TimestampResult = withContext(Dispatchers.IO) {
        val rollbackTask = TimestampTask(
            id = "rollback_${System.currentTimeMillis()}",
            fileItem = fileItem,
            currentTimestamp = storageAccessProvider.readCurrentTimestamp(fileItem.uri, fileItem.directFilePath),
            proposedTimestamp = originalTimestamp,
            selectedCandidate = null
        )

        val result = applyTimestamp(rollbackTask)
        if (result.status == OperationStatus.SUCCESS) {
            result.copy(
                status = OperationStatus.ROLLED_BACK,
                diagnosticMessage = "Successfully rolled back to original timestamp: ${DateCandidate.formatEpoch(originalTimestamp)}"
            )
        } else {
            result
        }
    }
}
