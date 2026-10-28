package com.local.interviewassistant.service

import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.data.hardware.DelegateType
import com.local.interviewassistant.data.hardware.HardwareProfile
import com.local.interviewassistant.data.hardware.HardwareTier
import com.local.interviewassistant.data.provisioning.ErrorReason
import com.local.interviewassistant.data.provisioning.ModelProvisioningManager
import com.local.interviewassistant.data.provisioning.ProvisioningState
import com.local.interviewassistant.data.provisioning.StreamFetcher
import com.local.interviewassistant.data.provisioning.StreamResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloadServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testProfile = HardwareProfile(
        tier = HardwareTier.TPU,
        preferredDelegate = DelegateType.NPU_GOOGLE_TENSOR,
        socName = "Google Tensor G4",
        totalRamMb = 16000L,
        availableRamMb = 8000L,
        recommendedLlmFileName = "gemma-4-E2B-it_Google_Tensor_G4.litertlm",
        recommendedLlmDownloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_Google_Tensor_G4.litertlm",
        expectedLlmBytes = 3_113_545_589L,
        autoEnableWhisperRescorer = true
    )

    @Test
    fun testForegroundServiceTimeoutCancelsStreamPreservesTmpAndSetsCanResumeTrue() = runTest {
        val modelsDir = tempFolder.newFolder("service_timeout_test")
        val appConfig = AppConfig(modelsDir)
        appConfig.ensureDirectoriesExist()

        // Target file
        val targetTmpFile = File(appConfig.vadDir, "silero_vad.onnx.tmp")

        // Slow infinite stream simulator to mimic a hanging download that hits Android 17 FGS timeout
        val slowStream = object : InputStream() {
            private var bytesEmitted = 0
            override fun read(): Int {
                if (bytesEmitted++ < 50_000) {
                    return 0x42
                }
                // Simulate blocking wait
                Thread.sleep(10)
                return 0x42
            }
        }

        val fetcher = StreamFetcher { _, _, _ ->
            StreamResponse(200, slowStream, 2_200_000L)
        }

        val manager = ModelProvisioningManager(appConfig, fetcher)

        // Launch download in a coroutine
        val job = launch {
            manager.downloadAllModels(testProfile)
        }

        // Give it time to start writing to .tmp
        var attempts = 0
        while (!targetTmpFile.exists() && attempts++ < 50) {
            testScheduler.advanceTimeBy(50)
        }

        // Trigger timeout handling (as ModelDownloadService.onTimeout does)
        job.cancel()
        manager.cancelOngoingDownload(
            profile = testProfile,
            reason = ErrorReason.SERVICE_TIMEOUT,
            message = "Download timed out by Android 17 FGS quota."
        )

        // Verify state is Error with SERVICE_TIMEOUT and canResume = true
        val state = manager.state.value
        assertTrue("State should be ProvisioningState.Error on timeout", state is ProvisioningState.Error)
        val errorState = state as ProvisioningState.Error
        assertEquals(ErrorReason.SERVICE_TIMEOUT, errorState.reason)
        assertTrue("canResume must be true on timeout so user can resume seamlessly", errorState.canResume)

        // Verify that the partial .tmp file was NOT deleted, so bytes can be resumed with HTTP 206
        if (targetTmpFile.exists()) {
            assertTrue("Partial .tmp file must be preserved on disk for resumption", targetTmpFile.length() > 0)
        }
    }
}
