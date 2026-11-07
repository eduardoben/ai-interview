package com.edgecue.app.data.rag

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Extracts, sanitizes, and normalizes text from PDF and plain-text documents.
 * Adheres to local-only privacy: does NOT log extracted document content.
 */
class DocumentTextExtractor(private val context: Context? = null) {

    companion object {
        const val MAX_DOCUMENT_CHARS = 50_000

        private val isPdfBoxInitialized = AtomicBoolean(false)

        fun ensurePdfBoxInitialized(context: Context) {
            if (isPdfBoxInitialized.compareAndSet(false, true)) {
                try {
                    val appContext = runCatching { context.applicationContext }.getOrNull() ?: context
                    PDFBoxResourceLoader.init(appContext)
                } catch (_: Throwable) {
                    // Ignored in unit test environments without Android AssetManager
                }
            }
        }

        private val CONTROL_CHARS_REGEX = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]")
        private val EXCESSIVE_NEWLINES_REGEX = Regex("\n{3,}")
        private val HORIZONTAL_SPACES_REGEX = Regex("[ \\t]{2,}")
        private val BROKEN_HYPHEN_REGEX = Regex("(?<=\\p{L})[\u00AD\uFFFD](?=\\p{L})")
        private val UNICODE_BULLET_REGEX = Regex("^[ \t]*[▪▸◦●‣⁃–—][ \t]*", RegexOption.MULTILINE)
        private val INLINE_SECTION_HEADER_REGEX = Regex(
            """^(?:[#•|\-–—*]\s*)?(certifications?|licensure(?:\s*[&/and]+\s*certifications?)?|""" +
                    """professional\s+certifications?|technical\s+certifications?|licenses(?:\s*[&/and]+\s*credentials?)?|""" +
                    """credentials|education|technical\s+skills|skills|work\s+experience|experience)[:—–]\s*(.+)$""",
            RegexOption.IGNORE_CASE
        )
    }

    init {
        try {
            if (context != null) {
                ensurePdfBoxInitialized(context)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Extracts text from an open [InputStream] based on mime type or file name.
     */
    suspend fun extractText(
        inputStream: InputStream,
        mimeType: String?,
        fileName: String? = null
    ): String = withContext(Dispatchers.IO) {
        val isPdf = mimeType.equals("application/pdf", ignoreCase = true) ||
                fileName?.endsWith(".pdf", ignoreCase = true) == true

        val rawText = if (isPdf) {
            val ctx = context ?: throw IllegalStateException("Context required for PDF text extraction")
            ensurePdfBoxInitialized(ctx)
            PDDocument.load(inputStream).use { pdDoc ->
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                    setShouldSeparateByBeads(true)
                }
                stripper.getText(pdDoc) ?: ""
            }
        } else {
            inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

        normalizeText(rawText)
    }

    /**
     * Extracts text directly from a content [Uri].
     */
    suspend fun extractFromUri(uri: Uri, mimeType: String?, fileName: String? = null): String {
        return withContext(Dispatchers.IO) {
            val ctx = context ?: throw IllegalStateException("Context required for URI text extraction")
            val stream = ctx.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open input stream for URI: $uri")
            stream.use { extractText(it, mimeType, fileName) }
        }
    }

    /**
     * Normalizes raw text: strips BOM, replaces CRLF, strips control characters,
     * collapses excessive newlines, and enforces [MAX_DOCUMENT_CHARS].
     */
    fun normalizeText(rawText: String): String {
        var text = rawText
        if (text.contains("\uFEFF")) {
            text = text.replace("\uFEFF", "")
        }

        text = text.replace("\r\n", "\n").replace("\r", "\n")
        text = BROKEN_HYPHEN_REGEX.replace(text, "-")
        text = CONTROL_CHARS_REGEX.replace(text, "")
        text = UNICODE_BULLET_REGEX.replace(text, "• ")
        text = EXCESSIVE_NEWLINES_REGEX.replace(text, "\n\n")

        val processedLines = mutableListOf<String>()
        for (rawLine in text.lines()) {
            val line = HORIZONTAL_SPACES_REGEX.replace(rawLine.trimEnd(), " ")
            val trimmed = line.trim()
            val inlineMatch = INLINE_SECTION_HEADER_REGEX.matchEntire(trimmed)
            if (inlineMatch != null && inlineMatch.groupValues.size >= 3) {
                val header = inlineMatch.groupValues[1].trim()
                val body = inlineMatch.groupValues[2].trim()
                processedLines.add(header)
                if (body.isNotEmpty()) {
                    val formattedBody = if (body.startsWith("•") || body.startsWith("-")) body else "• $body"
                    processedLines.add(formattedBody)
                }
            } else {
                processedLines.add(line)
            }
        }

        text = processedLines.joinToString("\n").trim()

        if (text.length > MAX_DOCUMENT_CHARS) {
            val truncated = text.take(MAX_DOCUMENT_CHARS)
            val lastBoundary = truncated.lastIndexOfAny(charArrayOf('\n', ' ', '.'))
            text = if (lastBoundary > MAX_DOCUMENT_CHARS - 500) {
                truncated.substring(0, lastBoundary).trimEnd()
            } else {
                truncated.trimEnd()
            }
        }

        return text
    }
}
