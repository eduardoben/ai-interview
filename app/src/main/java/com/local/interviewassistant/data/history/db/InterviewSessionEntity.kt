package com.local.interviewassistant.data.history.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.local.interviewassistant.core.model.InterviewCategory
import com.local.interviewassistant.data.history.QuestionAnswerRecord

/**
 * Room Database entity representing a persistent interview Q&A session interaction.
 */
@Entity(tableName = "interview_sessions")
data class InterviewSessionEntity(
    @PrimaryKey
    val id: String,
    val question: String,
    val answer: String,
    val category: String,
    val timestamp: Long,
    val ttftMs: Long,
    val decodeTps: Float,
    val tokenCount: Int
) {
    fun toRecord(): QuestionAnswerRecord {
        val cat = try {
            InterviewCategory.valueOf(category)
        } catch (_: Exception) {
            InterviewCategory.GENERAL
        }
        return QuestionAnswerRecord(
            id = id,
            question = question,
            answer = answer,
            category = cat,
            timestamp = timestamp,
            ttftMs = ttftMs,
            decodeTps = decodeTps,
            tokenCount = tokenCount
        )
    }

    companion object {
        fun fromRecord(record: QuestionAnswerRecord): InterviewSessionEntity {
            return InterviewSessionEntity(
                id = record.id,
                question = record.question,
                answer = record.answer,
                category = record.category.name,
                timestamp = record.timestamp,
                ttftMs = record.ttftMs,
                decodeTps = record.decodeTps,
                tokenCount = record.tokenCount
            )
        }
    }
}

