package com.warrantyvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 2,
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "warrantyvault.db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}