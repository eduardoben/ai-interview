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
        val needsRefresh = forceRefresh || !hotwordsFile.exists() || hotwordsFile.length() <= 0L ||
                try { hotwordsFile.readText().any { it.isUpperCase() } } catch (_: Throwable) { true }

        if (needsRefresh) {
            val keywords = listOf(
                // General, Behavioral & School / University Admissions
                "behavioral", "strengths", "weaknesses", "leadership", "achievement", "challenge",
                "conflict", "resolution", "star", "situation", "task", "action", "result",
                "motivation", "collaboration", "communication", "teamwork", "academic", "admissions",
                "research", "curriculum", "thesis", "undergraduate", "graduate", "dissertation",
                "extracurricular", "experience", "project", "management", "organization",
                
                // Software, Engineering & Computer Science
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

                // Product, Business & Operations
                "roadmap", "stakeholder", "metrics", "kpi", "kpis", "analytics",
                "user experience", "prioritization", "agile", "scrum", "strategy",
                "opportunity", "market", "competitor", "revenue", "growth",

                // Finance, Economics & Consulting
                "valuation", "ebitda", "profitability", "roi", "balance sheet",
                "income statement", "cash flow", "portfolio", "financial modeling",
                "capital", "assets", "equity", "margin",

                // Medicine, Healthcare & Sciences
                "clinical", "patient", "diagnosis", "treatment", "pathology",
                "pharmacology", "anatomy", "biology", "oncology", "cardiology",
                "surgery", "practice",

                // Common Question & Interview conversational patterns
                "tell me about", "what is the difference between",
                "how do you handle", "can you explain", "walk me through",
                "how does", "what are the advantages of", "why would you choose",
                "what happens when", "how would you design"
            )
            try {
                hotwordsFile.writeText(keywords.map { it.trim().lowercase() }.filter { it.isNotBlank() }.joinToString("\n"))
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
        val safeRagContext = ragContext.trim().take(1200)

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
            "GROUNDING & ANSWERING RULE: You are the candidate in the [Candidate CV]. Answer in the first person (\"I\", \"my\"). " +
            "Directly answer technical, architectural, and specialized domain questions with deep engineering substance, " +
            "applying your expertise and citing relevant projects, tools, frameworks, and metrics from your [Candidate CV] " +
            "and aligning to the [Target Job]. Do not give a resume recap when a technical concept, tradeoff, or architecture is asked."

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

