package com.local.interviewassistant.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.io.path.createTempDirectory

class Phase2DomainTest {

    @Test
    fun testTranscriptSegmentIsActionableQuestion() {
        // Blank string, isFinal = true
        val blankSegment = TranscriptSegment(text = "   ", isFinal = true)
        assertFalse(blankSegment.isActionableQuestion)

        // < 12 chars, isFinal = true
        val shortSegment = TranscriptSegment(text = "Hello world", isFinal = true) // 11 chars
        assertFalse(shortSegment.isActionableQuestion)

        // >= 12 chars, isFinal = false
        val longNotFinal = TranscriptSegment(text = "What is polymorphism?", isFinal = false)
        assertFalse(longNotFinal.isActionableQuestion)

        // >= 12 chars, isFinal = true
        val validSegment = TranscriptSegment(text = "What is polymorphism?", isFinal = true)
        assertTrue(validSegment.isActionableQuestion)

        // Check normalization
        val extraSpacesSegment = TranscriptSegment(text = "   What   is   polymorphism?   ", isFinal = true)
        assertEquals("What is polymorphism?", extraSpacesSegment.normalizedText)
        assertTrue(extraSpacesSegment.isActionableQuestion)
    }

    @Test
    fun testAppConfigValidateModelsOnDisk_Missing() {
        val tempDir = createTempDirectory("empty_models").toFile()
        tempDir.deleteOnExit()

        val config = AppConfig(tempDir)
        val report = config.validateModelsOnDisk()
        
        assertFalse(report.allPresent)
        assertTrue(report.files.all { (_, _, exists, sizeBytes) -> (!exists) && (sizeBytes == 0L) })
    }

    @Test
    fun testAppConfigValidateModelsOnDisk_Present() {
        val tempDir = createTempDirectory("populated_models").toFile()
        tempDir.deleteOnExit()

        val config = AppConfig(tempDir)
        
        // Create dummy files
        listOf(
            config.vadModelFile,
            config.sttEncoderFile,
            config.sttDecoderFile,
            config.sttJoinerFile,
            config.sttTokensFile,
            config.llmModelFile,
        ).forEach { file ->
            file.writeText("dummy content")
        }

        val report = config.validateModelsOnDisk()
        
        assertTrue(report.allPresent)
        assertTrue(report.files.all { (_, _, exists, sizeBytes) -> exists && (sizeBytes > 0L) })
    }

    @Test
    fun testAppConfigFormatGemmaPrompt() {
        val config = AppConfig(File("."))
        val question = "   What is dependency injection?   "
        val formatted = config.formatGemmaPrompt(question)

        val expected = "<start_of_turn>user\n" +
                "You are an expert candidate assisting in an interview. Answer the following question directly, accurately, and concisely. Use at most 3 key bullet points. No introductions or greetings.\n" +
                "Question: What is dependency injection?<end_of_turn>\n" +
                "<start_of_turn>model\n"

        assertEquals(expected, formatted)
    }


    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDiscoverLocalModels() {
        // 1. Non-existent directory
        val nonExistentDir = File("does_not_exist_xyz123")
        val configNonExistent = AppConfig(nonExistentDir)
        assertTrue(configNonExistent.discoverLocalModels().isEmpty())

        // 2. Empty directory
        val emptyDir = tempFolder.newFolder("empty_models_dir")
        val configEmpty = AppConfig(emptyDir)
        assertTrue(configEmpty.discoverLocalModels().isEmpty())

        // Create a base directory for remaining tests
        val baseDir = tempFolder.newFolder("models_dir")
        val config = AppConfig(baseDir)

        // 3. Valid files with supported extension and size > 0
        val validFile1 = File(baseDir, "model1.litertlm").apply { writeText("data") }

        // 4. Supported extension but size == 0
        val emptyFile = File(baseDir, "empty.litertlm").apply { writeText("") }

        // 5. Unsupported extensions (legacy MediaPipe/TFLite formats are no longer runnable)
        val unsupportedFile = File(baseDir, "model.txt").apply { writeText("data") }
        val legacyBin = File(baseDir, "model2.bin").apply { writeText("data") }
        val legacyTask = File(baseDir, "model5.task").apply { writeText("data") }
        val legacyTflite = File(baseDir, "model3.tflite").apply { writeText("data") }

        // 6. Case insensitivity of extensions
        val uppercaseExtFile = File(baseDir, "model6.LITERTLM").apply { writeText("data") }

        // 7. Subdirectories are traversed correctly
        val subDir = File(baseDir, "subdir").apply { mkdir() }
        val subDirFile = File(subDir, "model4.litertlm").apply { writeText("data") }

        val discoveredFiles = config.discoverLocalModels()

        assertEquals(3, discoveredFiles.size)
        assertTrue(discoveredFiles.contains(validFile1))
        assertTrue(discoveredFiles.contains(uppercaseExtFile))
        assertTrue(discoveredFiles.contains(subDirFile))
        assertFalse(discoveredFiles.contains(emptyFile))
        assertFalse(discoveredFiles.contains(unsupportedFile))
        assertFalse(discoveredFiles.contains(legacyBin))
        assertFalse(discoveredFiles.contains(legacyTask))
        assertFalse(discoveredFiles.contains(legacyTflite))
    }
}
