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
    version = 4,
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
         * v1 -> v2 (historical).
         *
         * HONEST LIMITATION: this repository was initialized with the v2 schema (its first
         * commit already contains the v2 entities), so no verified v1 schema exists to write
         * a real migration against. This no-op is therefore only correct for installs whose
         * v1 tables already matched the v2 entity definitions. It preserves all data either
         * way; if a v1 install has a genuinely different schema Room will fail validation at
         * open time rather than destroy data.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No verified v1 schema shipped in this repo; keep as data-preserving no-op.
            }
        }

        /** v2 -> v3: no schema change (DAO-layer version marker only). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) { /* schema unchanged */ }
        }

        /**
         * v3 -> v4: adds `products.imei` so IMEIs get a dedicated domain field instead of
         * being conflated with serialNumber. ALTER TABLE ADD COLUMN is safe and preserves
         * every existing row; existing rows get NULL imei.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN imei TEXT DEFAULT NULL")
            }
        }

        /**
         * All migrations in order, exposed for migration tests. The production builder
         * below must stay in sync with this list.
         */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "warrantyvault.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
