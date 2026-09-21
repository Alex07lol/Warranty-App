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

    @Query("SELECT * FROM products WHERE userId = :userId AND isDeleted = 0 AND (productName LIKE :query OR brand LIKE :query OR model LIKE :query OR serialNumber LIKE :query OR category LIKE :query OR purchaseStore LIKE :query OR warrantyProvider LIKE :query) ORDER BY createdAt DESC LIMIT 100")
    fun searchProducts(userId: Long, query: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE userId = :userId AND serialNumber = :serialNumber AND isDeleted = 0 LIMIT 1")
    suspend fun getProductBySerialNumber(userId: Long, serialNumber: String): Product?

    @Query("SELECT * FROM products WHERE userId = :userId AND serialNumber = :serialNumber AND isDeleted = 0")
    suspend fun getAllBySerialNumber(userId: Long, serialNumber: String): List<Product>

    /** Normalized duplicate lookup: uppercase/trimmed comparisons happen in code via getAllForUser. */

    @Query("UPDATE products SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long): Int

    @Query("SELECT COUNT(*) FROM products WHERE userId = :userId AND isDeleted = 0")
    suspend fun getProductCount(userId: Long): Int

    @Query("SELECT * FROM products WHERE isDeleted = 0 AND warrantyExpiryDate IS NOT NULL AND warrantyExpiryDate > :now AND warrantyExpiryDate <= :in30 ORDER BY warrantyExpiryDate ASC")
    fun getExpiringSoonProducts(now: Long, in30: Long): List<Product>

    @Query("SELECT * FROM products WHERE isDeleted = 0 AND warrantyExpiryDate IS NOT NULL AND warrantyExpiryDate <= :now ORDER BY warrantyExpiryDate ASC")
    fun getExpiredProducts(now: Long): List<Product>
}
