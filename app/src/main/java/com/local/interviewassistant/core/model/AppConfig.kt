package com.local.interviewassistant.core.model

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Suppress("unused")
class AppConfig(val modelsRootDir: File) {
    val vadModelFile = File(modelsRootDir, "silero_vad.onnx")
    val sttEncoderFile = File(modelsRootDir, "encoder.onnx")
    val sttDecoderFile = File(modelsRootDir, "decoder.onnx")
    val sttJoinerFile = File(modelsRootDir, "joiner.onnx")
    val sttTokensFile = File(modelsRootDir, "tokens.txt")
    val sttModelVersionFile = File(modelsRootDir, ".stt_version")
    val hotwordsFile = File(modelsRootDir, "hotwords.txt")
    val offlineWhisperEncoderFile = File(modelsRootDir, "tiny.en-encoder.int8.onnx")
    val offlineWhisperDecoderFile = File(modelsRootDir, "tiny.en-decoder.int8.onnx")
    val offlineWhisperTokensFile = File(modelsRootDir, "tiny.en-tokens.txt")
    val llmModelFile = File(modelsRootDir, "gemma-4-E2B-it.litertlm")
    val tpuLlmModelFile = File(modelsRootDir, resolveTensorModelFileName())

    fun hasOfflineRescorer(): Boolean {
        return offlineWhisperEncoderFile.exists() && offlineWhisperEncoderFile.length() > 0L &&
                offlineWhisperDecoderFile.exists() && offlineWhisperDecoderFile.length() > 0L &&
                offlineWhisperTokensFile.exists() && offlineWhisperTokensFile.length() > 0L
    }

    fun resolveTensorModelFileName(): String {
        val soc = try { android.os.Build.SOC_MODEL.lowercase().trim() } catch (_: Throwable) { "" }
        val device = try { android.os.Build.MODEL.lowercase().trim() } catch (_: Throwable) { "" }
        return when {
            soc.contains("g5") || device.contains("pixel 10") -> "gemma-4-E2B-it_Google_Tensor_G5.litertlm"
            soc.contains("g4") || device.contains("pixel 9") -> "gemma-4-E2B-it_Google_Tensor_G4.litertlm"
            soc.contains("g3") || device.contains("pixel 8") -> "gemma-4-E2B-it_Google_Tensor_G3.litertlm"
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
            return if (path.isNotBlank()) File(path) else llmModelFile
        }

    fun discoverLocalModels(): List<File> {
        val extensions = listOf(".litertlm", ".bin", ".task", ".tflite")
        if (!modelsRootDir.exists() || !modelsRootDir.isDirectory) return emptyList()
        return modelsRootDir.walkTopDown()
            .filter { file -> file.isFile && extensions.any { ext -> file.name.endsWith(ext, ignoreCase = true) } && file.length() > 0L }
            .toList()
    }

    val sampleRateHz: Int = 16000
    val vadWindowSizeSamples: Int = 512
    val vadThreshold: Float = 0.5f
    val minSilenceDurationSec: Float = 0.6f
    val minSpeechDurationSec: Float = 0.25f
    val minQuestionLengthChars: Int = 12

    // STT Decoding & Beam Search Configuration (v1.1)
    val sttDecodingMethod: String = "modified_beam_search"
    val sttMaxActivePaths: Int = 4
    val sttHotwordsScore: Float = 2.0f
    val sttBlankPenalty: Float = 1.0f
    val enableHardwareAudioEffects: Boolean = false

    private val _llmConfig = MutableStateFlow(LlmGenerationConfig())
    val llmConfig: StateFlow<LlmGenerationConfig> = _llmConfig

    val maxTokens: Int get() = _llmConfig.value.maxTokens
    val temperature: Float get() = _llmConfig.value.temperature
    val topK: Int get() = _llmConfig.value.topK
    val useGpuDelegate: Boolean = true

    fun updateLlmConfig(newConfig: LlmGenerationConfig) {
        _llmConfig.value = newConfig.validated()
    }

    fun ensureDirectoriesExist(): Boolean {
        return try {
            if (!modelsRootDir.exists()) {
                modelsRootDir.mkdirs()
            }
            modelsRootDir.exists() && modelsRootDir.canWrite()
        } catch (_: Exception) {
            false
        }
    }

    fun formatGemmaPrompt(question: String, configOverride: LlmGenerationConfig = _llmConfig.value): String {
        val trimmed = question.trim().take(2000)
        val contextTrimmed = configOverride.interviewContext.trim().take(500)
        val contextLine = if (contextTrimmed.isNotBlank()) {
            "Interview Context (Role, Program & Domain): $contextTrimmed\n"
        } else {
            ""
        }
        return "<start_of_turn>user\n" +
               "You are an expert candidate assisting in an interview. ${configOverride.promptStyle.systemInstruction}\n" +
               contextLine +
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

    companion object {
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
    }
}
