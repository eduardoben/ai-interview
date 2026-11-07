package com.edgecue.app.data.rag.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Supported RAG document source types.
 */
enum class RagSourceType {
    CURRICULUM,
    JOB_DESCRIPTION;

    val displayName: String
        get() = when (this) {
            CURRICULUM -> "Curriculum / Resume"
            JOB_DESCRIPTION -> "Target Job Description"
        }
}

/**
 * Room entity representing an imported or pasted RAG document.
 */
@Entity(tableName = "rag_documents")
data class RagDocumentEntity(
    @PrimaryKey
    val sourceType: String,
    val fileName: String,
    val mimeType: String,
    val rawText: String,
    val charCount: Int,
    val chunkCount: Int,
    val updatedAtEpochMs: Long
)
