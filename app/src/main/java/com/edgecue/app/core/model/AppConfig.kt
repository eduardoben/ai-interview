package com.edgecue.app.core.model

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Suppress("unused")
class AppConfig(val modelsRootDir: File) {
    val vadDir = File(modelsRootDir, "vad")
    val asrDir = File(modelsRootDir, "asr")
    val whisperDir = File(modelsRootDir, "whisper")
    val llmDir = File(modelsRootDir, "llm")

    val vadModelFile: File
        get() {
            val sub = File(vadDir, "silero_vad.onnx")
            if (sub.exists()) return sub
            val flat = File(modelsRootDir, "silero_vad.onnx")
            if (flat.exists()) return flat
            return if (vadDir.exists()) sub else flat
        }

    val sttEncoderFile: File
        get() {
            val sub = File(asrDir, "encoder-epoch-99-avg-1.int8.onnx")
            if (sub.exists()) return sub
            val flat = File(modelsRootDir, "encoder.onnx")
            if (flat.exists()) return flat
            return if (asrDir.exists()) sub else flat
        }

    val sttDecoderFile: File
        get() {
            val subInt8 = File(asrDir, "decoder-epoch-99-avg-1.int8.onnx")
            if (subInt8.exists()) return subInt8
            val sub = File(asrDir, "decoder-epoch-99-avg-1.onnx")
            if (sub.exists()) return sub
            val flat = File(modelsRootDir, "decoder.onnx")
            if (flat.exists()) return flat
            return if (asrDir.exists()) subInt8 else flat
        }

    val sttJoinerFile: File
        get() {
            val sub = File(asrDir, "joiner-epoch-99-avg-1.int8.onnx")
            if (sub.exists()) return sub
            val flat = File(modelsRootDir, "joiner.onnx")
            if (flat.exists()) return flat
            return if (asrDir.exists()) sub else flat
        }

    val sttTokensFile: File
        get() {
            val sub = File(asrDir, "tokens.txt")
            if (sub.exists()) return sub
            val flat = File(modelsRootDir, "tokens.txt")
            if (flat.exists()) return flat
            return if (asrDir.exists()) sub else flat
        }

    val sttModelVersionFile = File(modelsRootDir, ".stt_version")
    val hotwordsVersionFile = File(modelsRootDir, ".hotwords_version")
    val hotwordsFile = File(modelsRootDir, "hotwords.txt")

    val offlineWhisperEncoderFile: File
        get() {
            val sub = File(whisperDir, "tiny.en-encoder.int8.onnx")
            if (sub.exists()) return sub
            val flat = File(modelsRootDir, "tiny.en-encoder.int8.onnx")
            if (flat.exists()) return flat
            return if (whisperDir.exists()) sub else flat
        }

    val offlineWhisperDecoderFile: File
        get() {
            val sub = File(whisperDir, "tiny.en-decoder.int8.onnx")
            if (sub.exists()) return sub
            val flat = File(modelsRootDir, "tiny.en-decoder.int8.onnx")
            if (flat.exists()) return flat
            return if (whisperDir.exists()) sub else flat
        }

    val offlineWhisperTokensFile: File
        get() {
            val sub = File(whisperDir, "tiny.en-tokens.txt")
            if (sub.exists()) return sub
            val flat = File(modelsRootDir, "tiny.en-tokens.txt")
            if (flat.exists()) return flat
            return if (whisperDir.exists()) sub else flat
        }

    val llmModelFile: File
        get() {
            val sub = File(llmDir, "gemma-4-E2B-it.litertlm")
            if (sub.exists()) return sub
            val flat = File(modelsRootDir, "gemma-4-E2B-it.litertlm")
            if (flat.exists()) return flat
            return if (llmDir.exists()) sub else flat
        }

    val tpuLlmModelFile: File
        get() {
            val fileName = resolveTensorModelFileName()
            val sub = File(llmDir, fileName)
            if (sub.exists()) return sub
            val flat = File(modelsRootDir, fileName)
            if (flat.exists()) return flat
            return if (llmDir.exists()) sub else flat
        }

    fun hasOfflineRescorer(): Boolean {
        return enableWhisperRescorer &&
                offlineWhisperEncoderFile.exists() && offlineWhisperEncoderFile.length() > 0L &&
                offlineWhisperDecoderFile.exists() && offlineWhisperDecoderFile.length() > 0L &&
                offlineWhisperTokensFile.exists() && offlineWhisperTokensFile.length() > 0L
    }

