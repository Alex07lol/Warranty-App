package com.warrantyvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: Notification): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<Notification>)

    @Update
    suspend fun update(notification: Notification): Int

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllNotifications(userId: Long): Flow<List<Notification>>

    @Query("SELECT * FROM notifications WHERE userId = :userId AND isRead = 0 ORDER BY createdAt DESC")
    fun getUnreadNotifications(userId: Long): Flow<List<Notification>>

    @Query("SELECT * FROM notifications WHERE userId = :userId AND productId = :productId AND createdAt >= :sinceTime ORDER BY createdAt DESC LIMIT 1")
    suspend fun getRecentNotificationForProduct(userId: Long, productId: Long, sinceTime: Long): Notification?

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    suspend fun getUnreadCount(userId: Long): Int

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllAsRead(userId: Long): Int

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long): Int

    @Query("SELECT * FROM notifications WHERE userId = :userId AND notificationType = :type AND scheduledAt <= :now AND isSent = 0")
    suspend fun getPendingNotifications(userId: Long, type: String, now: Long): List<Notification>
}