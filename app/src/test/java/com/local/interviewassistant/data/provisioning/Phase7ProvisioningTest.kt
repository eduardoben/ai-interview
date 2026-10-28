package com.local.interviewassistant.data.provisioning

import com.local.interviewassistant.core.model.AppConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Arrays

class Phase7ProvisioningTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeStreamFetcher(
        private val failingUrl: String? = null,
        private val payloadSizeMap: Map<String, Long> = emptyMap()
    ) : StreamFetcher {
        override fun openStream(url: String, rangeOffset: Long, authToken: String?): StreamResponse {
            if (url == failingUrl) {
                val failingStream = object : InputStream() {
                    private var count = 0
                    override fun read(): Int {
                        if (count++ > 5) {
                            throw IOException("Simulated network stream interruption")
                        }
                        return 'X'.code
                    }
                }
                return StreamResponse(200, failingStream, 100_000L)
            }

            val size = payloadSizeMap[url] ?: 3_000_000L
            val stream = object : InputStream() {
                var remaining = (size - rangeOffset).coerceAtLeast(0L)
                override fun read(): Int {
                    if (remaining <= 0L) return -1
                    remaining--
                    return 0x01
                }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (remaining <= 0L) return -1
                    val toRead = len.toLong().coerceAtMost(remaining).toInt()
                    Arrays.fill(b, off, off + toRead, 0x01.toByte())
                    remaining -= toRead
                    return toRead
                }
            }
            return StreamResponse(if (rangeOffset > 0L) 206 else 200, stream, size - rangeOffset)
        }
    }

    @Test
    fun testAutoCreatesDirectoryAndDownloadsMissingSpeechModelsAtomically() = runTest {
        val modelsDir = File(tempFolder.root, "nested_models_dir")
        assertFalse(modelsDir.exists())

        val appConfig = AppConfig(modelsDir)
        val sizeMap = mapOf(
            AppConfig.VAD_URL to 2_500_000L,
            AppConfig.STT_ENCODER_URL to 125_000_000L,
            AppConfig.STT_DECODER_URL to 3_000_000L,
            AppConfig.STT_JOINER_URL to 60_000_000L,
            AppConfig.STT_TOKENS_URL to 10_000L
        )

        val fetcher = FakeStreamFetcher(payloadSizeMap = sizeMap)
        val provisioningManager = ModelProvisioningManager(appConfig, fetcher)

        val profile = com.local.interviewassistant.data.hardware.DeviceHardwareDetector.classify(
            com.local.interviewassistant.data.hardware.SystemDeviceInfo(
                "", "", "", "", 8_000_000_000L, 4_000_000_000L, false, false
            )
        )
        val manifest = provisioningManager.buildManifest(profile)

        // Pre-create other models in manifest (whisper and llm) so only speech models are needed
        for (entry in manifest.drop(5)) {
            entry.targetFile.parentFile?.mkdirs()
            java.io.RandomAccessFile(entry.targetFile, "rw").use { raf ->
                raf.setLength(entry.minExpectedBytes + 100L)
            }
        }

        val result = provisioningManager.downloadAllModels(profile)

        assertTrue(result.isSuccess)
        assertTrue(modelsDir.exists())

        assertTrue(appConfig.vadModelFile.exists())
        assertTrue(appConfig.sttEncoderFile.exists())
        assertTrue(appConfig.sttDecoderFile.exists())
        assertTrue(appConfig.sttJoinerFile.exists())
        assertTrue(appConfig.sttTokensFile.exists())

        val tmpFiles = modelsDir.walkTopDown().filter { it.name.endsWith(".tmp") }.toList()
        assertTrue(tmpFiles.isEmpty())

        val state = provisioningManager.state.value
        assertTrue(state is ProvisioningState.Ready)
    }

    @Test
    fun testFailedDownloadPreservesPartialTmpFileForResume() = runTest {
        val modelsDir = tempFolder.newFolder("models_fail_test")
        val appConfig = AppConfig(modelsDir)

        val fetcher = FakeStreamFetcher(failingUrl = AppConfig.VAD_URL)
        val provisioningManager = ModelProvisioningManager(appConfig, fetcher)

        val profile = com.local.interviewassistant.data.hardware.DeviceHardwareDetector.classify(
            com.local.interviewassistant.data.hardware.SystemDeviceInfo(
                "", "", "", "", 8_000_000_000L, 4_000_000_000L, false, false
            )
        )

        val result = provisioningManager.downloadAllModels(profile)

        assertTrue(result.isFailure)

        val targetFile = appConfig.vadModelFile
        val tmpFile = File(appConfig.vadDir, "${targetFile.name}.tmp")

        assertFalse("Target file should not exist on failure", targetFile.exists())
        assertTrue("Partial tmp file should be preserved on disk for resumption", tmpFile.exists())

        val state = provisioningManager.state.value
        assertTrue(state is ProvisioningState.Error)
        assertTrue((state as ProvisioningState.Error).canResume)
    }

    @Test
    fun testImportModelFromStreamCopiesFileCorrectly() = runTest {
        val modelsDir = tempFolder.newFolder("models_import_test")
        val appConfig = AppConfig(modelsDir)

        val provisioningManager = ModelProvisioningManager(appConfig, FakeStreamFetcher())
        val sampleData = "gemma 4 int4 model weights binary stream".toByteArray()

        val result = provisioningManager.importModelFromStream(
            inputStreamProvider = { ByteArrayInputStream(sampleData) },
            targetFile = appConfig.llmModelFile,
            expectedSizeBytes = sampleData.size.toLong()
        )

        assertTrue(result.isSuccess)
        assertTrue(appConfig.llmModelFile.exists())
        assertEquals(sampleData.size.toLong(), appConfig.llmModelFile.length())
    }

    @Test
    fun testHttp400ErrorPropagatesAndPreservesState() = runTest {
        val modelsDir = tempFolder.newFolder("models_http400_test")
        val appConfig = AppConfig(modelsDir)

        val fetcher = StreamFetcher { url, _, _ ->
            throw IOException("HTTP 400 (Bad Request): Network failure or broken link for URL: $url")
        }
        val provisioningManager = ModelProvisioningManager(appConfig, fetcher)
        val profile = com.local.interviewassistant.data.hardware.DeviceHardwareDetector.classify(
            com.local.interviewassistant.data.hardware.SystemDeviceInfo(
                "", "", "", "", 8_000_000_000L, 4_000_000_000L, false, false
            )
        )

        val result = provisioningManager.downloadAllModels(profile)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IOException)
        assertTrue(exception?.message?.contains("HTTP 400") == true)

        val targetFile = appConfig.vadModelFile
        assertFalse("Target file should not exist on HTTP 400 failure", targetFile.exists())

        val state = provisioningManager.state.value
        assertTrue(state is ProvisioningState.Error)
        assertTrue((state as ProvisioningState.Error).message.contains("HTTP 400"))
    }
}