    fun resolveTensorModelFileName(): String {
        val soc = try { android.os.Build.SOC_MODEL.lowercase().trim() } catch (_: Throwable) { "" }
        val hw = try { android.os.Build.HARDWARE.lowercase().trim() } catch (_: Throwable) { "" }
        val device = try { android.os.Build.MODEL.lowercase().trim() } catch (_: Throwable) { "" }
        return when {
            soc.contains("g6") || hw.contains("malibu") || device.contains("pixel 11") -> "gemma-4-E2B-it_Google_Tensor_G6.litertlm"
            soc.contains("g5") || hw.contains("laguna") || device.contains("pixel 10") -> "gemma-4-E2B-it_Google_Tensor_G5.litertlm"
            soc.contains("g4") || hw.contains("zumapro") || device.contains("pixel 9") -> "gemma-4-E2B-it_Google_Tensor_G4.litertlm"
            soc.contains("g3") || hw.contains("zuma") || device.contains("pixel 8") -> "gemma-4-E2B-it_Google_Tensor_G3.litertlm"
            else -> "gemma-4-E2B-it_Google_Tensor_G5.litertlm"
        }
    }

    fun ensureHotwordsFile(forceRefresh: Boolean = false): File {
        val currentVersion = try {
            if (hotwordsVersionFile.exists()) hotwordsVersionFile.readText().trim() else ""
        } catch (_: Throwable) { "" }

        val needsRefresh = forceRefresh || !hotwordsFile.exists() || hotwordsFile.length() <= 0L ||
                currentVersion != HOTWORDS_VERSION ||
                try { hotwordsFile.readText().any { it.isUpperCase() } } catch (_: Throwable) { true }

        if (needsRefresh) {
            val keywords = listOf(
                // General, Behavioral & School / University Admissions / Students
                "behavioral", "strengths", "weaknesses", "leadership", "achievement", "challenge",
                "conflict", "resolution", "star", "situation", "task", "action", "result",
                "motivation", "collaboration", "communication", "teamwork", "academic", "admissions",
                "research", "curriculum", "thesis", "undergraduate", "graduate", "dissertation",
                "extracurricular", "experience", "project", "management", "organization",
                "internship", "coursework", "scholarship", "fellowship", "capstone",

                // Software, Engineering, AI/ML & Computer Science (#1 Specialized Depth)
                "kotlin", "java", "coroutines", "stateflow", "sharedflow", "mutablestateflow",
                "viewmodel", "lifecycle", "jetpack", "compose", "dependency injection",
                "hilt", "dagger", "room", "retrofit", "okhttp", "serialization",
                "polymorphism", "inheritance", "encapsulation", "abstraction", "interface",
                "multithreading", "concurrency", "synchronized", "volatile", "mutex",
                "dispatchers", "main", "default", "io", "unconfined", "suspend",
                "solid", "singleton", "factory", "observer", "repository", "architecture",
                "mvvm", "mvi", "clean architecture", "testing", "junit", "mockk", "espresso",
                "playwright", "selenium", "docker", "kubernetes", "fastapi", "spring boot",
                "python", "typescript", "javascript", "react", "angular",
                "postgresql", "mysql", "mongodb", "redis", "graphql", "rest api",
                "microservices", "kafka", "rabbitmq", "ci cd", "garbage collection",
                "heap", "stack", "memory leak", "algorithms", "data structures",
                "pytorch", "tensorflow", "scikit learn", "latency", "throughput",
                "distributed systems", "load balancing", "sharding", "caching", "idempotency",

                // Teachers, Educators & Academic Instruction
                "pedagogy", "differentiated instruction", "classroom management", "lesson plan",
                "formative assessment", "summative assessment", "rubric", "iep", "ferpa",
                "special education", "scaffolding", "student engagement", "instructional design",

                // Physicians, Healthcare, Nursing & Clinical Social Work
                "clinical", "patient", "diagnosis", "differential diagnosis", "treatment",
                "pathology", "pharmacology", "anatomy", "oncology", "cardiology", "pediatrics",
                "surgery", "practice", "inpatient", "outpatient", "triage", "crisis",
                "care coordination", "discharge", "de escalation", "regulatory compliance",
                "social work", "biopsychosocial", "palliative", "hipaa", "electronic health record",

                // Biologists, Life Sciences & Laboratory Research
                "biology", "molecular", "genetics", "genomics", "proteomics", "microbiology",
                "biochemistry", "cell culture", "assay", "pcr", "sequencing", "microscopy",
                "chromatography", "spectroscopy", "reproducibility", "hypothesis", "protocol",

                // Lawyers, Legal Counsel & Compliance
                "litigation", "jurisdiction", "precedent", "statute", "deposition", "discovery",
                "arbitration", "mediation", "fiduciary", "indemnification", "due diligence",
                "regulatory", "compliance", "contract", "intellectual property", "tort",

                // Architects, Urban Planning & Structural Design
                "architectural", "zoning", "building code", "sustainability", "leed",
                "AutoCAD", "revit", "bim", "structural", "schematic design",
                "construction administration", "spatial", "rendering", "site analysis",

                // Coaches, Athletics & Performance Leadership
                "coaching", "mentorship", "athletics", "conditioning", "game plan",
                "scouting", "player development", "sportsmanship", "rehabilitation", "roster",

                // Secretaries, Executive Assistants & Office Administration
                "executive assistant", "calendar management", "scheduling", "correspondence",
                "itinerary", "gatekeeping", "confidentiality", "minutes", "procurement",
                "stakeholder", "onboarding", "office administration",

                // Product, Business, Finance & Consulting
                "roadmap", "metrics", "kpi", "kpis", "analytics",
                "user experience", "prioritization", "agile", "scrum", "strategy",
                "opportunity", "market", "competitor", "revenue", "growth",
                "valuation", "ebitda", "profitability", "roi", "balance sheet",
                "income statement", "cash flow", "portfolio", "financial modeling",
                "capital", "assets", "equity", "margin",

                // Common Question & Interview conversational patterns
                "tell me about", "tell us about", "what is the difference between",
                "how do you handle", "can you explain", "walk me through", "walk us through",
                "how does", "what are the advantages of", "why would you choose",
                "what happens when", "how would you design", "give me an example"
            )
            try {
                hotwordsFile.writeText(keywords.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct().joinToString("\n"))
                hotwordsVersionFile.writeText(HOTWORDS_VERSION)
            } catch (_: Exception) {}
        }
        return hotwordsFile
    }

