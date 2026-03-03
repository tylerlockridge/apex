package com.healthplatform.sync.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SyncQueueEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ApexDatabase : RoomDatabase() {

    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile
        private var instance: ApexDatabase? = null

        fun get(context: Context): ApexDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ApexDatabase::class.java,
                    "apex.db"
                )
                // Destructive migration is acceptable: the sync queue is ephemeral retry
                // data. Any records in the queue at upgrade time will be re-fetched from
                // Health Connect on the next sync run.
                // IMPORTANT: add explicit Migration objects before incrementing the version
                // if the schema ever needs preserving.
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
            }
    }
}
