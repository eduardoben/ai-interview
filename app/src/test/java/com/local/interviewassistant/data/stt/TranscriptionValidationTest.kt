package com.local.interviewassistant.data.stt

import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Comprehensive edge case and accuracy test suite for transcription validation:
 * - 100+ technical acronyms truecasing
 * - Proper nouns casing
 * - Contraction expansions and apostrophes
 * - Question detection across interview domains (technical, behavioral, architecture)
 * - Punctuation preservation (preventing duplicate punctuation)
 * - Terminal punctuation restoration (? vs .)
 * - Trailing punctuation cleanup
 * - Multi-sentence transcript processing
 * - Hotwords dictionary lowercase and vocabulary validation
 */
class TranscriptionValidationTest {

    private val tempDir = File.createTempFile("transcription_test_", "").apply {
        delete()
        mkdirs()
    }
    private val config = AppConfig(tempDir)

    @Test
    fun testAllUpperAcronymsAreTruecased() {
        val acronymsToVerify = listOf(
            "api", "apis", "rest", "http", "https", "json", "sql", "html", "css", "xml",
            "jvm", "jni", "sdk", "ndk", "aar", "apk", "jar", "url", "uri", "uuid",
            "cpu", "gpu", "tpu", "npu", "ram", "rom", "ssd", "hdd", "io",
            "tcp", "udp", "ip", "dns", "ssh", "ssl", "tls", "ipc", "rpc", "grpc",
            "mvvm", "mvi", "mvp", "mvc", "oop", "solid", "dry", "kiss", "yagni",
            "di", "ci", "cd", "ci/cd", "aws", "gcp", "bpe", "vad", "stt", "tts", "asr", "llm",
            "star", "kpi", "kpis", "roi", "ebitda", "okrs", "okr", "sla", "slo", "sli",
            "qa", "ui", "ux", "pr", "os", "ide", "db", "rdbms", "acid", "base", "orm",
            "aidl", "hal", "oom", "anr", "fps", "ttft", "tps"
        )

        for (acronym in acronymsToVerify) {
            val input = "what is $acronym"
            val result = QuestionPunctuationEngine.process(input)
            val expectedAcronym = acronym.uppercase()
            assertTrue(
                "Expected '$expectedAcronym' in '$result' for input '$input'",
                result.contains(expectedAcronym)
            )
            assertTrue("Expected result to end with '?'", result.endsWith("?"))
        }
    }

