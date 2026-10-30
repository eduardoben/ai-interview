package com.local.interviewassistant.data.stt

import java.util.Locale

/**
 * High-performance, on-device truecasing and punctuation restoration engine (Phase 3).
 *
 * Restores:
 * 1. Capitalization for initial sentence words and words after punctuation (. ? !).
 * 2. Proper casing for 100+ technical, behavioral, and domain acronyms and proper nouns
 *    (e.g., "kotlin" -> "Kotlin", "api" -> "API", "rest" -> "REST", "json" -> "JSON", "sql" -> "SQL").
 * 3. Terminal question punctuation ('?') for interrogative sentences and prompt imperatives.
 * 4. Terminal period punctuation ('.') for declarative statements.
 * 5. English contractions ("whats" -> "What's", "dont" -> "don't", etc.).
 */
object QuestionPunctuationEngine {

    // Technical acronyms that must always be uppercase
    private val ACRONYMS_UPPER = setOf(
        "API", "APIS", "REST", "HTTP", "HTTPS", "JSON", "SQL", "HTML", "CSS", "XML",
        "JVM", "JNI", "SDK", "NDK", "AAR", "APK", "JAR", "URL", "URI", "UUID",
        "CPU", "GPU", "TPU", "NPU", "RAM", "ROM", "SSD", "HDD", "IO", "I/O",
        "TCP", "UDP", "IP", "DNS", "SSH", "SSL", "TLS", "IPC", "RPC", "GRPC",
        "MVVM", "MVI", "MVP", "MVC", "OOP", "SOLID", "DRY", "KISS", "YAGNI",
        "DI", "CI", "CD", "CI/CD", "AWS", "GCP", "BPE", "VAD", "STT", "TTS", "ASR", "LLM",
        "STAR", "KPI", "KPIS", "ROI", "EBITDA", "P&L", "OKRS", "OKR", "SLA", "SLO", "SLI",
        "QA", "UI", "UX", "PR", "OS", "IDE", "DB", "RDBMS", "ACID", "BASE", "ORM",
        "AIDL", "HAL", "OOM", "ANR", "FPS", "TTFT", "TPS"
    )

    // Proper nouns with specific casing
    private val PROPER_NOUNS = mapOf(
        "kotlin" to "Kotlin",
        "java" to "Java",
        "python" to "Python",
        "javascript" to "JavaScript",
        "typescript" to "TypeScript",
        "rust" to "Rust",
        "golang" to "Go",
        "swift" to "Swift",
        "c++" to "C++",
        "c#" to "C#",
        "android" to "Android",
        "pixel" to "Pixel",
        "google" to "Google",
        "apple" to "Apple",
        "linux" to "Linux",
        "coroutine" to "Coroutine",
        "coroutines" to "Coroutines",
        "stateflow" to "StateFlow",
        "sharedflow" to "SharedFlow",
        "mutablestateflow" to "MutableStateFlow",
        "mutablesharedflow" to "MutableSharedFlow",
        "viewmodel" to "ViewModel",
        "viewmodels" to "ViewModels",
        "compose" to "Compose",
        "jetpack" to "Jetpack",
        "room" to "Room",
        "retrofit" to "Retrofit",
        "okhttp" to "OkHttp",
        "dagger" to "Dagger",
        "hilt" to "Hilt",
        "koin" to "Koin",
        "docker" to "Docker",
        "kubernetes" to "Kubernetes",
        "k8s" to "K8s",
        "kafka" to "Kafka",
        "rabbitmq" to "RabbitMQ",
        "redis" to "Redis",
        "postgres" to "Postgres",
        "postgresql" to "PostgreSQL",
        "mysql" to "MySQL",
        "mongodb" to "MongoDB",
        "sqlite" to "SQLite",
        "graphql" to "GraphQL",
        "fastapi" to "FastAPI",
        "spring" to "Spring",
        "react" to "React",
        "angular" to "Angular",
        "vue" to "Vue",
        "git" to "Git",
        "github" to "GitHub",
        "gitlab" to "GitLab",
        "gemma" to "Gemma",
        "tensorflow" to "TensorFlow",
        "tflite" to "TFLite",
        "litert" to "LiteRT",
        "onnx" to "ONNX",
        "sherpa" to "Sherpa",
        "zipformer" to "Zipformer",
        "silero" to "Silero",
        "whisper" to "Whisper"
    )

    // Words indicating question intent
    private val QUESTION_STARTER_WORDS = setOf(
        "what", "what's", "whats",
        "how", "how's", "hows",
        "why", "why's", "whys",
        "where", "where's", "wheres",
        "when", "when's", "whens",
        "which", "who", "who's", "whom", "whose",
        "can", "could", "would", "should", "will", "shall", "may", "might", "must",
        "is", "are", "do", "does", "did", "have", "has", "had", "was", "were",
        "explain", "describe", "elaborate", "clarify", "discuss",
        "compare", "contrast", "differentiate"
    )

