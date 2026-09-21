package com.warrantyvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    /** Insert only. Auto-generates a fresh id; never overwrites an existing row. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: Product): Long

    /** Real update: touches only the row with this id, preserving id/createdAt by construction. */
    @Update
    suspend fun updateProduct(product: Product): Int

    @Query("SELECT * FROM products WHERE id = :id AND isDeleted = 0")
    fun getProductById(id: Long): Flow<Product?>

    @Query("SELECT * FROM products WHERE id = :id AND isDeleted = 0")
    suspend fun getProductByIdImmediate(id: Long): Product?

    @Query("SELECT * FROM products WHERE userId = :userId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllProducts(userId: Long): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE userId = :userId AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getAllForUser(userId: Long): List<Product>

    @Query("SELECT * FROM products WHERE userId = :userId AND isDeleted = 0 AND warrantyExpiryDate IS NOT NULL AND warrantyExpiryDate >= :startDate AND warrantyExpiryDate <= :endDate ORDER BY warrantyExpiryDate ASC")
    fun getExpiringProducts(userId: Long, startDate: Long, endDate: Long): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE userId = :userId AND isDeleted = 0 AND (productName LIKE :query OR brand LIKE :query OR model LIKE :query OR serialNumber LIKE :query OR imei LIKE :query OR category LIKE :query OR purchaseStore LIKE :query OR warrantyProvider LIKE :query) ORDER BY createdAt DESC LIMIT 100")
    fun searchProducts(userId: Long, query: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE userId = :userId AND serialNumber = :serialNumber AND isDeleted = 0 LIMIT 1")
    suspend fun getProductBySerialNumber(userId: Long, serialNumber: String): Product?

    @Query("SELECT * FROM products WHERE userId = :userId AND imei = :imei AND isDeleted = 0 LIMIT 1")
    suspend fun getProductByImei(userId: Long, imei: String): Product?

    /** Case/trim-insensitive serial lookup for the unique-index guard (SQLite LIKE is case-insensitive for ASCII). */
    @Query("SELECT * FROM products WHERE userId = :userId AND isDeleted = 0 AND TRIM(UPPER(serialNumber)) = TRIM(UPPER(:normalizedSerial))")
    suspend fun findByNormalizedSerial(userId: Long, normalizedSerial: String): Product?

    @Query("SELECT * FROM products WHERE userId = :userId AND isDeleted = 0 AND TRIM(UPPER(imei)) = TRIM(UPPER(:normalizedImei))")
    suspend fun findByNormalizedImei(userId: Long, normalizedImei: String): Product?

    /**
     * Targeted brand+model candidates: avoids loading the whole product table for matching.
     * Name containment is finalized in code.
     */
    @Query("SELECT * FROM products WHERE userId = :userId AND isDeleted = 0 AND brand = :brand COLLATE NOCASE AND (model LIKE ('%' || :modelToken || '%') COLLATE NOCASE OR productName LIKE ('%' || :modelToken || '%') COLLATE NOCASE) LIMIT 25")
    suspend fun findByBrandAndModelToken(userId: Long, brand: String, modelToken: String): List<Product>

    @Query("UPDATE products SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long): Int

    @Query("SELECT COUNT(*) FROM products WHERE userId = :userId AND isDeleted = 0")
    suspend fun getProductCount(userId: Long): Int

    @Query("SELECT * FROM products WHERE isDeleted = 0 AND warrantyExpiryDate IS NOT NULL AND warrantyExpiryDate > :now AND warrantyExpiryDate <= :in30 ORDER BY warrantyExpiryDate ASC")
    fun getExpiringSoonProducts(now: Long, in30: Long): List<Product>

    @Query("SELECT * FROM products WHERE isDeleted = 0 AND warrantyExpiryDate IS NOT NULL AND warrantyExpiryDate <= :now ORDER BY warrantyExpiryDate ASC")
    fun getExpiredProducts(now: Long): List<Product>
}
