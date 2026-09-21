package com.warrantyvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(document: Document): Long

    @Update
    suspend fun updateDocument(document: Document): Int

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentByIdImmediate(id: Long): Document?

    @Query("SELECT * FROM documents WHERE id = :id")
    fun getDocumentById(id: Long): Flow<Document?>

    @Query("SELECT * FROM documents WHERE userId = :userId ORDER BY uploadedAt DESC")
    fun getAllDocuments(userId: Long): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE productId = :productId ORDER BY uploadedAt DESC")
    fun getDocumentsByProductId(productId: Long): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE userId = :userId AND productId IS NULL ORDER BY uploadedAt DESC")
    fun getStandaloneDocuments(userId: Long): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE userId = :userId AND documentType = :type ORDER BY uploadedAt DESC")
    fun getDocumentsByType(userId: Long, type: String): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE userId = :userId AND ocrStatus = 'processing'")
    suspend fun getProcessingDocuments(userId: Long): List<Document>

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long): Int
}
