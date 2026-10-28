package com.local.interviewassistant.core.model

import com.local.interviewassistant.data.hardware.DelegateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppConfigResolutionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSubdirectoryTakesPrecedenceOverFlatRoot() {
        val root = tempFolder.newFolder("models_sub_precedence")
        val config = AppConfig(root)
        config.ensureDirectoriesExist()

        // Create both flat and structured files
        val flatVad = File(root, "silero_vad.onnx")
        flatVad.writeBytes(ByteArray(10) { 1 })

        val subVad = File(config.vadDir, "silero_vad.onnx")
        subVad.writeBytes(ByteArray(20) { 2 })

        // Subdirectory must take precedence
        assertEquals(subVad.absolutePath, config.vadModelFile.absolutePath)
        assertEquals(20L, config.vadModelFile.length())
    }

    @Test
    fun testFallbackToFlatRootWhenSubdirectoryFileDoesNotExist() {
        val root = tempFolder.newFolder("models_flat_fallback")
        val config = AppConfig(root)
        // Ensure subdirectories exist, but don't place files in them
        config.ensureDirectoriesExist()

        val flatVad = File(root, "silero_vad.onnx")
        flatVad.writeBytes(ByteArray(15) { 3 })

        // Subdirectory file does not exist, so fallback to flat
        assertEquals(flatVad.absolutePath, config.vadModelFile.absolutePath)
        assertEquals(15L, config.vadModelFile.length())
    }

    @Test
    fun testAsrFilesSubdirectoryResolution() {
        val root = tempFolder.newFolder("models_asr_resolution")
        val config = AppConfig(root)
        config.ensureDirectoriesExist()

        val asrTokens = File(config.asrDir, "tokens.txt")
        asrTokens.writeText("test tokens")

        val asrEncoder = File(config.asrDir, "encoder-epoch-99-avg-1.int8.onnx")
        asrEncoder.writeBytes(ByteArray(5))

        val asrDecoder = File(config.asrDir, "decoder-epoch-99-avg-1.int8.onnx")
        asrDecoder.writeBytes(ByteArray(5))

        val asrJoiner = File(config.asrDir, "joiner-epoch-99-avg-1.int8.onnx")
        asrJoiner.writeBytes(ByteArray(5))

        assertEquals(asrTokens.absolutePath, config.sttTokensFile.absolutePath)
        assertEquals(asrEncoder.absolutePath, config.sttEncoderFile.absolutePath)
        assertEquals(asrDecoder.absolutePath, config.sttDecoderFile.absolutePath)
        assertEquals(asrJoiner.absolutePath, config.sttJoinerFile.absolutePath)
    }

    @Test
    fun testWhisperFilesSubdirectoryResolution() {
        val root = tempFolder.newFolder("models_whisper_resolution")
        val config = AppConfig(root)
        config.ensureDirectoriesExist()

        val wTokens = File(config.whisperDir, "tiny.en-tokens.txt")
        wTokens.writeText("whisper tokens")

        val wEncoder = File(config.whisperDir, "tiny.en-encoder.int8.onnx")
        wEncoder.writeBytes(ByteArray(5))

        val wDecoder = File(config.whisperDir, "tiny.en-decoder.int8.onnx")
        wDecoder.writeBytes(ByteArray(5))

        assertEquals(wTokens.absolutePath, config.offlineWhisperTokensFile.absolutePath)
        assertEquals(wEncoder.absolutePath, config.offlineWhisperEncoderFile.absolutePath)
        assertEquals(wDecoder.absolutePath, config.offlineWhisperDecoderFile.absolutePath)
    }

    @Test
    fun testLlmSubdirectoryResolution() {
        val root = tempFolder.newFolder("models_llm_resolution")
        val config = AppConfig(root)
        config.ensureDirectoriesExist()

        val llmFile = File(config.llmDir, "gemma-4-E2B-it.litertlm")
        llmFile.writeBytes(ByteArray(50))

        assertEquals(llmFile.absolutePath, config.llmModelFile.absolutePath)
    }

    @Test
    fun testUseGpuDelegateBasedOnPreferredDelegate() {
        val root = tempFolder.newFolder("models_delegate_config")
        val config = AppConfig(root)

        // Default null -> useGpuDelegate true
        assertTrue(config.useGpuDelegate)

        // CPU_MMAP -> useGpuDelegate false
        config.updateLlmConfig(config.llmConfig.value.copy(preferredDelegate = DelegateType.CPU_MMAP))
        assertFalse(config.useGpuDelegate)

        // GPU_LITERT -> useGpuDelegate true
        config.updateLlmConfig(config.llmConfig.value.copy(preferredDelegate = DelegateType.GPU_LITERT))
        assertTrue(config.useGpuDelegate)

        // NPU_GOOGLE_TENSOR -> useGpuDelegate true
        config.updateLlmConfig(config.llmConfig.value.copy(preferredDelegate = DelegateType.NPU_GOOGLE_TENSOR))
        assertTrue(config.useGpuDelegate)
    }

    @Test
    fun testEnsureDirectoriesExistCreatesAllFourSubdirs() {
        val root = tempFolder.newFolder("models_ensure_dirs")
        val config = AppConfig(root)

        assertFalse(config.vadDir.exists())
        assertFalse(config.asrDir.exists())
        assertFalse(config.whisperDir.exists())
        assertFalse(config.llmDir.exists())

        config.ensureDirectoriesExist()

        assertTrue(config.vadDir.exists() && config.vadDir.isDirectory)
        assertTrue(config.asrDir.exists() && config.asrDir.isDirectory)
        assertTrue(config.whisperDir.exists() && config.whisperDir.isDirectory)
        assertTrue(config.llmDir.exists() && config.llmDir.isDirectory)
    }
}
