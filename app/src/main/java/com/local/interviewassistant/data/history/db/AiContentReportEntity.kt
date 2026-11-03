package com.local.interviewassistant.data.history.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room Database entity representing an in-app report of AI-generated content (Requirement R2, P0-03).
 * Persisted locally on-device in SQLite to ensure full user data custody and zero cloud egress.
 */
@Entity(tableName = "ai_content_reports")
data class AiContentReportEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val question: String?,
    val answer: String,
    val reason: String,
    val comment: String? = null
)
