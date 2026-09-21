package com.warrantyvault.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface RepairCenterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(centers: List<RepairCenter>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(center: RepairCenter): Long

    @Update
    suspend fun update(center: RepairCenter): Int

    @Query("SELECT * FROM repair_centers WHERE id = :id")
    suspend fun getById(id: Long): RepairCenter?

    @Query("SELECT * FROM repair_centers WHERE brand = :brand AND countryCode = :countryCode ORDER BY rating DESC LIMIT :limit")
    suspend fun getByBrandAndCountry(brand: String, countryCode: String, limit: Int): List<RepairCenter>

    @Query("SELECT * FROM repair_centers WHERE lat BETWEEN :minLat AND :maxLat AND lng BETWEEN :minLng AND :maxLng AND (:brand IS NULL OR brand = :brand) ORDER BY rating DESC LIMIT :limit")
    suspend fun getNearby(
        minLat: Double, maxLat: Double,
        minLng: Double, maxLng: Double,
        brand: String?, limit: Int
    ): List<RepairCenter>

    @Query("SELECT * FROM repair_centers WHERE source = :source")
    suspend fun getBySource(source: String): List<RepairCenter>

    @Query("DELETE FROM repair_centers WHERE source = :source")
    suspend fun deleteBySource(source: String): Int

    @Query("SELECT * FROM repair_centers ORDER BY lastUpdated DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<RepairCenter>

    @Query("SELECT COUNT(*) FROM repair_centers")
    suspend fun count(): Int
}