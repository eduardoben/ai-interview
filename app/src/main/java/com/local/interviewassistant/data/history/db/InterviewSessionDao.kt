package com.local.interviewassistant.data.history.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for interview session history.
 */
@Dao
interface InterviewSessionDao {

    @Query("SELECT * FROM interview_sessions ORDER BY timestamp DESC")
    fun getAllSessionsFlow(): Flow<List<InterviewSessionEntity>>

    @Query("SELECT * FROM interview_sessions ORDER BY timestamp DESC")
    suspend fun getAllSessions(): List<InterviewSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: InterviewSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<InterviewSessionEntity>)

    @Query("DELETE FROM interview_sessions")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM interview_sessions")
    suspend fun getCount(): Int
}