    val activeLlmModelFile: File
        get() {
            val path = _llmConfig.value.selectedModelPath
            if (path.isNotBlank()) return File(path)
            return if (tpuLlmModelFile.exists()) tpuLlmModelFile else llmModelFile
        }

    fun discoverLocalModels(): List<File> {
        if (!modelsRootDir.exists() || !modelsRootDir.isDirectory) return emptyList()
        return modelsRootDir.walkTopDown()
            .filter { file -> file.isFile && isSupportedLlmModel(file) && file.length() > 0L }
            .toList()
    }

    val sampleRateHz: Int = 16000
    val vadWindowSizeSamples: Int = 512
    val vadThreshold: Float = 0.5f
    val minSilenceDurationSec: Float get() = _llmConfig.value.minSilenceDurationSec
    val minSpeechDurationSec: Float = 0.25f
    val minQuestionLengthChars: Int = 12

    // STT Decoding & Beam Search Configuration (v1.1)
    val sttDecodingMethod: String = "modified_beam_search"
    val sttMaxActivePaths: Int = 4
    val sttHotwordsScore: Float = 2.0f
    val sttBlankPenalty: Float = 1.0f
    val enableHardwareAudioEffects: Boolean get() = _llmConfig.value.enableHardwareAudioEffects

    private val _llmConfig = MutableStateFlow(LlmGenerationConfig())
    val llmConfig: StateFlow<LlmGenerationConfig> = _llmConfig

    val maxTokens: Int get() = _llmConfig.value.maxTokens
    val temperature: Float get() = _llmConfig.value.temperature
    val topK: Int get() = _llmConfig.value.topK
    val preferredDelegate: com.edgecue.app.data.hardware.DelegateType?
        get() = _llmConfig.value.preferredDelegate

    val useGpuDelegate: Boolean
        get() = preferredDelegate != com.edgecue.app.data.hardware.DelegateType.CPU_MMAP

    val enableWhisperRescorer: Boolean
        get() = _llmConfig.value.enableWhisperRescorer

    fun updateLlmConfig(newConfig: LlmGenerationConfig) {
        _llmConfig.value = newConfig.validated()
    }

    fun ensureDirectoriesExist(): Boolean {
        return try {
            if (!modelsRootDir.exists()) modelsRootDir.mkdirs()
            if (!vadDir.exists()) vadDir.mkdirs()
            if (!asrDir.exists()) asrDir.mkdirs()
            if (!whisperDir.exists()) whisperDir.mkdirs()
            if (!llmDir.exists()) llmDir.mkdirs()
            modelsRootDir.exists() && modelsRootDir.canWrite()
        } catch (_: Exception) {
            false
        }
    }

    fun formatGemmaPrompt(
        question: String,
        configOverride: LlmGenerationConfig = _llmConfig.value,
        ragContext: String = ""
    ): String {
        val trimmed = question.trim().take(2000)
        val contextTrimmed = configOverride.interviewContext.trim().take(500)
        val safeRagContext = ragContext.trim().take(1500)

        val contextSection = when {
            contextTrimmed.isNotBlank() && safeRagContext.isNotBlank() -> {
                "<interview_context>\nInterview Context (Role, Program & Domain): $contextTrimmed\n\nTailored Reference Context:\n$safeRagContext\n</interview_context>\n" +
                "$CANDIDATE_RAG_GROUNDING_DIRECTIVE\n"
            }
            contextTrimmed.isNotBlank() -> {
                "<interview_context>\nInterview Context (Role, Program & Domain): $contextTrimmed\n</interview_context>\n"
            }
            safeRagContext.isNotBlank() -> {
                "<interview_context>\nTailored Reference Context:\n$safeRagContext\n</interview_context>\n" +
                "$CANDIDATE_RAG_GROUNDING_DIRECTIVE\n"
            }
            else -> ""
        }
        return "<start_of_turn>user\n" +
               "$SYSTEM_SAFETY_GUARDRAIL\n" +
               "${configOverride.promptStyle.systemInstruction}\n" +
               contextSection +
               "Question: $trimmed<end_of_turn>\n" +
               "<start_of_turn>model\n"
    }

