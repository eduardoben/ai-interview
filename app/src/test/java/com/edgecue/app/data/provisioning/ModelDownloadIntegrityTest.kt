package com.edgecue.app.data.provisioning

import com.edgecue.app.core.model.AppConfig
import com.edgecue.app.data.hardware.DelegateType
import com.edgecue.app.data.hardware.HardwareProfile
import com.edgecue.app.data.hardware.HardwareTier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ModelDownloadIntegrityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testProfile = HardwareProfile(
        tier = HardwareTier.GPU,
        preferredDelegate = DelegateType.GPU_LITERT,
        socName = "Qualcomm Snapdragon 8 Gen 3",
        totalRamMb = 12000L,
        availableRamMb = 6000L,
        recommendedLlmFileName = "gemma-4-E2B-it.litertlm",
        recommendedLlmDownloadUrl = AppConfig.DEFAULT_LLM_URL,
        expectedLlmBytes = AppConfig.GEMMA_GPU_BYTES,
        autoEnableWhisperRescorer = true
    )

    @Test
    fun testComputeSha256MatchesKnownHash() {
        val testFile = tempFolder.newFile("test_sha256.bin")
        val sampleText = "EdgeCue - Model Download Integrity Verification"
        testFile.writeText(sampleText, Charsets.UTF_8)

        val appConfig = AppConfig(tempFolder.root)
        val manager = ModelProvisioningManager(appConfig)

        val computedHash = manager.computeSha256(testFile)

        val md = MessageDigest.getInstance("SHA-256")
        val expectedHash = md.digest(sampleText.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals(expectedHash, computedHash)
        assertEquals(64, computedHash.length)
    }

    @Test
    fun testBuildManifestPopulatesHashesAndSizesForAllNineAssets() {
        val appConfig = AppConfig(tempFolder.root)
        val manager = ModelProvisioningManager(appConfig)
        val manifest = manager.buildManifest(testProfile)

        assertEquals("Manifest must contain exactly 9 models", 9, manifest.size)

        for (entry in manifest) {
            assertNotNull("Entry ${entry.id} must have expectedSha256", entry.expectedSha256)
            assertEquals("SHA-256 for ${entry.id} must be 64 characters", 64, entry.expectedSha256?.length)
            assertTrue("Exact bytes for ${entry.id} must be positive", entry.exactBytes > 0L)
            assertTrue("minExpectedBytes for ${entry.id} must be positive", entry.minExpectedBytes > 0L)
            assertTrue("minExpectedBytes must be <= exactBytes", entry.minExpectedBytes <= entry.exactBytes)
        }
    }

    @Test
    fun testDownloadFailsAndDeletesTmpWhenSha256Mismatches() = runTest {
        val modelsDir = tempFolder.newFolder("models_sha_mismatch")
        val appConfig = AppConfig(modelsDir)

        val payload = ByteArray(2_327_524) { 0x05 }
        val fakeFetcher = StreamFetcher { _, _, _ ->
            StreamResponse(statusCode = 200, inputStream = ByteArrayInputStream(payload), contentLength = payload.size.toLong())
        }

        val manager = ModelProvisioningManager(appConfig, fakeFetcher, verifyChecksums = true)
        val manifest = manager.buildManifest(testProfile)
        val vadEntry = manifest.first { it.id == "silero_vad" }

        val tmpFile = File(appConfig.vadDir, "${vadEntry.targetFile.name}.tmp")

        try {
            manager.downloadFileWithResume(
                profile = testProfile,
                entry = vadEntry,
                fileIndex = 1,
                totalFiles = 1,
                authToken = null
            )
            fail("Expected IOException due to SHA-256 checksum mismatch")
        } catch (e: IOException) {
            assertTrue("Error message should mention integrity verification: ${e.message}",
                e.message?.contains("Integrity verification failed") == true)
        }

        assertFalse("Corrupted .tmp file must be deleted upon checksum failure", tmpFile.exists())
        assertFalse("Target file must not be created upon checksum failure", vadEntry.targetFile.exists())
    }

    @Test
    fun testDownloadFailsAndDeletesTmpWhenExactBytesMismatches() = runTest {
        val modelsDir = tempFolder.newFolder("models_size_mismatch")
        val appConfig = AppConfig(modelsDir)

        // Payload length is 2_100_000 (> minExpectedBytes 2,000,000, but != exactBytes 2,327,524)
        val payload = ByteArray(2_100_000) { 0x06 }
        val fakeFetcher = StreamFetcher { _, _, _ ->
            StreamResponse(statusCode = 200, inputStream = ByteArrayInputStream(payload), contentLength = payload.size.toLong())
        }

        val manager = ModelProvisioningManager(appConfig, fakeFetcher, verifyChecksums = true)
        val manifest = manager.buildManifest(testProfile)
        val vadEntry = manifest.first { it.id == "silero_vad" }

        val tmpFile = File(appConfig.vadDir, "${vadEntry.targetFile.name}.tmp")

        try {
            manager.downloadFileWithResume(
                profile = testProfile,
                entry = vadEntry,
                fileIndex = 1,
                totalFiles = 1,
                authToken = null
            )
            fail("Expected IOException due to size mismatch")
        } catch (e: IOException) {
            assertTrue("Error message should mention size mismatch: ${e.message}",
                e.message?.contains("size mismatch") == true)
        }

        assertFalse("Corrupted .tmp file must be deleted upon size mismatch", tmpFile.exists())
        assertFalse("Target file must not be created upon size mismatch", vadEntry.targetFile.exists())
    }

    @Test
    fun testDownloadSucceedsWhenChecksumAndSizeMatch() = runTest {
        val modelsDir = tempFolder.newFolder("models_integrity_success")
        val appConfig = AppConfig(modelsDir)

        val payload = ByteArray(10_000) { (it % 127).toByte() }
        val md = MessageDigest.getInstance("SHA-256")
        val payloadSha256 = md.digest(payload).joinToString("") { "%02x".format(it) }

        val fakeFetcher = StreamFetcher { _, _, _ ->
            StreamResponse(statusCode = 200, inputStream = ByteArrayInputStream(payload), contentLength = payload.size.toLong())
        }

        val manager = ModelProvisioningManager(appConfig, fakeFetcher, verifyChecksums = true)
        val testTargetFile = File(appConfig.asrDir, "custom_model.bin")
        val entry = ManifestEntry(
            id = "custom_test_model",
            targetFile = testTargetFile,
            downloadUrl = "https://example.com/custom_model.bin",
            estimatedBytes = payload.size.toLong(),
            minExpectedBytes = 5_000L,
            exactBytes = payload.size.toLong(),
            expectedSha256 = payloadSha256
        )

        manager.downloadFileWithResume(
            profile = testProfile,
            entry = entry,
            fileIndex = 1,
            totalFiles = 1,
            authToken = null
        )

        val tmpFile = File(appConfig.asrDir, "custom_model.bin.tmp")
        assertFalse("Temporary .tmp file must be renamed/cleaned up", tmpFile.exists())
        assertTrue("Target file must exist after successful download", testTargetFile.exists())
        assertEquals("Target file must match payload size", payload.size.toLong(), testTargetFile.length())

        val onDiskHash = manager.computeSha256(testTargetFile)
        assertEquals("Target file on disk must match expected SHA-256", payloadSha256, onDiskHash)
    }

    @Test
    fun testLiteRtLmModelDownloaderIntegrityChecks() = runTest {
        val modelsDir = tempFolder.newFolder("litertlm_downloader_test")
        val targetFile = File(modelsDir, "gemma-test.litertlm")
        val payload = ByteArray(20_000) { 0x42 }
        val md = MessageDigest.getInstance("SHA-256")
        val payloadSha256 = md.digest(payload).joinToString("") { "%02x".format(it) }

        val mockFetcher = StreamFetcher { _, _, _ ->
            StreamResponse(statusCode = 200, inputStream = ByteArrayInputStream(payload), contentLength = payload.size.toLong())
        }

        val downloader = LiteRtLmModelDownloader(mockFetcher, verifyChecksums = true)

        // 1. Success case: matching size and SHA-256
        downloader.downloadModelAtomic(
            url = "https://example.com/gemma.litertlm",
            targetFile = targetFile,
            expectedBytes = payload.size.toLong(),
            authToken = null,
            expectedSha256 = payloadSha256,
            onProgress = { _, _ -> }
        )
        assertTrue(targetFile.exists())
        assertEquals(payload.size.toLong(), targetFile.length())

        // 2. Failure case: hash mismatch
        targetFile.delete()
        try {
            downloader.downloadModelAtomic(
                url = "https://example.com/gemma.litertlm",
                targetFile = targetFile,
                expectedBytes = payload.size.toLong(),
                authToken = null,
                expectedSha256 = "1111111111111111111111111111111111111111111111111111111111111111",
                onProgress = { _, _ -> }
            )
            fail("Expected IOException for LiteRtLmModelDownloader SHA mismatch")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Integrity verification failed") == true)
        }
        assertFalse(targetFile.exists())
    }
}
