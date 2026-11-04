package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationRecordDao {
    @Query("SELECT * FROM operation_records ORDER BY createdAtMillis DESC")
    fun getAllRecords(): Flow<List<OperationRecordEntity>>

    @Query("SELECT * FROM operation_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): OperationRecordEntity?

    @Query("SELECT * FROM operation_records WHERE batchId = :batchId ORDER BY createdAtMillis DESC")
    suspend fun getRecordsByBatch(batchId: String): List<OperationRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: OperationRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<OperationRecordEntity>)

    @Update
    suspend fun updateRecord(record: OperationRecordEntity)

    @Query("DELETE FROM operation_records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM operation_records")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM operation_records WHERE status = 'SUCCESS'")
    fun getSuccessCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM operation_records")
    fun getTotalCount(): Flow<Int>
}
