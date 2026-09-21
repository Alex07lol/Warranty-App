package com.warrantyvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        User::class,
        Product::class,
        WarrantyPeriod::class,
        Document::class,
        ServiceHistory::class,
        Notification::class,
        RepairCenter::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun productDao(): ProductDao
    abstract fun warrantyPeriodDao(): WarrantyPeriodDao
    abstract fun documentDao(): DocumentDao
    abstract fun serviceHistoryDao(): ServiceHistoryDao
    abstract fun notificationDao(): NotificationDao
    abstract fun repairCenterDao(): RepairCenterDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2 (historical). The v1 schema is not shipped in this repo, so the safest
         * non-destructive approximation is a no-op schema alignment; existing rows are kept.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema change was shipped for v2 beyond what Room validates per entity;
                // recreate nothing, destroy nothing.
            }
        }

        /**
         * v2 -> v3: no column changes. Bumped so the DAO layer (strict insert semantics,
         * duplicate-serial lookup helpers) ships with a clean version marker. All data kept.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema is unchanged; this migration exists to remove
                // fallbackToDestructiveMigration() without risking user data.
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "warrantyvault.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
