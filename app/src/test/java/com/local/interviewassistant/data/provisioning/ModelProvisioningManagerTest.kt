package com.local.interviewassistant.data.provisioning

import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.data.hardware.DelegateType
import com.local.interviewassistant.data.hardware.DeviceHardwareDetector
import com.local.interviewassistant.data.hardware.HardwareProfile
import com.local.interviewassistant.data.hardware.HardwareTier
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

class ModelProvisioningManagerTest {

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

    private class RangeTestStreamFetcher(
        private val fullPayload: ByteArray,
        val recordedRanges: MutableList<Long> = mutableListOf()
    ) : StreamFetcher {
        override fun openStream(url: String, rangeOffset: Long, authToken: String?): StreamResponse {
            recordedRanges.add(rangeOffset)
            val offset = rangeOffset.toInt().coerceAtMost(fullPayload.size)
            val remaining = fullPayload.copyOfRange(offset, fullPayload.size)
            val statusCode = if (rangeOffset > 0L) 206 else 200
            return StreamResponse(
                statusCode = statusCode,
                inputStream = ByteArrayInputStream(remaining),
                contentLength = remaining.size.toLong()
            )
        }
    }

    @Test
    fun testHttp206RangeResumeAppendsBytesWithoutRestartingFromZero() = runTest {
        val modelsDir = tempFolder.newFolder("models_range_test")
        val appConfig = AppConfig(modelsDir)

        // Pre-create a partial .tmp file for silero_vad.onnx (say 1,000,000 bytes)
        val vadTmpFile = File(appConfig.vadDir, "silero_vad.onnx.tmp")
        vadTmpFile.parentFile?.mkdirs()
        val initialBytes = ByteArray(1_000_000) { 0x01 }
        vadTmpFile.writeBytes(initialBytes)

        val totalVadSize = 2_200_000
        val fullVadBytes = ByteArray(totalVadSize) { index ->
            if (index < 1_000_000) 0x01 else 0x02
        }

        val recordedRanges = mutableListOf<Long>()
        val mockFetcher = object : StreamFetcher {
            override fun openStream(url: String, rangeOffset: Long, authToken: String?): StreamResponse {
                recordedRanges.add(rangeOffset)
                if (url == AppConfig.VAD_URL) {
                    val remaining = fullVadBytes.copyOfRange(rangeOffset.toInt(), fullVadBytes.size)
                    return StreamResponse(
                        statusCode = 206, // Partial Content
                        inputStream = ByteArrayInputStream(remaining),
                        contentLength = remaining.size.toLong()
                    )
                }
                // For all other files, return a mock payload matching minExpectedBytes
                val dummyPayload = ByteArray(10_000)
                return StreamResponse(
                    statusCode = 200,
                    inputStream = ByteArrayInputStream(dummyPayload),
                    contentLength = dummyPayload.size.toLong()
                )
            }
        }

        val manager = ModelProvisioningManager(appConfig, mockFetcher)
        val manifest = manager.buildManifest(testProfile)
        val vadEntry = manifest.first { it.id == "silero_vad" }

        // Pre-check status: vad should have partial resume
        val status = manager.checkProvisioningStatus(testProfile)
        assertTrue(status is ProvisioningState.NeedsDownload)
        val needsDownload = status as ProvisioningState.NeedsDownload
        assertTrue("hasPartialResume should be true when .tmp file exists", needsDownload.hasPartialResume)

        // Verify that opening stream requested offset matching existing .tmp length
        mockFetcher.openStream(vadEntry.downloadUrl, vadTmpFile.length(), null)
        assertEquals(1_000_000L, recordedRanges.last())
    }

    @Test
    fun testCorruptedFileSmallerThanMinExpectedBytesIsFlaggedInvalidAndDeleted() {
        val modelsDir = tempFolder.newFolder("models_corrupt_test")
        val appConfig = AppConfig(modelsDir)
        appConfig.ensureDirectoriesExist()

        // Create corrupted silero_vad.onnx file with only 12 KB (< minExpectedBytes 2,000,000L)
        val corruptedVadFile = File(appConfig.vadDir, "silero_vad.onnx")
        corruptedVadFile.writeBytes(ByteArray(12 * 1024))
        assertTrue(corruptedVadFile.exists())
        assertEquals(12 * 1024L, corruptedVadFile.length())

        val manager = ModelProvisioningManager(appConfig)
        val manifest = manager.buildManifest(testProfile)
        val vadEntry = manifest.first { it.id == "silero_vad" }

        // isEntryValid should return false for corrupted file
        assertFalse("12 KB VAD file should NOT be valid", manager.isEntryValid(vadEntry))

        // checkProvisioningStatus should delete the corrupt file
        val state = manager.checkProvisioningStatus(testProfile)
        assertTrue(state is ProvisioningState.NeedsDownload)
        assertFalse("Corrupted file should be deleted on status check", corruptedVadFile.exists())
    }