    /**
     * Builds the system prompt for live LiteRT-LM conversations with automatic role/persona adaptation:
     * 1. If RAG (Resume / JD) and/or Interview Context is provided -> adopts that exact candidate role & background.
     * 2. If NO context or resume is provided -> dynamically infers the target profession/role from the question
     *    (with #1 principal-level specialization for Software Engineering & CS, and practitioner-grade STAR rigor across all professions).
     */
    fun buildEffectiveSystemPrompt(
        question: String,
        configOverride: LlmGenerationConfig = _llmConfig.value,
        ragContext: String = ""
    ): String {
        val base = "$SYSTEM_SAFETY_GUARDRAIL\n${configOverride.promptStyle.systemInstruction}"
        val contextTrimmed = configOverride.interviewContext.trim().take(500)
        val safeRag = ragContext.trim().take(1500)

        val contextSection = when {
            contextTrimmed.isNotBlank() && safeRag.isNotBlank() -> {
                "<interview_context>\nInterview Context (Role, Program & Domain): $contextTrimmed\n\nTailored Reference Context:\n$safeRag\n</interview_context>\n$CANDIDATE_RAG_GROUNDING_DIRECTIVE"
            }
            safeRag.isNotBlank() -> {
                "<interview_context>\nTailored Reference Context:\n$safeRag\n</interview_context>\n$CANDIDATE_RAG_GROUNDING_DIRECTIVE"
            }
            contextTrimmed.isNotBlank() -> {
                "<interview_context>\nInterview Context (Role, Program & Domain): $contextTrimmed\n</interview_context>\n$EXPLICIT_ROLE_GROUNDING_DIRECTIVE"
            }
            else -> {
                val inferredPersona = inferPersonaFromQuestion(question)
                "<inferred_persona>\nInferred Role & Domain Persona: $inferredPersona\n</inferred_persona>\n$ZERO_CONTEXT_ADAPTIVE_ROLE_DIRECTIVE"
            }
        }
        return "$base\n$contextSection"
    }

    /**
     * Builds the user turn payload for live LiteRT-LM conversations.
     * Avoids duplicating the full RAG resume block (which is already in the system prompt) so the model
     * focuses on answering the interviewer's question naturally instead of reciting a resume monologue.
     */
    fun buildEffectiveUserTurn(
        question: String,
        configOverride: LlmGenerationConfig = _llmConfig.value,
        ragContext: String = ""
    ): String {
        val trimmedQuestion = question.trim().take(2000)
        val contextTrimmed = configOverride.interviewContext.trim().take(500)
        val safeRag = ragContext.trim().take(1500)

        return when {
            safeRag.isNotBlank() -> {
                val deliveryCue = buildConversationalDeliveryCue(trimmedQuestion, hasRag = true)
                "$deliveryCue\n\nInterviewer Question: $trimmedQuestion"
            }
            contextTrimmed.isNotBlank() -> {
                val deliveryCue = buildConversationalDeliveryCue(trimmedQuestion, hasRag = false)
                "<interview_context>\nTarget Role & Context: $contextTrimmed\n</interview_context>\n" +
                "$deliveryCue\n\n" +
                "Interviewer Question: $trimmedQuestion"
            }
            else -> {
                val inferredPersona = inferPersonaFromQuestion(trimmedQuestion)
                val deliveryCue = buildConversationalDeliveryCue(trimmedQuestion, hasRag = false)
                "<inferred_persona>\nInferred Role & Domain Persona: $inferredPersona\n</inferred_persona>\n" +
                "$deliveryCue\n\n" +
                "Interviewer Question: $trimmedQuestion"
            }
        }
    }

    data class ModelFileStatus(
        val name: String, 
        val absolutePath: String, 
        val exists: Boolean, 
        val sizeBytes: Long,
    )

    data class ModelValidationReport(val files: List<ModelFileStatus>) {
        val allPresent: Boolean
            get() = files.all { (_, _, exists, sizeBytes) -> exists && (sizeBytes > 0L) }
    }

    fun validateModelsOnDisk(): ModelValidationReport {
        val requiredFiles = listOf(
            vadModelFile,
            sttEncoderFile,
            sttDecoderFile,
            sttJoinerFile,
            sttTokensFile,
            activeLlmModelFile,
        )
        
        val statuses = requiredFiles.map { file ->
            ModelFileStatus(
                name = file.name,
                absolutePath = file.absolutePath,
                exists = file.exists(),
                sizeBytes = if (file.exists()) file.length() else 0L,
            )
        }
        
        return ModelValidationReport(statuses)
    }

