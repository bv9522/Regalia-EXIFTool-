package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OperationRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RegaliaDatabase : RoomDatabase() {
    abstract fun operationRecordDao(): OperationRecordDao

    companion object {
        @Volatile
        private var INSTANCE: RegaliaDatabase? = null

        fun getInstance(context: Context): RegaliaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RegaliaDatabase::class.java,
                    "regalia_metadata.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
