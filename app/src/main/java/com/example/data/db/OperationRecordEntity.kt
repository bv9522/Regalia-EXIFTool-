package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "operation_records")
data class OperationRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val batchId: String,
    val fileName: String,
    val fileUriString: String,
    val originalTimestamp: Long,
    val proposedTimestamp: Long,
    val appliedTimestamp: Long?,
    val verifiedTimestamp: Long?,
    val metadataSource: String,
    val status: String, // SUCCESS, UNSUPPORTED_PROVIDER, FAILED, ROLLED_BACK
    val diagnosticMessage: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
