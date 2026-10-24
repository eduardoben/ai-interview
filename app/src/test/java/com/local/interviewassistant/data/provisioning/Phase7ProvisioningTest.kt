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

class Phase7ProvisioningTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeStreamFetcher(
        private val contentMap: Map<String, ByteArray> = emptyMap(),
        private val failingUrl: String? = null
    ) : StreamFetcher {
        override fun openStream(url: String, authToken: String?, onTotalBytes: (Long) -> Unit): InputStream {
            if (url == failingUrl) {
                return object : InputStream() {
                    private var count = 0
                    override fun read(): Int {
                        if (count++ > 5) {
                            throw IOException("Simulated network stream interruption")
                        }
                        return 'X'.code
                    }
                }
            }
            val content = contentMap[url] ?: "dummy test content payload".toByteArray()
            onTotalBytes(content.size.toLong())
            return ByteArrayInputStream(content)
        }
    }

    @Test
    fun testAutoCreatesDirectoryAndDownloadsMissingSpeechModelsAtomically() = runTest {
        val modelsDir = File(tempFolder.root, "nested_models_dir")
        assertFalse(modelsDir.exists())

        val appConfig = AppConfig(modelsDir)
        val fakeContent = "valid model content".toByteArray()
        val contentMap = mapOf(
            AppConfig.VAD_URL to fakeContent,
            AppConfig.STT_ENCODER_URL to fakeContent,
            AppConfig.STT_DECODER_URL to fakeContent,
            AppConfig.STT_JOINER_URL to fakeContent,
            AppConfig.STT_TOKENS_URL to fakeContent
        )

        val fetcher = FakeStreamFetcher(contentMap)
        val provisioningManager = ModelProvisioningManager(appConfig, fetcher)

        val result = provisioningManager.downloadMissingSpeechModels()

        assertTrue(result.isSuccess)
        assertTrue(modelsDir.exists())

        assertTrue(appConfig.vadModelFile.exists())
        assertTrue(appConfig.sttEncoderFile.exists())
        assertTrue(appConfig.sttDecoderFile.exists())
        assertTrue(appConfig.sttJoinerFile.exists())
        assertTrue(appConfig.sttTokensFile.exists())

        val partFiles = modelsDir.listFiles { _, name -> name.endsWith(".part") }
        assertTrue(partFiles.isNullOrEmpty())

        val state = provisioningManager.state.value
        assertTrue(state is ProvisioningState.Completed)
        val report = (state as ProvisioningState.Completed).report
        assertTrue(report.files.filter { it.name != appConfig.llmModelFile.name }.all { it.exists && it.sizeBytes > 0 })
    }

    @Test
    fun testFailedDownloadCleansUpTemporaryPartFile() = runTest {
        val modelsDir = tempFolder.newFolder("models_fail_test")
        val appConfig = AppConfig(modelsDir)

        val fetcher = FakeStreamFetcher(failingUrl = AppConfig.VAD_URL)
        val provisioningManager = ModelProvisioningManager(appConfig, fetcher)

        val result = provisioningManager.downloadMissingSpeechModels()

        assertTrue(result.isFailure)

        val targetFile = appConfig.vadModelFile
        val partFile = File(modelsDir, "${targetFile.name}.part")

        assertFalse("Target file should not exist on failure", targetFile.exists())
        assertFalse("Part file should be deleted on failure", partFile.exists())

        val state = provisioningManager.state.value
        assertTrue(state is ProvisioningState.Error)
        assertEquals(targetFile.name, (state as ProvisioningState.Error).fileName)
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

        val state = provisioningManager.state.value
        assertTrue(state is ProvisioningState.Completed)
        val report = (state as ProvisioningState.Completed).report
        val llmStatus = report.files.first { it.name == appConfig.llmModelFile.name }
        assertTrue(llmStatus.exists)
        assertEquals(sampleData.size.toLong(), llmStatus.sizeBytes)
    }

    @Test
    fun testHttp400ErrorPropagatesAndCleansUpPartFile() = runTest {
        val modelsDir = tempFolder.newFolder("models_http400_test")
        val appConfig = AppConfig(modelsDir)

        val fetcher = StreamFetcher { url, _, _ ->
            throw IOException("HTTP 400 (Bad Request): Network failure or broken link for URL: $url")
        }
        val provisioningManager = ModelProvisioningManager(appConfig, fetcher)

        val result = provisioningManager.downloadMissingSpeechModels()

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IOException)
        assertTrue(exception?.message?.contains("HTTP 400") == true)

        val targetFile = appConfig.vadModelFile
        val partFile = File(modelsDir, "${targetFile.name}.part")

        assertFalse("Target file should not exist on HTTP 400 failure", targetFile.exists())
        assertFalse("Part file should be deleted on HTTP 400 failure", partFile.exists())

        val state = provisioningManager.state.value
        assertTrue(state is ProvisioningState.Error)
        assertEquals(targetFile.name, (state as ProvisioningState.Error).fileName)
        assertTrue(state.message.contains("HTTP 400"))
    }

    @Test
    fun testDownloadAllModelsSequentiallyDownloadsAllSixModels() = runTest {
        val modelsDir = tempFolder.newFolder("models_download_all_test")
        val appConfig = AppConfig(modelsDir)
        val fakeContent = "valid model binary content payload".toByteArray()

        val contentMap = mapOf(
            AppConfig.VAD_URL to fakeContent,
            AppConfig.STT_ENCODER_URL to fakeContent,
            AppConfig.STT_DECODER_URL to fakeContent,
            AppConfig.STT_JOINER_URL to fakeContent,
            AppConfig.STT_TOKENS_URL to fakeContent,
            AppConfig.DEFAULT_LLM_URL to fakeContent
        )

        val fetcher = FakeStreamFetcher(contentMap)
        val provisioningManager = ModelProvisioningManager(appConfig, fetcher)

        val result = provisioningManager.downloadAllModels()

        assertTrue(result.isSuccess)
        assertTrue(appConfig.vadModelFile.exists())
        assertTrue(appConfig.sttEncoderFile.exists())
        assertTrue(appConfig.sttDecoderFile.exists())
        assertTrue(appConfig.sttJoinerFile.exists())
        assertTrue(appConfig.sttTokensFile.exists())
        assertTrue(appConfig.llmModelFile.exists())

        val state = provisioningManager.state.value
        assertTrue(state is ProvisioningState.Completed)
        val report = (state as ProvisioningState.Completed).report
        assertTrue(report.allPresent)
        assertEquals(6, report.files.size)
    }

    @Test
    fun testHttpUrlStreamFetcherRejectsHttpWithAuthToken() {
        val fetcher = HttpUrlStreamFetcher()
        try {
            fetcher.openStream("http://example.com/model.bin", "secret-token", {})
            org.junit.Assert.fail("Expected IOException to be thrown for HTTP URL with auth token")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Insecure connection") == true)
        }
    }
}