    /**
     * Purges all locally downloaded AI model weights, tokenizers, and caches from disk.
     * Reclaims ~3.1 GB of internal/external device storage.
     * Returns true if all files were purged or did not exist.
     */
    fun purgeDownloadedModels(): Boolean {
        var allDeleted = true
        val targetDirs = listOf(vadDir, asrDir, whisperDir, llmDir)
        for (dir in targetDirs) {
            if (dir.exists()) {
                val success = dir.deleteRecursively()
                if (!success) allDeleted = false
            }
        }
        val legacyFiles = listOf(
            File(modelsRootDir, "silero_vad.onnx"),
            File(modelsRootDir, "encoder.onnx"),
            File(modelsRootDir, "decoder.onnx"),
            File(modelsRootDir, "joiner.onnx"),
            File(modelsRootDir, "tokens.txt"),
            File(modelsRootDir, "tiny.en-encoder.int8.onnx"),
            File(modelsRootDir, "tiny.en-decoder.int8.onnx"),
            File(modelsRootDir, "tiny.en-tokens.txt"),
            File(modelsRootDir, "gemma-4-E2B-it.litertlm"),
            File(modelsRootDir, ".stt_version"),
            File(modelsRootDir, "hotwords.txt")
        )
        for (file in legacyFiles) {
            if (file.exists()) {
                val success = file.delete()
                if (!success) allDeleted = false
            }
        }
        ensureDirectoriesExist()
        return allDeleted
    }

