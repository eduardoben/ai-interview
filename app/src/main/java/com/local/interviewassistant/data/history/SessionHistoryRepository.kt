package com.local.interviewassistant.data.history

import com.local.interviewassistant.core.model.InterviewCategory
import com.local.interviewassistant.data.history.db.InterviewSessionDao
import com.local.interviewassistant.data.history.db.InterviewSessionEntity
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QuestionAnswerRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val question: String,
    val answer: String,
    val category: InterviewCategory = InterviewCategory.GENERAL,
    val timestamp: Long = System.currentTimeMillis(),
    val ttftMs: Long = 0L,
    val decodeTps: Float = 0f,
    val tokenCount: Int = 0
)

data class AggregateTelemetry(
    val totalQuestionsAnswered: Int = 0,
    val totalTokensGenerated: Int = 0,
    val avgTtftMs: Long = 0L,
    val avgDecodeTps: Float = 0f
)

/**
 * Repository managing persistent interview history backed by Android Jetpack Room Database,
 * with atomic migration support from legacy raw JSON storage.
 */
class SessionHistoryRepository(
    private val dao: InterviewSessionDao? = null,
    private val legacyStorageDir: File? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) {
    // Secondary constructor for backward compatibility with existing constructor calls
    constructor(legacyStorageDir: File) : this(
        dao = null,
        legacyStorageDir = legacyStorageDir,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ioDispatcher = Dispatchers.IO
    )

    private val legacyHistoryFile = legacyStorageDir?.let { File(it, "interview_session_history.json") }
    private val _records = MutableStateFlow<List<QuestionAnswerRecord>>(emptyList())
    val records: StateFlow<List<QuestionAnswerRecord>> = _records.asStateFlow()

    private val _telemetry = MutableStateFlow(AggregateTelemetry())
    val telemetry: StateFlow<AggregateTelemetry> = _telemetry.asStateFlow()

    init {
        if (dao != null) {
            // Room-backed reactive flow
            scope.launch {
                // Perform one-time migration from legacy JSON file if present
                migrateLegacyJsonIfPresent()

                dao.getAllSessionsFlow().collect { entities ->
                    val domainRecords = entities.map { it.toRecord() }
                    _records.value = domainRecords
                    recomputeTelemetry(domainRecords)
                }
            }
        } else {
            // In-memory or standalone legacy fallback (e.g., in unit tests without Android Context)
            loadLegacyHistory()
        }
    }

    private suspend fun migrateLegacyJsonIfPresent() = withContext(ioDispatcher) {
        val file = legacyHistoryFile ?: return@withContext
        if (!file.exists()) return@withContext
        try {
            val jsonStr = file.readText()
            if (jsonStr.isNotBlank()) {
                val array = JSONArray(jsonStr)
                val entities = mutableListOf<InterviewSessionEntity>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    entities.add(
                        InterviewSessionEntity(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            question = obj.optString("question", ""),
                            answer = obj.optString("answer", ""),
                            category = obj.optString("category", InterviewCategory.GENERAL.name),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            ttftMs = obj.optLong("ttftMs", 0L),
                            decodeTps = obj.optDouble("decodeTps", 0.0).toFloat(),
                            tokenCount = obj.optInt("tokenCount", 0)
                        )
                    )
                }
                if (entities.isNotEmpty() && dao != null) {
                    dao.insertSessions(entities)
                }
            }
            // Once migrated, safely delete legacy JSON file to avoid duplicate imports
            file.delete()
        } catch (_: Exception) {
            // Non-critical fallback; preserve file if parse fails
        }
    }

    @Synchronized
    private fun loadLegacyHistory() {
        val file = legacyHistoryFile ?: return
        if (!file.exists()) return
        try {
            val jsonStr = file.readText()
            if (jsonStr.isBlank()) return
            val array = JSONArray(jsonStr)
            val list = mutableListOf<QuestionAnswerRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val catStr = obj.optString("category", InterviewCategory.GENERAL.name)
                val cat = try { InterviewCategory.valueOf(catStr) } catch (_: Exception) { InterviewCategory.GENERAL }
                list.add(
                    QuestionAnswerRecord(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        question = obj.optString("question", ""),
                        answer = obj.optString("answer", ""),
                        category = cat,
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        ttftMs = obj.optLong("ttftMs", 0L),
                        decodeTps = obj.optDouble("decodeTps", 0.0).toFloat(),
                        tokenCount = obj.optInt("tokenCount", 0)
                    )
                )
            }
            _records.value = list
            recomputeTelemetry(list)
        } catch (_: Exception) {}
    }

    suspend fun recordQa(record: QuestionAnswerRecord) = withContext(ioDispatcher) {
        if (dao != null) {
            dao.insertSession(InterviewSessionEntity.fromRecord(record))
        } else {
            val updated = listOf(record) + _records.value
            _records.value = updated
            recomputeTelemetry(updated)
            persistLegacyHistory(updated)
        }
    }

    suspend fun clearHistory() = withContext(ioDispatcher) {
        if (dao != null) {
            dao.clearAll()
        } else {
            _records.value = emptyList()
            _telemetry.value = AggregateTelemetry()
            legacyHistoryFile?.let {
                if (it.exists()) it.delete()
            }
        }
    }

    private fun recomputeTelemetry(list: List<QuestionAnswerRecord>) {
        if (list.isEmpty()) {
            _telemetry.value = AggregateTelemetry()
            return
        }
        val count = list.size
        val totalTokens = list.sumOf { it.tokenCount }
        val avgTtft = list.map { it.ttftMs }.filter { it > 0 }.let { if (it.isNotEmpty()) it.average().toLong() else 0L }
        val avgTps = list.map { it.decodeTps }.filter { it > 0f }.let { if (it.isNotEmpty()) it.average().toFloat() else 0f }
        _telemetry.value = AggregateTelemetry(
            totalQuestionsAnswered = count,
            totalTokensGenerated = totalTokens,
            avgTtftMs = avgTtft,
            avgDecodeTps = avgTps
        )
    }

    private fun persistLegacyHistory(list: List<QuestionAnswerRecord>) {
        val file = legacyHistoryFile ?: return
        try {
            val array = JSONArray()
            list.take(100).forEach { item ->
                val obj = org.json.JSONObject().apply {
                    put("id", item.id)
                    put("question", item.question)
                    put("answer", item.answer)
                    put("category", item.category.name)
                    put("timestamp", item.timestamp)
                    put("ttftMs", item.ttftMs)
                    put("decodeTps", item.decodeTps.toDouble())
                    put("tokenCount", item.tokenCount)
                }
                array.put(obj)
            }
            file.writeText(array.toString(2))
        } catch (_: Exception) {}
    }

    fun exportToMarkdown(): String {
        val list = _records.value
        if (list.isEmpty()) return "# Interview Session Transcript\n\nNo Q&A pairs recorded yet."
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("# AI Interview Assistant — Session Transcript\n\n")
        sb.append("*Generated on: ${sdf.format(Date())}*\n\n---\n\n")
        list.forEachIndexed { index, item ->
            val num = list.size - index
            val dateStr = sdf.format(Date(item.timestamp))
            sb.append("### Question $num [${item.category.displayName}] — $dateStr\n\n")
            sb.append("**Q:** ${item.question}\n\n")
            sb.append("**AI Copilot Answer:**\n\n${item.answer}\n\n")
            if (item.ttftMs > 0 || item.decodeTps > 0f) {
                sb.append("> *Performance: TTFT ${item.ttftMs}ms | Speed ${String.format(Locale.US, "%.1f", item.decodeTps)} tok/s | Tokens: ${item.tokenCount}*\n\n")
            }
            sb.append("---\n\n")
        }
        return sb.toString()
    }
}
