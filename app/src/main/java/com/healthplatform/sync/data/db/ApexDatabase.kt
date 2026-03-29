package com.healthplatform.sync.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SyncQueueEntity::class,
        FoodCacheEntity::class,
        FoodEntryCacheEntity::class,
        WaterEntryCacheEntity::class,
        NutritionTargetCacheEntity::class,
        HydrationTargetCacheEntity::class,
        PendingNutritionWriteEntity::class,
    ],
    version = 2,
    exportSchema = false
)
abstract class ApexDatabase : RoomDatabase() {

    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun nutritionCacheDao(): NutritionCacheDao

    companion object {
        @Volatile
        private var instance: ApexDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `food_cache` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `brand` TEXT,
                        `barcode` TEXT,
                        `servingSizeG` REAL,
                        `calories` REAL NOT NULL,
                        `proteinG` REAL,
                        `carbsG` REAL,
                        `fatG` REAL,
                        `fiberG` REAL,
                        `sugarG` REAL,
                        `sodiumMg` REAL,
                        `dataSource` TEXT,
                        `qualityFlag` TEXT,
                        `isCustom` INTEGER NOT NULL,
                        `syncState` TEXT NOT NULL,
                        `lastUsedAt` INTEGER NOT NULL,
                        `createdAt` TEXT,
                        `updatedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_cache_barcode` ON `food_cache` (`barcode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_cache_name` ON `food_cache` (`name`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `food_entry_cache` (
                        `id` TEXT NOT NULL,
                        `foodId` TEXT,
                        `foodName` TEXT NOT NULL,
                        `entrySource` TEXT,
                        `mealType` TEXT,
                        `servings` REAL NOT NULL,
                        `calories` REAL NOT NULL,
                        `proteinG` REAL,
                        `carbsG` REAL,
                        `fatG` REAL,
                        `fiberG` REAL,
                        `sugarG` REAL,
                        `sodiumMg` REAL,
                        `loggedAt` TEXT NOT NULL,
                        `loggedDate` TEXT NOT NULL,
                        `notes` TEXT,
                        `syncState` TEXT NOT NULL,
                        `createdAt` TEXT,
                        `updatedAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entry_cache_loggedDate` ON `food_entry_cache` (`loggedDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entry_cache_foodId` ON `food_entry_cache` (`foodId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `water_entry_cache` (
                        `id` TEXT NOT NULL,
                        `amountMl` INTEGER NOT NULL,
                        `entrySource` TEXT,
                        `loggedAt` TEXT NOT NULL,
                        `loggedDate` TEXT NOT NULL,
                        `notes` TEXT,
                        `syncState` TEXT NOT NULL,
                        `createdAt` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_entry_cache_loggedDate` ON `water_entry_cache` (`loggedDate`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `nutrition_target_cache` (
                        `effectiveDate` TEXT NOT NULL,
                        `serverId` TEXT,
                        `calories` INTEGER NOT NULL,
                        `proteinG` INTEGER NOT NULL,
                        `carbsG` INTEGER NOT NULL,
                        `fatG` INTEGER NOT NULL,
                        `method` TEXT NOT NULL,
                        `syncState` TEXT NOT NULL,
                        `createdAt` TEXT,
                        `updatedAt` TEXT,
                        PRIMARY KEY(`effectiveDate`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `hydration_target_cache` (
                        `effectiveDate` TEXT NOT NULL,
                        `serverId` TEXT,
                        `targetMl` INTEGER NOT NULL,
                        `method` TEXT NOT NULL,
                        `syncState` TEXT NOT NULL,
                        `createdAt` TEXT,
                        `updatedAt` TEXT,
                        PRIMARY KEY(`effectiveDate`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `nutrition_write_queue` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `dedupeKey` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_nutrition_write_queue_dedupeKey` ON `nutrition_write_queue` (`dedupeKey`)"
                )
            }
        }

        fun get(context: Context): ApexDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ApexDatabase::class.java,
                    "apex.db"
                )
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
            }

        /**
         * Close and clear the singleton so the next [get] call creates a fresh
         * database. Only intended for test isolation — production code must not
         * call this.
         */
        @androidx.annotation.VisibleForTesting
        fun resetForTesting() {
            synchronized(this) {
                try { instance?.close() } catch (_: Exception) {}
                instance = null
            }
        }
    }
}