    companion object {
        const val SYSTEM_SAFETY_GUARDRAIL =
            "Provide professional, truthful, and helpful responses to interview questions. " +
            "Answer the interview question directly and clearly. " +
            "Do not generate hate speech, harassment, sexually explicit content, dangerous advice, " +
            "or confidential proprietary data. Decline out-of-scope or unethical requests politely."

        const val CANDIDATE_RAG_GROUNDING_DIRECTIVE =
            "NATURAL CONVERSATIONAL & RESUME GROUNDING RULE: You are the candidate in [Candidate CV] interviewing for [Target Job]. Speak naturally and conversationally in the first person (\"I\", \"my\")—NEVER sound like you are reading a resume or delivering a rehearsed monologue:\n" +
            "1. **Answer the Question First (80% Substance / 20% Resume Tie-In):** Treat your [Candidate CV] as background memory, not a script to recite. Do NOT list multiple jobs, company names, or degrees unless the interviewer explicitly asks about your background.\n" +
            "2. **Certifications, Licensure & Verified Credentials:** When the interviewer asks about your qualifications, certifications, licenses, training, or background walkthroughs, explicitly cite the exact certification names, issuing bodies, or license credentials from your [Candidate CV] (e.g. state license, board certifications, cloud/specialty credentials) with professional confidence.\n" +
            "3. **Match Structure to the Question Type:**\n" +
            "   - *Technical, Architectural, or Domain 'How-To' Questions:* Lead immediately with direct practitioner know-how, mechanisms, workflows, and trade-offs. Weave in at most ONE brief, natural reference to a relevant project, certification, or experience from your CV as a concrete example.\n" +
            "   - *Behavioral or 'Walk me through an instance/project' Questions:* Share ONE focused example using a natural spoken STAR flow (• **Situation:** 1 brief sentence of context; • **Task:** the core challenge; • **Action:** 70% of your answer detailing your hands-on execution, decisions, and trade-offs; • **Result:** the concrete outcome).\n" +
            "   - *'Tell me about yourself' / Background Walkthroughs:* Deliver a warm, natural spoken narrative connecting your current work, key achievements, verified certifications/credentials, and academic foundation to the role without stiff labels."

        /**
         * Generates a concise, question-specific delivery cue so the LLM responds naturally to the exact question type
         * instead of defaulting to a repetitive resume monologue.
         */
        fun buildConversationalDeliveryCue(question: String, hasRag: Boolean): String {
            val lower = question.lowercase()
            val isIntroOrBackground = lower.contains("tell me about yourself") ||
                    lower.contains("tell us about yourself") ||
                    lower.contains("walk me through your background") ||
                    lower.contains("walk me through your resume") ||
                    lower.contains("walk me through your career") ||
                    lower.contains("your career path") ||
                    lower.contains("introduce yourself")

            val isBehavioralOrProjectStory = lower.contains("describe a situation") ||
                    lower.contains("describe a time") ||
                    lower.contains("tell me about a time") ||
                    lower.contains("specific instance") ||
                    lower.contains("walk me through an end-to-end project") ||
                    lower.contains("walk me through a project") ||
                    lower.contains("conflict")

            val isQualificationOrCert = lower.contains("certif") ||
                    lower.contains("license") ||
                    lower.contains("licensure") ||
                    lower.contains("credential") ||
                    lower.contains("qualification") ||
                    lower.contains("accredit")

            return when {
                isQualificationOrCert && hasRag ->
                    "DELIVERY NOTE: Answer directly and conversationally, highlighting your verified licenses, certifications, and credentials from your CV alongside relevant practical experience and training."

                isIntroOrBackground && hasRag ->
                    "DELIVERY NOTE: This is a background introduction question. Speak naturally and conversationally in 3 concise bullet points covering (1) your current role and core focus, (2) a standout achievement/project relevant to the target job, and (3) your academic progression, certifications/credentials, and why this role fits. Sound human and engaging, not like a bulleted resume printout."

                isBehavioralOrProjectStory && hasRag ->
                    "DELIVERY NOTE: Pick ONE specific example from your background and answer naturally using • **Situation:** (keep setup to 1 short sentence—do not recite your resume), • **Task:** (the core problem), • **Action:** (spend 70% of your answer explaining HOW you solved it, the technical/domain mechanisms, and trade-offs), and • **Result:** (the measurable impact)."

                hasRag ->
                    "DELIVERY NOTE: Answer the interviewer's question directly and conversationally first—focus 80% of your response on the practical know-how, architecture/workflow, and trade-offs they asked about. Weave in at most one brief, natural touchpoint from your experience without turning the answer into a resume monologue."

                isBehavioralOrProjectStory ->
                    "DELIVERY NOTE: Answer in the first person using a natural STAR flow (• **Situation:** 1 short sentence, • **Task:** core objective, • **Action:** deep practitioner execution & trade-offs, • **Result:** measurable impact)."

                else ->
                    "DELIVERY NOTE: Answer directly, naturally, and with deep practitioner know-how—explaining concrete mechanisms, workflows, and trade-offs as a seasoned expert speaking in a real interview."
            }
        }

        const val EXPLICIT_ROLE_GROUNDING_DIRECTIVE =
            "EXPLICIT ROLE & STAR ANSWERING RULE: Embody the exact role, profession, and domain specified in <interview_context> and answer in the first person (\"I\", \"my\"). " +
            "For behavioral, situational, or experience questions, structure your response using the STAR framework (• **Situation:**, • **Task:**, • **Action:**, • **Result:**) with concrete practitioner workflows and measurable outcomes. " +
            "For software engineering, computer science, or system design questions, prioritize principal-level technical rigor—explaining underlying mechanisms, algorithms, concurrency/memory models, system architecture, and production trade-offs."

        const val ZERO_CONTEXT_ADAPTIVE_ROLE_DIRECTIVE =
            "ADAPTIVE PERSONA & ROLE INFERENCE RULE: No resume or explicit role context was provided. " +
            "Adopt the inferred practitioner persona in <inferred_persona> (or infer the exact profession, industry, and seniority level directly from the interviewer's question) and respond in the first person (\"I\", \"my\") as a seasoned expert in that field. " +
            "For behavioral, situational, or case-study questions, structure your response using the 4-bullet STAR framework (• **Situation:**, • **Task:**, • **Action:**, • **Result:**) with domain-authentic workflows, standards, and measurable results. " +
            "For software engineering, machine learning, or system design questions, deliver principal-level technical depth—explicitly detailing internal mechanisms, data structures, concurrency/memory/latency trade-offs, and production architecture."

        const val HOTWORDS_VERSION = "2026-11-v2-universal"

        /**
         * Zero-latency (<0.1 ms) domain and persona inference when no explicit resume or role context is configured.
         * Keeps Software Engineering #1 in specialized technical rigor while adapting dynamically to any profession.
         */
        fun inferPersonaFromQuestion(question: String): String {
            val lower = question.lowercase()
            return when {
                containsAny(
                    lower,
                    "kotlin", "java", "python", "javascript", "typescript", "c++", "rust", "golang",
                    "coroutine", "stateflow", "compose", "android", "viewmodel", "jni", "sdk",
                    "api", "rest", "graphql", "grpc", "microservice", "docker", "kubernetes",
                    "database", "sql", "postgres", "redis", "kafka", "distributed", "sharding",
                    "latency", "throughput", "concurrency", "multithreading", "mutex", "deadlock",
                    "garbage collection", "memory leak", "heap", "algorithm", "complexity",
                    "pytorch", "tensorflow", "scikit", "llm", "rag", "transformer", "mlops",
                    "ci/cd", "git", "unit test", "refactor", "dependency injection", "system design",
                    "architecture", "code", "compiler", "backend", "frontend", "fullstack", "devops"
                ) -> "Staff / Principal Software & Systems Engineer (deliver deep architectural mechanisms, algorithms, concurrency/memory/latency trade-offs, and production-grade engineering rigor)"

                containsAny(
                    lower,
                    "patient", "clinical", "hospital", "inpatient", "outpatient", "triage",
                    "diagnosis", "treatment", "physician", "doctor", "nurse", "nursing", "icu",
                    "emergency", "discharge", "biopsychosocial", "palliative", "hipaa", "ehr",
                    "epic", "medicaid", "medicare", "pharmacology", "surgery", "pathology",
                    "social work", "lcsw", "psychiatric", "de-escalation", "bereavement"
                ) -> "Senior Healthcare Clinician / Physician / Medical Specialist (ground answer in evidence-based clinical protocols, patient safety, interdisciplinary care coordination, and HIPAA/CMS compliance)"

                containsAny(
                    lower,
                    "classroom", "student", "lesson plan", "pedagogy", "curriculum", "teaching",
                    "teacher", "iep", "504", "ferpa", "rubric", "formative", "summative",
                    "differentiated instruction", "special education", "parent-teacher", "grade level",
                    "school district", "principal", "literacy", "scaffolding"
                ) -> "Lead Educator / Classroom Teacher (ground answer in differentiated pedagogy, instructional design, classroom management, IEP/504 accommodations, and measurable student growth)"

                containsAny(
                    lower,
                    "litigation", "contract", "statute", "precedent", "deposition", "discovery",
                    "court", "judge", "jury", "counsel", "attorney", "lawyer", "legal",
                    "fiduciary", "indemnification", "arbitration", "mediation", "jurisdiction",
                    "brief", "motion", "pleading", "regulatory", "nda", "due diligence"
                ) -> "Senior Legal Counsel / Litigation & Corporate Attorney (ground answer in statutory/contractual precision, precedent analysis, risk mitigation, fiduciary duty, and client advocacy)"

                containsAny(
                    lower,
                    "pcr", "qpcr", "assay", "sequencing", "crispr", "dna", "rna", "protein",
                    "cell culture", "microscopy", "chromatography", "spectroscopy", "reagent",
                    "specimen", "laboratory", "wet lab", "biologist", "biology", "molecular",
                    "genetics", "microbiology", "biochemistry", "western blot", "elisa"
                ) -> "Senior Research Biologist / Life Sciences Scientist (ground answer in experimental controls, assay validation, protocol reproducibility, biosafety/GLP standards, and quantitative data analysis)"

                containsAny(
                    lower,
                    "zoning", "building code", "bim", "revit", "autocad", "cad", "leed",
                    "schematic design", "floor plan", "structural", "elevation", "facade",
                    "construction administration", "site analysis", "hvac", "mep", "blueprint",
                    "urban planning", "spatial"
                ) -> "Licensed Senior Architect / Design Principal (ground answer in spatial & structural systems, building code/ADA compliance, BIM/Revit coordination, sustainability/LEED, and construction administration)"

                containsAny(
                    lower,
                    "athlete", "coaching", "coach", "practice plan", "game plan", "scouting",
                    "conditioning", "roster", "locker room", "halftime", "sportsmanship",
                    "playoff", "tournament", "player development", "varsity", "Athletic"
                ) -> "Head Athletic / High-Performance Coach (ground answer in player development, tactical preparation, biomechanical/conditioning safety, team culture, and competitive execution)"

                containsAny(
                    lower,
                    "executive assistant", "secretary", "calendar management", "itinerary",
                    "travel arrangement", "inbox", "gatekeeping", "board meeting", "minutes",
                    "office management", "front desk", "reception", "correspondence", "c-suite",
                    "expense report", "scheduling conflict"
                ) -> "Senior Executive Assistant / Office Operations Manager (ground answer in C-suite calendar & inbox prioritization, diplomatic gatekeeping, strict confidentiality, logistics precision, and operational efficiency)"

                containsAny(
                    lower,
                    "admissions", "undergraduate", "graduate school", "scholarship", "dissertation",
                    "thesis", "gpa", "coursework", "capstone", "campus", "extracurricular",
                    "internship", "fellowship", "major", "faculty", "semester"
                ) -> "High-Achieving Academic / University Candidate (ground answer in rigorous coursework, research methodology, intellectual curiosity, collaborative capstone/lab execution, and academic impact)"

                containsAny(
                    lower,
                    "ebitda", "valuation", "balance sheet", "cash flow", "p&l", "gaap",
                    "financial model", "roi", "unit economics", "roadmap", "kpi", "okr",
                    "go-to-market", "churn", "arr", "mrr", "audit", "cpa", "portfolio"
                ) -> "Senior Finance & Product Strategy Leader (ground answer in quantitative financial modeling, unit economics, risk-adjusted ROI, stakeholder alignment, and measurable business outcomes)"

                else -> "Infer the exact target profession, industry, and seniority level directly from the interviewer's question and respond as a seasoned, high-performing practitioner in that exact role"
            }
        }

        private fun containsAny(lowerText: String, vararg terms: String): Boolean {
            for (term in terms) {
                if (term.length <= 3) {
                    if (Regex("""\b${Regex.escape(term)}\b""").containsMatchIn(lowerText)) return true
                } else {
                    if (lowerText.contains(term)) return true
                }
            }
            return false
        }

        const val VAD_URL = "https://huggingface.co/deepghs/silero-vad-onnx/resolve/main/silero_vad.onnx"
        const val STT_MODEL_VERSION = "2023-06-21-gigaspeech"
        const val STT_ENCODER_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-21/resolve/main/encoder-epoch-99-avg-1.int8.onnx"
        const val STT_DECODER_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-21/resolve/main/decoder-epoch-99-avg-1.onnx"
        const val STT_JOINER_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-21/resolve/main/joiner-epoch-99-avg-1.int8.onnx"
        const val STT_TOKENS_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-21/resolve/main/tokens.txt"
        const val WHISPER_TINY_ENCODER_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-encoder.int8.onnx"
        const val WHISPER_TINY_DECODER_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-decoder.int8.onnx"
        const val WHISPER_TINY_TOKENS_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-tokens.txt"
        const val DEFAULT_LLM_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val DEFAULT_TPU_LLM_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_Google_Tensor_G5.litertlm"

        // Cryptographic SHA-256 Checksums and Expected Byte Sizes (Requirement R4, P0-08)
        const val VAD_SHA256 = "2623a2953f6ff3d2c1e61740c6cdb7168133479b267dfef114a4a3cc5bdd788f"
        const val VAD_BYTES = 2_327_524L

        const val STT_ENCODER_SHA256 = "32c98281c7bd8b63e3e142d007251b37f120572e8fdea9a4f5a79ce22b10ec4f"
        const val STT_ENCODER_BYTES = 187_823_992L

        const val STT_DECODER_SHA256 = "9da02b77cb08826756ec6a88635f35a40374e4164e7c6359121a9145958a6ceb"
        const val STT_DECODER_BYTES = 2_092_566L

        const val STT_JOINER_SHA256 = "831477d390e59a61f1b6a6f763b9903e6c6366ff6034f1ddba613be82637122f"
        const val STT_JOINER_BYTES = 259_335L

        const val STT_TOKENS_SHA256 = "49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb"
        const val STT_TOKENS_BYTES = 5_048L

        const val WHISPER_TINY_ENCODER_SHA256 = "0ce578b827c94a961aacb8fa14b02f096504b337e5c94be37c36238cbe3e8bc6"
        const val WHISPER_TINY_ENCODER_BYTES = 12_937_772L

        const val WHISPER_TINY_DECODER_SHA256 = "06c0e6ff6348d427e51839219d1c886c18cfdf411e629e33f5e1679bff9c1527"
        const val WHISPER_TINY_DECODER_BYTES = 89_853_865L

        const val WHISPER_TINY_TOKENS_SHA256 = "306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930"
        const val WHISPER_TINY_TOKENS_BYTES = 835_554L

        const val GEMMA_GPU_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        const val GEMMA_GPU_BYTES = 2_588_147_712L

        const val GEMMA_TPU_G5_SHA256 = "af1082986639ecde7db95d91be6fe54f8b6b458104734c5bafc204e69d6852dc"
        const val GEMMA_TPU_G5_BYTES = 3_113_545_589L

        const val GEMMA_TPU_G6_SHA256 = "f86c7c19c736e9307267946edef58b0a45494a9127036d54d90dd6b7617c95b7"
        const val GEMMA_TPU_G6_BYTES = 3_313_938_293L

        const val EXPECTED_GPU_LLM_BYTES = GEMMA_GPU_BYTES
        const val EXPECTED_TPU_LLM_BYTES = GEMMA_TPU_G5_BYTES

        fun resolveLlmSha256(fileName: String): String {
            return when {
                fileName.contains("Tensor_G6", ignoreCase = true) -> GEMMA_TPU_G6_SHA256
                fileName.contains("Tensor_G5", ignoreCase = true) || fileName.contains("Tensor", ignoreCase = true) -> GEMMA_TPU_G5_SHA256
                else -> GEMMA_GPU_SHA256
            }
        }

        fun resolveLlmBytes(fileName: String): Long {
            return when {
                fileName.contains("Tensor_G6", ignoreCase = true) -> GEMMA_TPU_G6_BYTES
                fileName.contains("Tensor_G5", ignoreCase = true) || fileName.contains("Tensor", ignoreCase = true) -> GEMMA_TPU_G5_BYTES
                else -> GEMMA_GPU_BYTES
            }
        }

        /** LiteRT-LM is the only LLM runtime; it loads `.litertlm` bundles exclusively. */
        const val LLM_MODEL_EXTENSION = ".litertlm"

        /** Official Public Privacy Policy URL (hosted on verified public HTTPS domain via GitHub Pages) */
        const val PRIVACY_POLICY_URL = "https://eduardoben.github.io/ai-interview/privacy.html"
        const val PRIVACY_POLICY_MIRROR_URL = "https://aiinterviewassistant.app/privacy"
        const val FEEDBACK_EMAIL = "hbenitez@hectisai.com"

        fun isSupportedLlmModel(file: File): Boolean = isSupportedLlmModelName(file.name)

        fun isSupportedLlmModelName(name: String): Boolean =
            name.endsWith(LLM_MODEL_EXTENSION, ignoreCase = true)
    }
}