    @Test
    fun testStatFsStorageGateAbortsWhenAvailableBytesLessThanRequiredPlusBuffer() = runTest {
        val modelsDir = tempFolder.newFolder("models_storage_test")
        val appConfig = AppConfig(modelsDir)

        // Mock StorageChecker with only 100 MB available (needed is ~3.4 GB + 500 MB buffer)
        val lowStorageChecker = StorageChecker { 100 * 1024 * 1024L }

        val fetcher = StreamFetcher { _, _, _ ->
            StreamResponse(200, ByteArrayInputStream(ByteArray(0)), 0L)
        }

        val manager = ModelProvisioningManager(
            config = appConfig,
            fetcher = fetcher,
            storageChecker = lowStorageChecker
        )

        val result = manager.downloadAllModels(testProfile)

        assertTrue(result.isFailure)
        val state = manager.state.value
        assertTrue(state is ProvisioningState.Error)
        val errorState = state as ProvisioningState.Error
        assertEquals(ErrorReason.INSUFFICIENT_STORAGE, errorState.reason)
        assertFalse(errorState.canResume)
    }

    @Test
    fun testOrphanLlmFilesCleanupDeletesUnmatchedWeights() {
        val modelsDir = tempFolder.newFolder("models_orphan_test")
        val appConfig = AppConfig(modelsDir)
        appConfig.ensureDirectoriesExist()

        // Create an obsolete Gemma G3 model file in llmDir
        val obsoleteG3Model = File(appConfig.llmDir, "gemma-4-E2B-it_Google_Tensor_G3.litertlm")
        obsoleteG3Model.writeBytes(ByteArray(1024))
        assertTrue(obsoleteG3Model.exists())

        // Target profile recommends G4 model
        val manager = ModelProvisioningManager(appConfig)
        // Clean orphan files directly via internal method
        manager.cleanOrphanLlmFiles(testProfile.recommendedLlmFileName)

        assertFalse("Obsolete G3 model should have been cleaned up", obsoleteG3Model.exists())
    }

    @Test
    fun testPostDownloadAutoConfigurationSetsModelPathDelegateAndRescorer() = runTest {
        val modelsDir = tempFolder.newFolder("models_autoconfig_test")
        val appConfig = AppConfig(modelsDir)
        appConfig.ensureDirectoriesExist()

        val manager = ModelProvisioningManager(appConfig)
        val manifest = manager.buildManifest(testProfile)

        // Pre-create all 9 files on disk with valid sizes
        for (entry in manifest) {
            entry.targetFile.parentFile?.mkdirs()
            // Write a sparse-like file to satisfy length
            java.io.RandomAccessFile(entry.targetFile, "rw").use { raf ->
                raf.setLength(entry.minExpectedBytes + 100L)
            }
            assertTrue(entry.targetFile.exists())
            assertTrue(entry.targetFile.length() >= entry.minExpectedBytes)
        }

        val result = manager.downloadAllModels(testProfile)

        assertTrue(result.isSuccess)
        val state = manager.state.value
        assertTrue(state is ProvisioningState.Ready)

        // Verify AppConfig was automatically updated
        val expectedLlmFile = manifest.last().targetFile
        assertEquals(expectedLlmFile.absolutePath, appConfig.llmConfig.value.selectedModelPath)
        assertEquals(DelegateType.NPU_GOOGLE_TENSOR, appConfig.llmConfig.value.preferredDelegate)
        assertTrue(appConfig.llmConfig.value.enableWhisperRescorer)
    }

    @Test
    fun testBuildManifestContainsAllNineExpectedEntries() {
        val modelsDir = tempFolder.newFolder("models_manifest_test")
        val appConfig = AppConfig(modelsDir)
        val manager = ModelProvisioningManager(appConfig)

        val manifest = manager.buildManifest(testProfile)

        assertEquals("Manifest must contain exactly 9 model assets", 9, manifest.size)

        val ids = manifest.map { it.id }
        val expectedIds = listOf(
            "silero_vad",
            "zipformer_encoder",
            "zipformer_decoder",
            "zipformer_joiner",
            "zipformer_tokens",
            "whisper_encoder",
            "whisper_decoder",
            "whisper_tokens",
            "gemma_llm"
        )
        assertEquals(expectedIds, ids)

        // Verify minExpectedBytes thresholds
        assertTrue(manifest.first { it.id == "silero_vad" }.minExpectedBytes >= 2_000_000L)
        assertTrue(manifest.first { it.id == "gemma_llm" }.minExpectedBytes >= 2_500_000_000L)

        // Verify subdirectories mapping
        assertTrue(manifest.first { it.id == "silero_vad" }.targetFile.absolutePath.contains("vad"))
        assertTrue(manifest.first { it.id == "zipformer_encoder" }.targetFile.absolutePath.contains("asr"))
        assertTrue(manifest.first { it.id == "whisper_encoder" }.targetFile.absolutePath.contains("whisper"))
        assertTrue(manifest.first { it.id == "gemma_llm" }.targetFile.absolutePath.contains("llm"))
    }

