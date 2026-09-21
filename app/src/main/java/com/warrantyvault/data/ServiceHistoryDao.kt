package com.warrantyvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(serviceHistory: ServiceHistory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(serviceHistories: List<ServiceHistory>)

    @Update
    suspend fun update(serviceHistory: ServiceHistory): Int

    @Query("SELECT * FROM service_history WHERE productId = :productId ORDER BY serviceDate DESC")
    fun getServiceHistoryByProductId(productId: Long): Flow<List<ServiceHistory>>

    @Query("SELECT * FROM service_history WHERE userId = :userId ORDER BY serviceDate DESC")
    fun getAllServiceHistory(userId: Long): Flow<List<ServiceHistory>>

    @Query("SELECT * FROM service_history WHERE id = :id")
    suspend fun getServiceHistoryById(id: Long): ServiceHistory?

    @Query("SELECT * FROM service_history WHERE productId IN (:productIds)")
    suspend fun getByProductIds(productIds: List<Long>): List<ServiceHistory>

    @Query("DELETE FROM service_history WHERE id = :id")
    suspend fun deleteServiceHistory(id: Long): Int
}