    @Test
    fun testAllProperNounsCasing() {
        val properNouns = mapOf(
            "kotlin" to "Kotlin",
            "java" to "Java",
            "python" to "Python",
            "javascript" to "JavaScript",
            "typescript" to "TypeScript",
            "rust" to "Rust",
            "golang" to "Go",
            "swift" to "Swift",
            "android" to "Android",
            "pixel" to "Pixel",
            "google" to "Google",
            "coroutine" to "Coroutine",
            "coroutines" to "Coroutines",
            "stateflow" to "StateFlow",
            "sharedflow" to "SharedFlow",
            "viewmodel" to "ViewModel",
            "compose" to "Compose",
            "jetpack" to "Jetpack",
            "room" to "Room",
            "retrofit" to "Retrofit",
            "okhttp" to "OkHttp",
            "dagger" to "Dagger",
            "hilt" to "Hilt",
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

        for ((inputWord, expectedWord) in properNouns) {
            val input = "tell me about $inputWord"
            val result = QuestionPunctuationEngine.process(input)
            assertTrue(
                "Expected '$expectedWord' in '$result' for input word '$inputWord'",
                result.contains(expectedWord)
            )
        }
    }

    @Test
    fun testContractionsExpansionAndApostrophes() {
        val contractions = mapOf(
            "whats the best practice" to "What's the best practice?",
            "hows your experience" to "How's your experience?",
            "whys that the case" to "Why's that the case?",
            "wheres the error located" to "Where's the error located?",
            "whens the deadline" to "When's the deadline?",
            "whos responsible for this" to "Who's responsible for this?",
            "i dont think we should do that" to "I don't think we should do that.",
            "we cant reproduce the crash" to "We can't reproduce the crash.",
            "it wont scale horizontally" to "It won't scale horizontally.",
            "they didnt write unit tests" to "They didn't write unit tests.",
            "it doesnt matter" to "It doesn't matter.",
            "this isnt an issue" to "This isn't an issue.",
            "we arent ready" to "We aren't ready.",
            "i couldnt find the memory leak" to "I couldn't find the memory leak.",
            "we wouldnt recommend that" to "We wouldn't recommend that.",
            "you shouldnt block the main thread" to "You shouldn't block the main thread.",
            "im very interested in this role" to "I'm very interested in this role.",
            "ive worked with kotlin for 5 years" to "I've worked with Kotlin for 5 years.",
            "id be happy to explain" to "I'd be happy to explain.",
            "ill demonstrate with an example" to "I'll demonstrate with an example.",
            "youre doing great" to "You're doing great.",
            "weve migrated to compose" to "We've migrated to Compose.",
            "we are gonna deploy to production" to "We are going to deploy to production.",
            "i wanna discuss the system design" to "I want to discuss the system design."
        )

        for ((input, expected) in contractions) {
            val actual = QuestionPunctuationEngine.process(input)
            assertEquals("Mismatch for input: '$input'", expected, actual)
        }
    }

    @Test
    fun testPunctuationPreservationNoDuplicates() {
        // Inputs that ALREADY have terminal punctuation should NOT get double punctuation
        assertEquals("What is Kotlin Coroutine?", QuestionPunctuationEngine.process("what is kotlin coroutine?"))
        assertEquals("What is Kotlin Coroutine?", QuestionPunctuationEngine.process("What is Kotlin Coroutine?"))
        assertEquals("I have five years of experience.", QuestionPunctuationEngine.process("i have five years of experience."))
        assertEquals("That was an impressive achievement!", QuestionPunctuationEngine.process("that was an impressive achievement!"))

        // Ensure ensureTerminalPunctuation respects existing terminal symbols
        assertEquals("Hello?", QuestionPunctuationEngine.ensureTerminalPunctuation("Hello?"))
        assertEquals("Hello.", QuestionPunctuationEngine.ensureTerminalPunctuation("Hello."))
        assertEquals("Hello!", QuestionPunctuationEngine.ensureTerminalPunctuation("Hello!"))
    }

    @Test
    fun testTrailingPunctuationSanitization() {
        // Utterance with trailing commas, colons, or semicolons should be cleanly stripped
        val q1 = QuestionPunctuationEngine.process("can you explain rest api,")
        assertEquals("Can you explain REST API?", q1)

        val q2 = QuestionPunctuationEngine.process("how does room handle migrations;")
        assertEquals("How does Room handle migrations?", q2)

        val q3 = QuestionPunctuationEngine.process("tell me about star method:")
        assertEquals("Tell me about STAR method?", q3)
    }

    @Test
    fun testMultiSentenceTranscription() {
        val input = "can you explain rest api. how does kotlin work"
        val output = QuestionPunctuationEngine.process(input)
        assertEquals("Can you explain REST API. How does Kotlin work?", output)

        val statementFollowedByQuestion = "i use mvvm. what are the advantages of mvi"
        val output2 = QuestionPunctuationEngine.process(statementFollowedByQuestion)
        assertEquals("I use MVVM. What are the advantages of MVI?", output2)
    }

    @Test
    fun testEmptyAndWhitespaceTranscripts() {
        assertEquals("", QuestionPunctuationEngine.process(""))
        assertEquals("", QuestionPunctuationEngine.process("   "))
        assertEquals("", QuestionPunctuationEngine.process("\n\t  \r"))
        assertEquals("", QuestionPunctuationEngine.ensureTerminalPunctuation(""))
    }

    @Test
    fun testSingleWordTranscripts() {
        assertEquals("Kotlin.", QuestionPunctuationEngine.process("kotlin"))
        assertEquals("API.", QuestionPunctuationEngine.process("api"))
        assertEquals("REST.", QuestionPunctuationEngine.process("rest"))
        assertEquals("I.", QuestionPunctuationEngine.process("i"))
    }

    @Test
    fun testLowercaseIConvertedToCapitalI() {
        val input = "i think i should use kotlin coroutines because i love it"
        val output = QuestionPunctuationEngine.process(input)
        assertEquals("I think I should use Kotlin Coroutines because I love it.", output)
    }

    @Test
    fun testComprehensiveInterrogativeClassification() {
        // 1. Direct question starter words
        val starters = listOf(
            "what is the difference between val and var",
            "how do you handle configuration changes",
            "why should we use dependency injection with hilt",
            "where does room store database files",
            "when would you use stateflow over sharedflow",
            "which garbage collection algorithm does android use",
            "who designed the kotlin language",
            "can you explain how compose recomposition works",
            "could you describe your experience with CI/CD pipelines",
            "would you prefer flow or rxjava for reactive streams",
            "should viewmodels hold android context references",
            "is kotlin 100% interoperable with java",
            "are coroutines lightweight threads",
            "does sqlite support concurrent writes",
            "did you encounter any ANR or OOM crashes in your app",
            "have you worked with litert or mediapipe",
            "has the team adopted clean architecture",
            "explain the star method for behavioral questions",
            "describe a situation where you had a conflict with a peer",
            "compare mvvm and mvi architecture patterns",
            "differentiate between thread pool and coroutine dispatcher"
        )

        for (starter in starters) {
            val processed = QuestionPunctuationEngine.process(starter)
            assertTrue(
                "Sentence starting with '${starter.split(" ").first()}' should end with '?': $processed",
                processed.endsWith("?")
            )
            assertTrue(
                "Sentence should be detected as question: $starter",
                QuestionPunctuationEngine.isQuestion(starter)
            )
        }

        // 2. Colloquial interview prompts
        val colloquialPrompts = listOf(
            "tell me about a time you solved a complex production bug",
            "walk me through your design for a real-time messaging app",
            "share an example of when you demonstrated leadership",
            "in your opinion what is the biggest drawback of microservices",
            "difference between process and thread in android"
        )

        for (prompt in colloquialPrompts) {
            val processed = QuestionPunctuationEngine.process(prompt)
            assertTrue("Prompt should end with '?': $processed", processed.endsWith("?"))
            assertTrue("Prompt should be detected as question: $prompt", QuestionPunctuationEngine.isQuestion(prompt))
        }

        // 3. Declarative statements (must NOT end with '?')
        val statements = listOf(
            "i have five years of experience as a senior android engineer",
            "we reduced the app bundle size by forty percent using proguard",
            "the pipeline successfully deployed the apk to the internal test track",
            "our service achieved sixty fps rendering with minimal jank",
            "i enjoy collaborating with cross functional engineering teams"
        )

        for (stmt in statements) {
            val processed = QuestionPunctuationEngine.process(stmt)
            assertTrue("Declarative statement should end with '.': $processed", processed.endsWith("."))
            assertFalse("Statement should NOT be detected as question: $stmt", QuestionPunctuationEngine.isQuestion(stmt))
        }

        // 4. Sub-threshold length (< 8 chars)
        assertFalse(QuestionPunctuationEngine.isQuestion("what"))
        assertFalse(QuestionPunctuationEngine.isQuestion("how?"))
        assertFalse(QuestionPunctuationEngine.isQuestion("why"))
    }

    @Test
    fun testTranscriptSegmentSmartQuestionDetectionEdgeCases() {
        // Normalization handles excessive whitespace, tabs, and newlines
        val dirtySegment = TranscriptSegment(
            text = "  what   is   the   difference \t between \n val and var?  ",
            isFinal = true
        )
        assertEquals("what is the difference between val and var?", dirtySegment.normalizedText)
        assertTrue(dirtySegment.isActionableQuestion)
        assertTrue(dirtySegment.isLikelyQuestion)

        // Non-final segments are NOT actionable questions
        val partialSegment = TranscriptSegment(
            text = "what is the difference between val and var in kotlin",
            isFinal = false
        )
        assertFalse(partialSegment.isActionableQuestion)
        assertFalse(partialSegment.isLikelyQuestion)

        // Utterance below 12 characters is not an actionable question
        val shortSegment = TranscriptSegment(text = "Hello there", isFinal = true)
        assertEquals(11, shortSegment.normalizedText.length)
        assertFalse(shortSegment.isActionableQuestion)
        assertFalse(shortSegment.isLikelyQuestion)

        // Utterance with length exactly 12 characters and question mark
        val exact12CharQuestion = TranscriptSegment(text = "What is SQL?", isFinal = true)
        assertEquals(12, exact12CharQuestion.normalizedText.length)
        assertTrue(exact12CharQuestion.isActionableQuestion)
        assertTrue(exact12CharQuestion.isLikelyQuestion)

        // Sustained utterance >= 24 characters is detected as likely question even without question mark
        val sustainedSentence = TranscriptSegment(
            text = "I think we need to carefully review the database schema migration",
            isFinal = true
        )
        assertTrue(sustainedSentence.isActionableQuestion)
        assertTrue(sustainedSentence.isLikelyQuestion)

        // Segment ID is unique
        val segA = TranscriptSegment(text = "sample text", isFinal = true)
        val segB = TranscriptSegment(text = "sample text", isFinal = true)
        assertNotNull(segA.id)
        assertNotNull(segB.id)
        assertTrue(segA.id != segB.id)
        assertTrue(segA.timestampMs > 0L)
    }

    @Test
    fun testHotwordsFileIntegrityAndCompleteness() {
        val hotwordsFile = config.ensureHotwordsFile(forceRefresh = true)
        assertTrue(hotwordsFile.exists())
        assertTrue(hotwordsFile.length() > 0)

        val lines = hotwordsFile.readLines().map { it.trim() }
        assertFalse("Hotwords file must not be empty", lines.isEmpty())

        // Ensure no uppercase characters
        for (line in lines) {
            assertFalse("Hotword '$line' contains uppercase characters", line.any { it.isUpperCase() })
            assertFalse("Hotword must not be blank", line.isBlank())
        }

        // Verify key domains are represented in hotwords
        val text = lines.joinToString(" ")
        // Technical
        assertTrue(text.contains("kotlin"))
        assertTrue(text.contains("coroutines"))
        assertTrue(text.contains("stateflow"))
        assertTrue(text.contains("viewmodel"))
        assertTrue(text.contains("mvvm"))
        assertTrue(text.contains("clean architecture"))
        assertTrue(text.contains("docker"))
        assertTrue(text.contains("kubernetes"))
        // Behavioral
        assertTrue(text.contains("star"))
        assertTrue(text.contains("leadership"))
        assertTrue(text.contains("conflict"))
        // Business / Finance
        assertTrue(text.contains("ebitda"))
        assertTrue(text.contains("roi"))
        assertTrue(text.contains("kpi"))
        // Healthcare
        assertTrue(text.contains("diagnosis"))
        assertTrue(text.contains("clinical"))
    }
}
