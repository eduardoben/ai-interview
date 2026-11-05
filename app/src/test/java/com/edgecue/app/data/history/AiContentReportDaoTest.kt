package com.edgecue.app.data.history

import com.edgecue.app.data.history.db.AiContentReportDao
import com.edgecue.app.data.history.db.AiContentReportEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AiContentReportDaoTest {

    private class FakeAiContentReportDao : AiContentReportDao {
        private val reports = mutableListOf<AiContentReportEntity>()
        private val _flow = MutableStateFlow<List<AiContentReportEntity>>(emptyList())

        override fun getAllReportsFlow(): Flow<List<AiContentReportEntity>> = _flow.asStateFlow()

        override suspend fun getAllReports(): List<AiContentReportEntity> = reports.toList()

        override suspend fun insertReport(report: AiContentReportEntity) {
            reports.removeAll { it.id == report.id }
            reports.add(0, report)
            _flow.value = reports.toList()
        }

        override suspend fun deleteReport(id: String) {
            reports.removeAll { it.id == id }
            _flow.value = reports.toList()
        }

        override suspend fun clearAllReports() {
            reports.clear()
            _flow.value = emptyList()
        }

        override suspend fun getCount(): Int = reports.size
    }

    @Test
    fun testEntityDefaultsAndFieldIntegrity() {
        val entity = AiContentReportEntity(
            question = "What is dependency injection?",
            answer = "Dependency injection is passing dependencies into a class rather than constructing them directly.",
            reason = AiReportReason.FACTUAL_INACCURACY.name,
            comment = "Detailed critique"
        )

        assertNotNull(entity.id)
        assertTrue(entity.timestamp > 0L)
        assertEquals("What is dependency injection?", entity.question)
        assertEquals("Dependency injection is passing dependencies into a class rather than constructing them directly.", entity.answer)
        assertEquals(AiReportReason.FACTUAL_INACCURACY.name, entity.reason)
        assertEquals("Detailed critique", entity.comment)
    }

    @Test
    fun testAiReportReasonEnumMetadata() {
        assertEquals(4, AiReportReason.values().size)
        val inaccuracy = AiReportReason.FACTUAL_INACCURACY
        assertEquals("Factual Inaccuracy or Hallucination", inaccuracy.title)
        assertTrue(inaccuracy.description.isNotBlank())

        val offensive = AiReportReason.INAPPROPRIATE_OFFENSIVE
        assertEquals("Inappropriate or Offensive Content", offensive.title)

        val unsafe = AiReportReason.HARMFUL_UNSAFE
        assertEquals("Harmful or Unsafe Advice", unsafe.title)

        val other = AiReportReason.OTHER
        assertEquals("Other Quality or Safety Issue", other.title)
    }

    @Test
    fun testDaoCrudAndReactiveFlow() = runTest {
        val dao = FakeAiContentReportDao()

        assertEquals(0, dao.getCount())
        assertTrue(dao.getAllReports().isEmpty())
        assertTrue(dao.getAllReportsFlow().first().isEmpty())

        val report1 = AiContentReportEntity(
            id = "rep-1",
            timestamp = 1000L,
            question = "Question 1",
            answer = "Answer 1",
            reason = AiReportReason.FACTUAL_INACCURACY.name,
            comment = "Hallucinated syntax"
        )
        dao.insertReport(report1)

        assertEquals(1, dao.getCount())
        assertEquals(1, dao.getAllReports().size)
        assertEquals("rep-1", dao.getAllReportsFlow().first().first().id)

        val report2 = AiContentReportEntity(
            id = "rep-2",
            timestamp = 2000L,
            question = "Question 2",
            answer = "Answer 2",
            reason = AiReportReason.HARMFUL_UNSAFE.name,
            comment = "Security issue"
        )
        dao.insertReport(report2)

        assertEquals(2, dao.getCount())
        val all = dao.getAllReports()
        assertEquals("rep-2", all[0].id)
        assertEquals("rep-1", all[1].id)

        dao.deleteReport("rep-1")
        assertEquals(1, dao.getCount())
        assertEquals("rep-2", dao.getAllReports().first().id)

        dao.clearAllReports()
        assertEquals(0, dao.getCount())
        assertTrue(dao.getAllReports().isEmpty())
    }

    @Test
    fun testSessionHistoryRepositoryAiReportSubmission() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val fakeDao = FakeAiContentReportDao()

        val repo = SessionHistoryRepository(
            scope = testScope,
            ioDispatcher = testDispatcher,
            reportDao = fakeDao
        )

        advanceUntilIdle()
        assertTrue(repo.getAiReports().isEmpty())

        val submitted = repo.submitAiReport(
            question = "How to sort a list in Kotlin?",
            answer = "Use sorted() function",
            reason = AiReportReason.OTHER,
            comment = "Works as expected"
        )

        advanceUntilIdle()
        assertNotNull(submitted.id)
        assertEquals("How to sort a list in Kotlin?", submitted.question)
        assertEquals("Use sorted() function", submitted.answer)
        assertEquals(AiReportReason.OTHER.name, submitted.reason)

        val retrieved = repo.getAiReports()
        assertEquals(1, retrieved.size)
        assertEquals(submitted.id, retrieved.first().id)

        repo.clearAllAiReports()
        advanceUntilIdle()
        assertTrue(repo.getAiReports().isEmpty())
    }

    @Test
    fun testSessionHistoryRepositoryInMemoryReportFallback() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        // Repository without DAO must fall back to in-memory list without crashing
        val repo = SessionHistoryRepository(
            scope = testScope,
            ioDispatcher = testDispatcher,
            reportDao = null
        )

        val submitted = repo.submitAiReport(
            question = "Tell me a joke",
            answer = "Why did the robot cross the road?",
            reason = AiReportReason.INAPPROPRIATE_OFFENSIVE,
            comment = "Not funny"
        )

        assertEquals("Tell me a joke", submitted.question)
        val reports = repo.getAiReports()
        assertEquals(1, reports.size)
        assertEquals(submitted.id, reports.first().id)

        repo.clearAllAiReports()
        assertTrue(repo.getAiReports().isEmpty())
    }
}
