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
    val hotwordsFile = File(modelsRootDir, "hotwords.txt")
    val llmModelFile = File(modelsRootDir, "gemma-4-E2B-it.litertlm")
    val tpuLlmModelFile = File(modelsRootDir, resolveTensorModelFileName())

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

    fun ensureHotwordsFile(): File {
        if (!hotwordsFile.exists() || hotwordsFile.length() <= 0L) {
            val keywords = listOf(
                // General, Behavioral & School / University Admissions
                "BEHAVIORAL", "STRENGTHS", "WEAKNESSES", "LEADERSHIP", "ACHIEVEMENT", "CHALLENGE",
                "CONFLICT", "RESOLUTION", "STAR", "SITUATION", "TASK", "ACTION", "RESULT",
                "MOTIVATION", "COLLABORATION", "COMMUNICATION", "TEAMWORK", "ACADEMIC", "ADMISSIONS",
                "RESEARCH", "CURRICULUM", "THESIS", "UNDERGRADUATE", "GRADUATE", "DISSERTATION",
                "EXTRACURRICULAR", "EXPERIENCE", "PROJECT", "MANAGEMENT", "ORGANIZATION",
                
                // Software, Engineering & Computer Science
                "KOTLIN", "JAVA", "COROUTINES", "STATEFLOW", "SHAREDFLOW", "MUTABLESTATEFLOW",
                "VIEWMODEL", "LIFECYCLE", "JETPACK", "COMPOSE", "DEPENDENCY", "INJECTION",
                "HILT", "DAGGER", "ROOM", "RETROFIT", "OKHTTP", "SERIALIZATION",
                "POLYMORPHISM", "INHERITANCE", "ENCAPSULATION", "ABSTRACTION", "INTERFACE",
                "MULTITHREADING", "CONCURRENCY", "SYNCHRONIZED", "VOLATILE", "MUTEX",
                "DISPATCHERS", "MAIN", "DEFAULT", "IO", "UNCONFINED", "SUSPEND",
                "SOLID", "SINGLETON", "FACTORY", "OBSERVER", "REPOSITORY", "ARCHITECTURE",
                "MVVM", "MVI", "CLEAN", "TESTING", "JUNIT", "MOCKK", "ESPRESSO",
                "PLAYWRIGHT", "SELENIUM", "DOCKER", "KUBERNETES", "FASTAPI", "SPRING",
                "BOOT", "PYTHON", "TYPESCRIPT", "JAVASCRIPT", "REACT", "ANGULAR",
                "POSTGRESQL", "MYSQL", "MONGODB", "REDIS", "GRAPHQL", "REST", "API",
                "MICROSERVICES", "KAFKA", "RABBITMQ", "CI", "CD", "GARBAGE", "COLLECTION",
                "HEAP", "STACK", "MEMORY", "LEAK", "ALGORITHMS", "DATASTRUCTURES",

                // Product, Business & Operations
                "ROADMAP", "STAKEHOLDER", "METRICS", "KPI", "KPIS", "ANALYTICS",
                "USEREXPERIENCE", "PRIORITIZATION", "AGILE", "SCRUM", "STRATEGY",
                "OPPORTUNITY", "MARKET", "COMPETITOR", "REVENUE", "GROWTH",

                // Finance, Economics & Consulting
                "VALUATION", "EBITDA", "PROFITABILITY", "ROI", "BALANCESHEET",
                "INCOMESTATEMENT", "CASHFLOW", "PORTFOLIO", "FINANCIAL", "MODELING",
                "CAPITAL", "ASSETS", "EQUITY", "MARGIN",

                // Medicine, Healthcare & Sciences
                "CLINICAL", "PATIENT", "DIAGNOSIS", "TREATMENT", "PATHOLOGY",
                "PHARMACOLOGY", "ANATOMY", "BIOLOGY", "ONCOLOGY", "CARDIOLOGY",
                "SURGERY", "PRACTICE",
            )
            try {
                hotwordsFile.writeText(keywords.joinToString("\n"))
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
        const val STT_ENCODER_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/resolve/main/encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
        const val STT_DECODER_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/resolve/main/decoder-epoch-99-avg-1-chunk-16-left-128.onnx"
        const val STT_JOINER_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/resolve/main/joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
        const val STT_TOKENS_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/resolve/main/tokens.txt"
        const val DEFAULT_LLM_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val DEFAULT_TPU_LLM_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_Google_Tensor_G5.litertlm"
    }
}
