package com.edgecue.app.data.rag.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an individual section-aware chunk of a RAG document.
 */
@Entity(
    tableName = "rag_chunks",
    foreignKeys = [
        ForeignKey(
            entity = RagDocumentEntity::class,
            parentColumns = ["sourceType"],
            childColumns = ["sourceType"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sourceType"),
        Index("sourceType", "chunkIndex")
    ]
)
data class RagChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceType: String,
    val chunkIndex: Int,
    val sectionTitle: String,
    val content: String,
    val tokensCsv: String
)
