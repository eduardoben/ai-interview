package com.edgecue.app.data.rag

import com.edgecue.app.data.rag.db.RagChunkEntity

/**
 * Section-aware document chunker and lexical pre-tokenizer.
 * Splits documents into ~320 char chunks (max 360 chars, ~50 char overlap)
 * without splitting words, detecting resume and JD section headings,
 * and generating pre-tokenized stems stored as tokensCsv.
 */
class RagDocumentChunker(
    val targetChunkChars: Int = 320,
    val maxChunkChars: Int = 360,
    val overlapChars: Int = 50
) {

    companion object {
        private val SECTION_HEADING_REGEX = Regex(
            """^(?:#+\s*)?(?:[•|\-–—*]\s*)?(experience|work\s+history|employment|professional\s+experience|clinical\s+experience|""" +
                    """teaching\s+experience|instructional\s+experience|classroom\s+experience|curriculum\s+development|""" +
                    """research\s+experience|laboratory\s+skills|fieldwork|clinical\s+rotations|residency|fellowship|fellowships|""" +
                    """legal\s+experience|litigation\s+experience|representative\s+matters|bar\s+admissions|clerkships|practice\s+areas|""" +
                    """coaching\s+experience|athletic\s+experience|team\s+achievements|administrative\s+experience|office\s+management|executive\s+support|""" +
                    """design\s+experience|selected\s+works|studio\s+projects|exhibitions|""" +
                    """internships|relevant\s+coursework|coursework|academic\s+projects|extracurricular\s+activities|""" +
                    """leadership(?:\s*[&/and]+\s*activities)?|volunteer\s+experience|community\s+service|""" +
                    """grants(?:\s*[&/and]+\s*fellowships)?|honors(?:\s*[&/and]+\s*awards)?|presentations|conferences|affiliations|professional\s+affiliations|memberships|""" +
                    """education|academic\s+background|skills|technical\s+skills|clinical\s+skills|core\s+competencies(?:\s*[&/and]+\s*(?:clinical|technical)\s+skills)?|technologies|""" +
                    """projects|key\s+projects|selected\s+projects|case\s+studies|portfolio|summary|professional\s+summary|executive\s+summary|profile|about\s+me|objective|""" +
                    """licensure(?:\s*[&/and]+\s*certifications?)?|certifications?(?:\s*[&/and]+\s*(?:licensure|licenses|training|courses))?|""" +
                    """professional\s+certifications?|technical\s+certifications?|certificates|licenses(?:\s*[&/and]+\s*credentials?)?|""" +
                    """board\s+certifications|credentials|accreditations|security\s+clearances|awards|publications|patents|languages|""" +
                    """responsibilities|key\s+responsibilities|what\s+you'll\s+do|duties|job\s+duties|role\s+overview|position\s+overview|about\s+the\s+role|""" +
                    """requirements|qualifications|basic\s+qualifications|minimum\s+qualifications|""" +
                    """preferred\s+qualifications|what\s+we're\s+looking\s+for)(?:\s*[:|—\-–])?[:\s]*$""",
            RegexOption.IGNORE_CASE
        )

        private val CONTACT_OR_FOOTER_LINE_REGEX = Regex(
            """^(?:[\w.+-]+@[\w.-]+\.[a-zA-Z]{2,}|https?://\S+|(?:www\.)?(?:linkedin|github)\.com/\S+|\s*[•|-]?\s*|.*curriculum\s+vitae.*page\s+\d+\s+of\s+\d+.*)$""",
            RegexOption.IGNORE_CASE
        )

        private val STOPWORDS = setOf(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but",
            "by", "can", "cannot", "could", "did", "do", "does", "doing", "down", "during", "each",
            "few", "for", "from", "further", "had", "has", "have", "having", "he", "her", "here",
            "hers", "herself", "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it",
            "its", "itself", "me", "more", "most", "my", "myself", "no", "nor", "not", "of", "off",
            "on", "once", "only", "or", "other", "our", "ours", "ourselves", "out", "over", "own",
            "same", "she", "should", "so", "some", "such", "than", "that", "the", "their", "theirs",
            "them", "themselves", "then", "there", "these", "they", "this", "those", "through", "to",
            "too", "under", "until", "up", "very", "was", "we", "were", "what", "when", "where",
            "which", "while", "who", "whom", "why", "with", "would", "you", "your", "yours",
            "yourself", "yourselves"
        )
    }

    private data class ParsedSection(
        val title: String,
        val text: String
    )

    /**
     * Splits [rawText] of [sourceType] into an ordered list of [RagChunkEntity].
     */
    fun chunkDocument(sourceType: String, rawText: String): List<RagChunkEntity> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val sections = parseSections(trimmed)
        val allChunks = mutableListOf<RagChunkEntity>()
        var globalChunkIndex = 0

        for (section in sections) {
            val sectionChunks = chunkSection(
                sourceType = sourceType,
                sectionTitle = section.title,
                sectionText = section.text,
                startChunkIndex = globalChunkIndex
            )
            allChunks.addAll(sectionChunks)
            globalChunkIndex += sectionChunks.size
        }

        return allChunks
    }

    /**
     * Parses the text into sections using detected headings.
     */
    private fun parseSections(text: String): List<ParsedSection> {
        val lines = text.lines()
        val sections = mutableListOf<ParsedSection>()

        var currentTitle = "Overview"
        val currentLines = mutableListOf<String>()

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty() && CONTACT_OR_FOOTER_LINE_REGEX.matches(trimmedLine)) {
                continue
            }
            // Strip repeated page footers that end with an email address (e.g., "Himmler Benitez himmlerbenitez@gmail.com")
            if (trimmedLine.contains("@") && trimmedLine.length < 80 &&
                trimmedLine.split(Regex("\\s+")).any { it.contains("@") && it.contains(".") }
            ) {
                continue
            }
            val match = SECTION_HEADING_REGEX.matchEntire(trimmedLine)
            if (match != null) {
                if (currentLines.isNotEmpty()) {
                    val sectionContent = currentLines.joinToString("\n").trim()
                    if (sectionContent.isNotEmpty()) {
                        sections.add(ParsedSection(currentTitle, sectionContent))
                    }
                    currentLines.clear()
                }
                currentTitle = formatHeadingTitle(match.groupValues[1])
            } else {
                currentLines.add(line)
            }
        }

        if (currentLines.isNotEmpty()) {
            val sectionContent = currentLines.joinToString("\n").trim()
            if (sectionContent.isNotEmpty()) {
                sections.add(ParsedSection(currentTitle, sectionContent))
            }
        }

        // If the first section is a short pre-heading header block (e.g., Candidate Name + Title or Job Title),
        // merge it into the next section (e.g., Summary or Role Overview) so chunk 0 has full context.
        if (sections.size >= 2 && sections[0].title == "Overview" && sections[0].text.length <= 240) {
            val header = sections[0].text.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" — ")
            val next = sections[1]
            sections[1] = ParsedSection(
                title = next.title,
                text = if (header.isNotEmpty()) "$header. ${next.text}" else next.text
            )
            sections.removeAt(0)
        }

        return if (sections.isEmpty() && text.isNotEmpty()) {
            listOf(ParsedSection("Overview", text))
        } else {
            sections
        }
    }

    private fun formatHeadingTitle(raw: String): String {
        return raw.split(Regex("\\s+")).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Chunks a single section's text with sliding window and overlap.
     */
    private fun chunkSection(
        sourceType: String,
        sectionTitle: String,
        sectionText: String,
        startChunkIndex: Int
    ): List<RagChunkEntity> {
        val units = splitIntoUnits(sectionText)
        if (units.isEmpty()) return emptyList()

        val result = mutableListOf<RagChunkEntity>()
        var currentChunkIndex = startChunkIndex

        var buffer = StringBuilder()
        var overlapPrefix = ""

        for (unit in units) {
            val candidate = if (buffer.isEmpty()) {
                if (overlapPrefix.isNotEmpty()) "$overlapPrefix $unit" else unit
            } else {
                "${buffer} $unit"
            }

            if (candidate.length <= maxChunkChars) {
                buffer.clear()
                buffer.append(candidate)
                if (buffer.length >= targetChunkChars) {
                    val chunkText = buffer.toString().trim()
                    if (chunkText.isNotEmpty()) {
                        result.add(createChunk(sourceType, currentChunkIndex++, sectionTitle, chunkText))
                        overlapPrefix = computeOverlap(chunkText)
                        buffer.clear()
                    }
                }
            } else {
                if (buffer.isNotEmpty()) {
                    val chunkText = buffer.toString().trim()
                    if (chunkText.isNotEmpty()) {
                        result.add(createChunk(sourceType, currentChunkIndex++, sectionTitle, chunkText))
                        overlapPrefix = computeOverlap(chunkText)
                    }
                    buffer.clear()
                }

                // Handle unit that exceeds maxChunkChars on its own
                if (unit.length > maxChunkChars) {
                    val subChunks = splitLongUnit(unit, overlapPrefix)
                    for (i in subChunks.indices) {
                        val subText = subChunks[i].trim()
                        if (subText.isNotEmpty()) {
                            if (i == subChunks.size - 1 && subText.length < targetChunkChars) {
                                // Keep last subchunk in buffer for continuation
                                buffer.append(subText)
                                overlapPrefix = ""
                            } else {
                                result.add(createChunk(sourceType, currentChunkIndex++, sectionTitle, subText))
                                overlapPrefix = computeOverlap(subText)
                            }
                        }
                    }
                } else {
                    buffer.append(if (overlapPrefix.isNotEmpty()) "$overlapPrefix $unit" else unit)
                    overlapPrefix = ""
                }
            }
        }

        if (buffer.isNotEmpty()) {
            val chunkText = buffer.toString().trim()
            if (chunkText.isNotEmpty()) {
                // Ensure the final chunk isn't an exact duplicate of the previous chunk
                val lastContent = result.lastOrNull()?.content
                if (chunkText != lastContent) {
                    result.add(createChunk(sourceType, currentChunkIndex, sectionTitle, chunkText))
                }
            }
        }

        return result
    }

    /**
     * Splits section text into natural sentences or bullet units.
     */
    private fun splitIntoUnits(text: String): List<String> {
        val lines = text.lines()
        val units = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Bullet points
            if (trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("*")) {
                units.add(trimmed)
            } else {
                // Split sentences on punctuation boundaries
                val sentenceRegex = Regex("""(?<=[.?!])\s+(?=[A-Z0-9"'])""")
                val sentences = trimmed.split(sentenceRegex)
                for (s in sentences) {
                    val sTrimmed = s.trim()
                    if (sTrimmed.isNotEmpty()) {
                        units.add(sTrimmed)
                    }
                }
            }
        }
        return units
    }

    /**
     * Computes clean ~50 char overlap without severing words mid-word.
     */
    private fun computeOverlap(text: String): String {
        if (text.length <= overlapChars) return text
        val candidate = text.takeLast(overlapChars)
        val firstSpace = candidate.indexOf(' ')
        return if (firstSpace in 0 until candidate.length - 1) {
            candidate.substring(firstSpace + 1).trim()
        } else {
            candidate.trim()
        }
    }

    /**
     * Splits an oversized single unit into ~320 char segments with ~50 char overlap.
     */
    private fun splitLongUnit(unit: String, initialOverlap: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var currentPrefix = initialOverlap

        while (start < unit.length) {
            val remaining = unit.substring(start)
            val targetLength = maxChunkChars - (if (currentPrefix.isNotEmpty()) currentPrefix.length + 1 else 0)

            if (remaining.length <= targetLength) {
                val full = if (currentPrefix.isNotEmpty()) "$currentPrefix $remaining" else remaining
                result.add(full.take(maxChunkChars))
                break
            }

            var splitPoint = start + targetLength
            if (splitPoint < unit.length) {
                val spaceBoundary = unit.lastIndexOf(' ', splitPoint)
                if (spaceBoundary > start + (targetLength / 2)) {
                    splitPoint = spaceBoundary
                }
            } else {
                splitPoint = unit.length
            }

            val segment = unit.substring(start, splitPoint).trim()
            val fullSegment = if (currentPrefix.isNotEmpty()) "$currentPrefix $segment" else segment
            result.add(fullSegment.take(maxChunkChars))

            currentPrefix = computeOverlap(segment)
            start = splitPoint
            while (start < unit.length && unit[start].isWhitespace()) {
                start++
            }
        }

        return result
    }

    private fun createChunk(
        sourceType: String,
        chunkIndex: Int,
        sectionTitle: String,
        content: String
    ): RagChunkEntity {
        val safeContent = if (content.length > maxChunkChars) content.take(maxChunkChars) else content
        val tokens = tokenizeText("$sectionTitle $safeContent") + getSectionSemanticTokens(sectionTitle)
        return RagChunkEntity(
            id = 0,
            sourceType = sourceType,
            chunkIndex = chunkIndex,
            sectionTitle = sectionTitle,
            content = safeContent,
            tokensCsv = tokens.joinToString(",")
        )
    }

    private fun getSectionSemanticTokens(sectionTitle: String): List<String> {
        val s = sectionTitle.lowercase()
        return when {
            s.contains("education") || s.contains("academic") || s.contains("coursework") ->
                listOf("educa", "academ", "degre", "univers", "progres", "school", "graduat", "master", "bachelor", "coursework", "major")
            s.contains("teaching") || s.contains("instruction") || s.contains("classroom") || s.contains("curriculum") ->
                listOf("teach", "educa", "classroom", "curriculum", "pedagogi", "lesson", "student", "experi", "instruct")
            s.contains("research") || s.contains("laboratori") || s.contains("laboratory") || s.contains("fieldwork") || s.contains("publica") ->
                listOf("research", "laboratori", "experi", "assay", "scientif", "studi", "protocol", "publica", "method")
            s.contains("legal") || s.contains("litiga") || s.contains("matter") || s.contains("bar ") || s.contains("clerkship") ->
                listOf("legal", "litiga", "counsel", "matter", "case", "contract", "statut", "court", "experi", "bar")
            s.contains("clinical") || s.contains("residen") || s.contains("rotation") || s.contains("fellowship") ->
                listOf("clinic", "patient", "hospit", "care", "diagnos", "treatment", "experi", "residen", "rotat")
            s.contains("coach") || s.contains("athlet") ->
                listOf("coach", "athlet", "team", "player", "train", "mentor", "leadership", "experi")
            s.contains("administrat") || s.contains("office") || s.contains("executive support") ->
                listOf("administrat", "execut", "calendar", "schedul", "oper", "coordin", "support", "experi")
            s.contains("experience") || s.contains("work") || s.contains("employment") || s.contains("internship") ->
                listOf("experi", "profession", "journey", "career", "work", "histori", "role", "compani", "internship")
            s.contains("summary") || s.contains("overview") || s.contains("profile") || s.contains("objective") ->
                listOf("summari", "overview", "background", "profil", "introduc", "career", "journey")
            s.contains("project") || s.contains("case stud") || s.contains("portfolio") || s.contains("selected work") || s.contains("studio") ->
                listOf("project", "portfolio", "built", "architect", "develop", "implement", "end-to-end", "deploy", "pipelin", "model", "produc", "serv", "api", "system", "workflow", "design", "studio")
            s.contains("certif") || s.contains("licens") || s.contains("credential") || s.contains("accredit") ->
                listOf("certif", "licens", "credential", "accredit", "board", "qualif", "exam", "clearanc", "charter", "registr", "competenc")
            s.contains("skill") || s.contains("competenc") || s.contains("technolog") ->
                listOf("skill", "technic", "clinic", "competenc", "tool", "framework", "technolog", "modalit", "complianc", "stack")
            s.contains("leadership") || s.contains("extracurricular") || s.contains("volunteer") || s.contains("award") || s.contains("grant") ->
                listOf("leadership", "achiev", "award", "volunteer", "grant", "initiativ", "mentor", "communiti")
            s.contains("requirement") || s.contains("qualif") || s.contains("responsib") || s.contains("duties") ->
                listOf("requir", "qualif", "respons", "role", "fit", "expect")
            else -> emptyList()
        }
    }

    /**
     * Tokenizes text: lowercase, preserves tech tokens (c++, c#, .net, ci/cd),
     * removes stopwords and punctuation, and applies lightweight stemming.
     */
    fun tokenizeText(text: String): List<String> {
        val normalized = text.lowercase()
            .replace("c++", "cpp")
            .replace("c#", "csharp")
            .replace(".net", "dotnet")
            .replace("ci/cd", "cicd")
            .replace("node.js", "nodejs")
            .replace("do-178b/c", "do178bc")
            .replace("do-178c", "do178c")
            .replace("do-178b", "do178b")

        // Split on non-alphanumeric characters except hyphen within words
        val rawTokens = normalized.split(Regex("[^a-z0-9_-]+"))
        val tokens = mutableListOf<String>()

        for (raw in rawTokens) {
            val token = raw.trim('-', '_')
            if (token.length in 2..30 && token !in STOPWORDS) {
                tokens.add(stemToken(token))
            }
        }
        return tokens
    }

    /**
     * Fast suffix stemming for English technical text.
     */
    fun stemToken(token: String): String {
        when {
            token.startsWith("certif") -> return "certif"
            token.startsWith("licens") -> return "licens"
            token.startsWith("credential") -> return "credential"
            token.startsWith("accredit") -> return "accredit"
        }
        return when {
            token.endsWith("ing") && token.length > 5 -> token.dropLast(3)
            token.endsWith("tion") && token.length > 5 -> token.dropLast(4)
            token.endsWith("ed") && token.length > 4 -> token.dropLast(2)
            token.endsWith("ses") || token.endsWith("xes") || token.endsWith("zes") ||
                    token.endsWith("ches") || token.endsWith("shes") -> token.dropLast(2)
            token.endsWith("s") && !token.endsWith("ss") && token.length > 3 -> token.dropLast(1)
            else -> token
        }
    }
}
