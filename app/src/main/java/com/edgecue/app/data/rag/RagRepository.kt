package com.edgecue.app.data.rag

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.edgecue.app.data.rag.db.RagChunkEntity
import com.edgecue.app.data.rag.db.RagDao
import com.edgecue.app.data.rag.db.RagDocumentEntity
import com.edgecue.app.data.rag.db.RagSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Summary of a stored RAG document for UI consumption.
 */
data class RagDocumentSummary(
    val sourceType: RagSourceType,
    val fileName: String,
    val charCount: Int,
    val chunkCount: Int,
    val updatedAtEpochMs: Long,
    val previewText: String = "",
    val rawText: String = ""
)

/**
 * Reactive status of the Lite RAG subsystem.
 */
data class RagStatus(
    val curriculumDoc: RagDocumentSummary? = null,
    val jobDescriptionDoc: RagDocumentSummary? = null,
    val totalChunks: Int = 0,
    val isBusy: Boolean = false,
    val statusMessage: String? = null
) {
    val isConfigured: Boolean
        get() = curriculumDoc != null || jobDescriptionDoc != null
}

/**
 * Repository orchestrating local document storage, extraction, chunking,
 * and in-memory index synchronization.
 */
class RagRepository(
    private val context: Context,
    private val ragDao: RagDao,
    private val liteRagEngine: LiteRagEngine,
    private val scope: CoroutineScope,
    private val textExtractor: DocumentTextExtractor = DocumentTextExtractor(context),
    private val chunker: RagDocumentChunker = RagDocumentChunker()
) {

    private val _ragStatusFlow = MutableStateFlow(RagStatus())
    val ragStatusFlow: StateFlow<RagStatus> = _ragStatusFlow.asStateFlow()

    init {
        // Observe stored documents reactively and hydrate the in-memory index
        scope.launch(Dispatchers.IO) {
            ragDao.getAllDocumentsFlow().collect { docs ->
                val cvDoc = docs.firstOrNull { it.sourceType == RagSourceType.CURRICULUM.name }?.toSummary(RagSourceType.CURRICULUM)
                val jdDoc = docs.firstOrNull { it.sourceType == RagSourceType.JOB_DESCRIPTION.name }?.toSummary(RagSourceType.JOB_DESCRIPTION)
                val chunks = ragDao.getAllChunks()
                liteRagEngine.updateIndex(chunks)

                _ragStatusFlow.update {
                    it.copy(
                        curriculumDoc = cvDoc,
                        jobDescriptionDoc = jdDoc,
                        totalChunks = chunks.size,
                        isBusy = false
                    )
                }
            }
        }
    }

    /**
     * Imports a document from a content [Uri] (.pdf or .txt).
     */
    suspend fun importDocument(uri: Uri, sourceType: RagSourceType) = withContext(Dispatchers.IO) {
        _ragStatusFlow.update { it.copy(isBusy = true, statusMessage = "Extracting ${sourceType.displayName}...") }
        try {
            val fileName = queryDisplayName(uri) ?: when (sourceType) {
                RagSourceType.CURRICULUM -> "resume.pdf"
                RagSourceType.JOB_DESCRIPTION -> "job_description.txt"
            }
            val mimeType = context.contentResolver.getType(uri) ?: inferMimeType(fileName)

            val rawText = textExtractor.extractFromUri(uri, mimeType, fileName)
            if (rawText.isBlank()) {
                throw IllegalArgumentException("The selected document appears to be empty or unreadable.")
            }

            val chunks = chunker.chunkDocument(sourceType.name, rawText)
            val entity = RagDocumentEntity(
                sourceType = sourceType.name,
                fileName = fileName,
                mimeType = mimeType,
                rawText = rawText,
                charCount = rawText.length,
                chunkCount = chunks.size,
                updatedAtEpochMs = System.currentTimeMillis()
            )

            ragDao.replaceDocumentWithChunks(entity, chunks)
            reloadIndex()
            _ragStatusFlow.update { it.copy(isBusy = false, statusMessage = null) }
        } catch (e: Throwable) {
            _ragStatusFlow.update {
                it.copy(
                    isBusy = false,
                    statusMessage = "Failed to import ${sourceType.displayName}: ${e.message ?: "Unreadable file"}"
                )
            }
            throw e
        }
    }

    /**
     * Overload for importDocument supporting parameter order `(sourceType, uri)`.
     */
    suspend fun importDocumentUri(sourceType: RagSourceType, uri: Uri) = importDocument(uri, sourceType)

    /**
     * Directly saves pasted or manually entered text as a RAG document.
     */
    suspend fun pasteDocument(
        rawText: String,
        sourceType: RagSourceType,
        fileName: String = "Direct Text Input"
    ) = withContext(Dispatchers.IO) {
        _ragStatusFlow.update { it.copy(isBusy = true, statusMessage = "Saving ${sourceType.displayName}...") }
        try {
            val normalized = textExtractor.normalizeText(rawText)
            if (normalized.isBlank()) {
                throw IllegalArgumentException("Pasted text cannot be empty.")
            }

            val chunks = chunker.chunkDocument(sourceType.name, normalized)
            val entity = RagDocumentEntity(
                sourceType = sourceType.name,
                fileName = fileName,
                mimeType = "text/plain",
                rawText = normalized,
                charCount = normalized.length,
                chunkCount = chunks.size,
                updatedAtEpochMs = System.currentTimeMillis()
            )

            ragDao.replaceDocumentWithChunks(entity, chunks)
            reloadIndex()
            _ragStatusFlow.update { it.copy(isBusy = false, statusMessage = null) }
        } catch (e: Throwable) {
            _ragStatusFlow.update {
                it.copy(
                    isBusy = false,
                    statusMessage = "Failed to import ${sourceType.displayName}: ${e.message ?: "Unreadable file"}"
                )
            }
            throw e
        }
    }

    /**
     * Overload for pasteDocument supporting alias `saveDocumentText`.
     */
    suspend fun saveDocumentText(sourceType: RagSourceType, rawText: String, label: String = "Direct Text Input") =
        pasteDocument(rawText, sourceType, label)

    /**
     * Removes the document of [sourceType] and cleans up its chunks.
     */
    suspend fun clearDocument(sourceType: RagSourceType) = withContext(Dispatchers.IO) {
        _ragStatusFlow.update { it.copy(isBusy = true, statusMessage = "Clearing ${sourceType.displayName}...") }
        try {
            ragDao.deleteDocument(sourceType.name)
            reloadIndex()
        } finally {
            _ragStatusFlow.update { it.copy(isBusy = false, statusMessage = null) }
        }
    }

    /**
     * Reloads all chunks from Room and refreshes the in-memory lexical index.
     */
    suspend fun reloadIndex() = withContext(Dispatchers.IO) {
        val chunks = ragDao.getAllChunks()
        liteRagEngine.updateIndex(chunks)
        val cvDoc = ragDao.getDocument(RagSourceType.CURRICULUM.name)?.toSummary(RagSourceType.CURRICULUM)
        val jdDoc = ragDao.getDocument(RagSourceType.JOB_DESCRIPTION.name)?.toSummary(RagSourceType.JOB_DESCRIPTION)
        _ragStatusFlow.update {
            it.copy(
                curriculumDoc = cvDoc,
                jobDescriptionDoc = jdDoc,
                totalChunks = chunks.size
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            } ?: uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    private fun inferMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".txt", ignoreCase = true) -> "text/plain"
            fileName.endsWith(".md", ignoreCase = true) -> "text/markdown"
            else -> "text/plain"
        }
    }

    private fun RagDocumentEntity.toSummary(type: RagSourceType): RagDocumentSummary {
        val singleLinePreview = rawText.replace(Regex("\\s+"), " ").trim().take(180)
        return RagDocumentSummary(
            sourceType = type,
            fileName = fileName,
            charCount = charCount,
            chunkCount = chunkCount,
            updatedAtEpochMs = updatedAtEpochMs,
            previewText = singleLinePreview,
            rawText = rawText
        )
    }
}
