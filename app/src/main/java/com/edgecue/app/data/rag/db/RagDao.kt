package com.edgecue.app.data.rag.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local RAG documents and chunks.
 */
@Dao
interface RagDao {

    @Query("SELECT * FROM rag_documents")
    fun getAllDocumentsFlow(): Flow<List<RagDocumentEntity>>

    @Query("SELECT * FROM rag_documents WHERE sourceType = :sourceType LIMIT 1")
    suspend fun getDocument(sourceType: String): RagDocumentEntity?

    @Query("SELECT * FROM rag_chunks ORDER BY sourceType ASC, chunkIndex ASC")
    suspend fun getAllChunks(): List<RagChunkEntity>

    @Query("SELECT * FROM rag_chunks WHERE sourceType = :sourceType ORDER BY chunkIndex ASC")
    suspend fun getChunksForSource(sourceType: String): List<RagChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: RagDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<RagChunkEntity>)

    @Query("DELETE FROM rag_documents WHERE sourceType = :sourceType")
    suspend fun deleteDocument(sourceType: String)

    @Query("DELETE FROM rag_chunks WHERE sourceType = :sourceType")
    suspend fun deleteChunksBySource(sourceType: String)

    @Transaction
    suspend fun replaceDocumentWithChunks(doc: RagDocumentEntity, chunks: List<RagChunkEntity>) {
        deleteDocument(doc.sourceType)
        insertDocument(doc)
        if (chunks.isNotEmpty()) {
            insertChunks(chunks)
        }
    }
}