    // Contractions mapping (lowercase token -> expanded or apostrophed form)
    private val CONTRACTIONS = mapOf(
        "whats" to "what's",
        "hows" to "how's",
        "whys" to "why's",
        "wheres" to "where's",
        "whens" to "when's",
        "whos" to "who's",
        "thats" to "that's",
        "theres" to "there's",
        "heres" to "here's",
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "didnt" to "didn't",
        "doesnt" to "doesn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "couldnt" to "couldn't",
        "wouldnt" to "wouldn't",
        "shouldnt" to "shouldn't",
        "hasnt" to "hasn't",
        "havent" to "haven't",
        "im" to "I'm",
        "ive" to "I've",
        "id" to "I'd",
        "ill" to "I'll",
        "youre" to "you're",
        "youve" to "you've",
        "theyre" to "they're",
        "weve" to "we've",
        "gonna" to "going to",
        "wanna" to "want to"
    )

    /**
     * Post-processes an utterance transcript string into a clean, capitalized,
     * truecased, and punctuated sentence ready for LLM prompt injection and UI display.
     */
    fun process(rawTranscript: String): String {
        val trimmed = rawTranscript.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return ""

        val tokens = trimmed.split(" ")
        val processedTokens = mutableListOf<String>()

        var capitalizeNext = true

        for (token in tokens) {
            // Strip any existing punctuation for clean matching
            val cleanToken = token.trimEnd(',', '.', '?', '!', ';', ':')
            val trailingPunct = token.substring(cleanToken.length)
            val lower = cleanToken.lowercase(Locale.US)

            // 1. Check contractions
            val expandedWord = CONTRACTIONS[lower] ?: cleanToken

            // 2. Check acronyms
            val upperCandidate = lower.uppercase(Locale.US)
            val casedWord = when {
                ACRONYMS_UPPER.contains(upperCandidate) -> upperCandidate
                PROPER_NOUNS.containsKey(lower) -> PROPER_NOUNS[lower]!!
                lower == "i" -> "I"
                capitalizeNext -> expandedWord.replaceFirstChar { it.uppercase(Locale.US) }
                else -> expandedWord
            }

            processedTokens.add(casedWord + trailingPunct)
            capitalizeNext = trailingPunct.any { it == '.' || it == '?' || it == '!' }
        }

        var result = processedTokens.joinToString(" ").trim()

        // 3. Ensure appropriate terminal punctuation
        result = ensureTerminalPunctuation(result)

        return result
    }

    /**
     * Determines whether the given text represents an interview question
     * and appends '?' or '.' accordingly if terminal punctuation is missing.
     */
    fun ensureTerminalPunctuation(text: String): String {
        val trimmed = text.trimEnd(',', ';', ':', ' ')
        if (trimmed.isEmpty()) return ""
        val lastChar = trimmed.last()
        if (lastChar == '?' || lastChar == '.' || lastChar == '!') {
            return trimmed
        }

        return if (isQuestion(trimmed)) {
            "$trimmed?"
        } else {
            "$trimmed."
        }
    }

    /**
     * Analyzes grammatical structure and question triggers to determine
     * if the utterance is an interrogative or interview prompt.
     */
    fun isQuestion(text: String): Boolean {
        val clean = text.trim().lowercase(Locale.US)
        if (clean.length < 8) return false
        if (clean.endsWith("?")) return true

        // If the utterance has multiple sentences, inspect the last sentence to determine terminal punctuation
        val sentenceCandidate = clean.split(Regex("[.!?]\\s+")).lastOrNull { it.isNotBlank() } ?: clean
        val targetSentence = if (sentenceCandidate.length >= 8) sentenceCandidate else clean

        val words = targetSentence.split(Regex("\\s+"))
        if (words.isEmpty()) return false

        val firstWord = words.first()

        // 1. Direct question word at the start
        if (QUESTION_STARTER_WORDS.contains(firstWord)) {
            return true
        }

        // 2. Colloquial interview prompts
        if (targetSentence.startsWith("tell me about") ||
            targetSentence.startsWith("walk me through") ||
            targetSentence.startsWith("share an example") ||
            targetSentence.startsWith("in your opinion") ||
            targetSentence.startsWith("talk about") ||
            targetSentence.startsWith("what is") ||
            targetSentence.startsWith("how do") ||
            targetSentence.startsWith("what are") ||
            targetSentence.startsWith("difference between")
        ) {
            return true
        }

        // 3. Embedded question clauses
        if (words.size > 2) {
            val hasQuestionWord = words.drop(1).any {
                it == "what" || it == "how" || it == "why" || it == "which"
            }
            if (hasQuestionWord && (words.contains("difference") || words.contains("explain") || words.contains("know"))) {
                return true
            }
        }

        return false
    }
}