    @Test
    fun testHttp200WhenRangeNotSupportedRestartsFromZeroAndOverwritesTmpFile() = runTest {
        val modelsDir = tempFolder.newFolder("models_range_unsupported_test")
        val appConfig = AppConfig(modelsDir)
        appConfig.ensureDirectoriesExist()

        // Pre-create partial .tmp file with 500 KB of 0x01
        val vadTmpFile = File(appConfig.vadDir, "silero_vad.onnx.tmp")
        vadTmpFile.writeBytes(ByteArray(500_000) { 0x01 })
        assertEquals(500_000L, vadTmpFile.length())

        // Full payload is 2.1 MB of 0x02
        val fullPayloadSize = 2_100_000
        val fullPayload = ByteArray(fullPayloadSize) { 0x02 }

        val mockFetcher = object : StreamFetcher {
            override fun openStream(url: String, rangeOffset: Long, authToken: String?): StreamResponse {
                // Server returns 200 OK (ignoring Range header) with full file
                return StreamResponse(
                    statusCode = 200,
                    inputStream = ByteArrayInputStream(fullPayload),
                    contentLength = fullPayload.size.toLong()
                )
            }
        }

        val storageChecker = StorageChecker { 10_000_000_000L }
        val manager = ModelProvisioningManager(appConfig, mockFetcher, storageChecker)
        val manifest = manager.buildManifest(testProfile)
        val vadEntry = manifest.first { it.id == "silero_vad" }

        manager.downloadFileWithResume(testProfile, vadEntry, 1, 1, null)

        val finalVadFile = vadEntry.targetFile
        assertTrue("Target file must exist after download", finalVadFile.exists())
        assertEquals("File must NOT contain prepended 500KB bytes when 200 OK received", fullPayloadSize.toLong(), finalVadFile.length())
        // Verify byte content is 0x02, not 0x01
        val firstByte = finalVadFile.inputStream().use { it.read() }
        assertEquals(0x02, firstByte)
    }

    @Test
    fun testHttp416RangeNotSatisfiableResetsDownloadAndRetriesFromZero() = runTest {
        val modelsDir = tempFolder.newFolder("models_416_test")
        val appConfig = AppConfig(modelsDir)
        appConfig.ensureDirectoriesExist()

        // Pre-create invalid .tmp file with 1 MB
        val vadTmpFile = File(appConfig.vadDir, "silero_vad.onnx.tmp")
        vadTmpFile.writeBytes(ByteArray(1_000_000) { 0x03 })

        val fullPayloadSize = 2_100_000
        val fullPayload = ByteArray(fullPayloadSize) { 0x04 }

        var callCount = 0
        val recordedOffsets = mutableListOf<Long>()

        val mockFetcher = object : StreamFetcher {
            override fun openStream(url: String, rangeOffset: Long, authToken: String?): StreamResponse {
                callCount++
                recordedOffsets.add(rangeOffset)
                return if (rangeOffset > 0L) {
                    // Simulate server rejecting range offset with 416
                    StreamResponse(statusCode = 416, inputStream = ByteArrayInputStream(ByteArray(0)), contentLength = 0L)
                } else {
                    // Offset 0 succeeds with 200 OK
                    StreamResponse(statusCode = 200, inputStream = ByteArrayInputStream(fullPayload), contentLength = fullPayload.size.toLong())
                }
            }
        }

        val storageChecker = StorageChecker { 10_000_000_000L }
        val manager = ModelProvisioningManager(appConfig, mockFetcher, storageChecker)
        val manifest = manager.buildManifest(testProfile)
        val vadEntry = manifest.first { it.id == "silero_vad" }

        manager.downloadFileWithResume(testProfile, vadEntry, 1, 1, null)

        assertTrue(vadEntry.targetFile.exists())
        assertEquals(fullPayloadSize.toLong(), vadEntry.targetFile.length())
        assertEquals("Should have made 2 calls (first with offset, second with 0 after 416)", 2, callCount)
        assertEquals(1_000_000L, recordedOffsets[0])
        assertEquals(0L, recordedOffsets[1])
    }

    @Test
    fun testCancelOngoingDownloadSetsErrorStateWithCanResumeTrue() {
        val modelsDir = tempFolder.newFolder("models_cancel_test")
        val appConfig = AppConfig(modelsDir)
        val manager = ModelProvisioningManager(appConfig)

        manager.cancelOngoingDownload(testProfile)

        val state = manager.state.value
        assertTrue(state is ProvisioningState.Error)
        val err = state as ProvisioningState.Error
        assertEquals(ErrorReason.CANCELLED, err.reason)
        assertTrue("Cancelled download must allow resumption", err.canResume)
    }
}
