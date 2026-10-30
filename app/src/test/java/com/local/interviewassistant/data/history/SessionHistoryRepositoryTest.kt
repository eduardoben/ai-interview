package com.local.interviewassistant.data.history

import com.local.interviewassistant.core.model.InterviewCategory
import com.local.interviewassistant.data.history.db.InterviewSessionDao
import com.local.interviewassistant.data.history.db.InterviewSessionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SessionHistoryRepositoryTest {

    private class FakeSessionDao : InterviewSessionDao {
        private val sessions = mutableListOf<InterviewSessionEntity>()
        private val _flow = MutableStateFlow<List<InterviewSessionEntity>>(emptyList())

        override fun getAllSessionsFlow(): Flow<List<InterviewSessionEntity>> = _flow.asStateFlow()

        override suspend fun getAllSessions(): List<InterviewSessionEntity> = sessions.toList()

        override suspend fun insertSession(session: InterviewSessionEntity) {
            sessions.removeAll { it.id == session.id }
            sessions.add(0, session)
            _flow.value = sessions.toList()
        }

        override suspend fun insertSessions(sessions: List<InterviewSessionEntity>) {
            this.sessions.addAll(0, sessions)
            _flow.value = this.sessions.toList()
        }

        override suspend fun clearAll() {
            sessions.clear()
            _flow.value = emptyList()
        }

        override suspend fun getCount(): Int = sessions.size
    }

    @Test
    fun testEntityConversion() {
        val record = QuestionAnswerRecord(
            id = "test-uuid-1",
            question = "Explain MVVM architecture in Android",
            answer = "MVVM separates UI from business logic using ViewModel and StateFlow",
            category = InterviewCategory.ALGORITHMS,
            timestamp = 1700000000000L,
            ttftMs = 180L,
            decodeTps = 24.5f,
            tokenCount = 120
        )

        val entity = InterviewSessionEntity.fromRecord(record)
        assertEquals("test-uuid-1", entity.id)
        assertEquals("Explain MVVM architecture in Android", entity.question)
        assertEquals("ALGORITHMS", entity.category)
        assertEquals(180L, entity.ttftMs)

        val converted = entity.toRecord()
        assertEquals(record, converted)
    }

    @Test
    fun testRoomDaoBackedRepositoryOperations() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val fakeDao = FakeSessionDao()

        val repo = SessionHistoryRepository(
            dao = fakeDao,
            scope = testScope,
            ioDispatcher = testDispatcher
        )

        advanceUntilIdle()
        assertTrue(repo.records.value.isEmpty())
        assertEquals(0, repo.telemetry.value.totalQuestionsAnswered)

        val rec1 = QuestionAnswerRecord(
            question = "Question 1",
            answer = "Answer 1",
            category = InterviewCategory.GENERAL,
            ttftMs = 200L,
            decodeTps = 20.0f,
            tokenCount = 50
        )
        repo.recordQa(rec1)
        advanceUntilIdle()

        assertEquals(1, repo.records.value.size)
        assertEquals(1, repo.telemetry.value.totalQuestionsAnswered)
        assertEquals(50, repo.telemetry.value.totalTokensGenerated)
        assertEquals(200L, repo.telemetry.value.avgTtftMs)
        assertEquals(20.0f, repo.telemetry.value.avgDecodeTps, 0.001f)

        val rec2 = QuestionAnswerRecord(
            question = "Question 2",
            answer = "Answer 2",
            category = InterviewCategory.SYSTEM_DESIGN,
            ttftMs = 100L,
            decodeTps = 30.0f,
            tokenCount = 150
        )
        repo.recordQa(rec2)
        advanceUntilIdle()

        assertEquals(2, repo.records.value.size)
        assertEquals(2, repo.telemetry.value.totalQuestionsAnswered)
        assertEquals(200, repo.telemetry.value.totalTokensGenerated)
        assertEquals(150L, repo.telemetry.value.avgTtftMs)
        assertEquals(25.0f, repo.telemetry.value.avgDecodeTps, 0.001f)

        val markdown = repo.exportToMarkdown()
        assertTrue(markdown.contains("AI Interview Assistant — Session Transcript"))
        assertTrue(markdown.contains("Question 1"))
        assertTrue(markdown.contains("Question 2"))

        repo.clearHistory()
        advanceUntilIdle()
        assertTrue(repo.records.value.isEmpty())
        assertEquals(0, repo.telemetry.value.totalQuestionsAnswered)
    }

    @Test
    fun testLegacyJsonMigrationToRoom() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val tempDir = File.createTempFile("legacy_hist", "dir").apply { delete(); mkdirs() }
        val legacyJsonFile = File(tempDir, "interview_session_history.json")

        legacyJsonFile.writeText(
            """
            [
              {
                "id": "migrated-1",
                "question": "Legacy Question",
                "answer": "Legacy Answer",
                "category": "BEHAVIORAL",
                "timestamp": 1690000000000,
                "ttftMs": 150,
                "decodeTps": 22.5,
                "tokenCount": 80
              }
            ]
            """.trimIndent()
        )

        val fakeDao = FakeSessionDao()
        val repo = SessionHistoryRepository(
            dao = fakeDao,
            legacyStorageDir = tempDir,
            scope = testScope,
            ioDispatcher = testDispatcher
        )

        advanceUntilIdle()

        assertEquals(1, repo.records.value.size)
        assertEquals("migrated-1", repo.records.value.first().id)
        assertEquals("Legacy Question", repo.records.value.first().question)
        assertEquals(InterviewCategory.BEHAVIORAL, repo.records.value.first().category)

        // Legacy file should have been cleaned up after migration
        assertFalse(legacyJsonFile.exists())

        tempDir.deleteRecursively()
    }
}
