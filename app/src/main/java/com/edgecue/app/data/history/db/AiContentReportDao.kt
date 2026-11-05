package com.edgecue.app.data.history.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local AI-generated content reports.
 */
@Dao
interface AiContentReportDao {

    @Query("SELECT * FROM ai_content_reports ORDER BY timestamp DESC")
    fun getAllReportsFlow(): Flow<List<AiContentReportEntity>>

    @Query("SELECT * FROM ai_content_reports ORDER BY timestamp DESC")
    suspend fun getAllReports(): List<AiContentReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: AiContentReportEntity)

    @Query("DELETE FROM ai_content_reports WHERE id = :id")
    suspend fun deleteReport(id: String)

    @Query("DELETE FROM ai_content_reports")
    suspend fun clearAllReports()

    @Query("SELECT COUNT(*) FROM ai_content_reports")
    suspend fun getCount(): Int
}
