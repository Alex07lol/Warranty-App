package com.warrantyvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WarrantyPeriodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(warrantyPeriod: WarrantyPeriod): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(warrantyPeriods: List<WarrantyPeriod>)

    @Update
    suspend fun update(warrantyPeriod: WarrantyPeriod): Int

    @Query("SELECT * FROM warranty_periods WHERE productId = :productId ORDER BY startDate ASC")
    fun getWarrantyPeriodsByProductId(productId: Long): Flow<List<WarrantyPeriod>>

    @Query("SELECT * FROM warranty_periods WHERE productId = :productId")
    suspend fun getWarrantyPeriodsByProductIdSuspend(productId: Long): List<WarrantyPeriod>

    @Query("SELECT * FROM warranty_periods WHERE productId IN (:productIds)")
    suspend fun getByProductIds(productIds: List<Long>): List<WarrantyPeriod>

    @Query("DELETE FROM warranty_periods WHERE productId = :productId")
    suspend fun deleteByProductId(productId: Long): Int

    @Query("DELETE FROM warranty_periods WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}