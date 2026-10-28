package com.local.interviewassistant.data.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHardwareDetectorTest {

    @Test
    fun testTensorG4ResolutionRoutesToTpuAndSpecificModel() {
        val deviceInfo = SystemDeviceInfo(
            socModel = "Tensor G4",
            hardware = "zumapro",
            device = "caiman",
            model = "Pixel 9 Pro",
            totalRamBytes = 16_000_000_000L,
            availableRamBytes = 8_000_000_000L,
            hasNpuFeature = true,
            hasNativeTensorLib = true
        )

        val profile = DeviceHardwareDetector.classify(deviceInfo)

        assertEquals(HardwareTier.TPU, profile.tier)
        assertEquals(DelegateType.NPU_GOOGLE_TENSOR, profile.preferredDelegate)
        assertEquals("gemma-4-E2B-it_Google_Tensor_G4.litertlm", profile.recommendedLlmFileName)
        assertTrue(profile.recommendedLlmDownloadUrl.endsWith("gemma-4-E2B-it_Google_Tensor_G4.litertlm"))
        assertEquals(DeviceHardwareDetector.EXPECTED_TPU_LLM_BYTES, profile.expectedLlmBytes)
        assertTrue(profile.autoEnableWhisperRescorer)
    }

    @Test
    fun testTensorG3ResolutionRoutesToTpuAndG3Model() {
        val deviceInfo = SystemDeviceInfo(
            socModel = "Tensor G3",
            hardware = "zuma",
            device = "husky",
            model = "Pixel 8 Pro",
            totalRamBytes = 12_000_000_000L,
            availableRamBytes = 6_000_000_000L,
            hasNpuFeature = true,
            hasNativeTensorLib = true
        )

        val profile = DeviceHardwareDetector.classify(deviceInfo)

        assertEquals(HardwareTier.TPU, profile.tier)
        assertEquals(DelegateType.NPU_GOOGLE_TENSOR, profile.preferredDelegate)
        assertEquals("gemma-4-E2B-it_Google_Tensor_G3.litertlm", profile.recommendedLlmFileName)
        assertTrue(profile.recommendedLlmDownloadUrl.endsWith("gemma-4-E2B-it_Google_Tensor_G3.litertlm"))
    }

    @Test
    fun testTensorG5ResolutionRoutesToTpuAndG5Model() {
        val deviceInfo = SystemDeviceInfo(
            socModel = "Tensor G5",
            hardware = "laguna",
            device = "frankel",
            model = "Pixel 10 Pro",
            totalRamBytes = 16_000_000_000L,
            availableRamBytes = 10_000_000_000L,
            hasNpuFeature = true,
            hasNativeTensorLib = true
        )

        val profile = DeviceHardwareDetector.classify(deviceInfo)

        assertEquals(HardwareTier.TPU, profile.tier)
        assertEquals(DelegateType.NPU_GOOGLE_TENSOR, profile.preferredDelegate)
        assertEquals("gemma-4-E2B-it_Google_Tensor_G5.litertlm", profile.recommendedLlmFileName)
        assertTrue(profile.recommendedLlmDownloadUrl.endsWith("gemma-4-E2B-it_Google_Tensor_G5.litertlm"))
    }

    @Test
    fun testTensorG6ResolutionRoutesToTpuAndG6Model() {
        val deviceInfo = SystemDeviceInfo(
            socModel = "Tensor G6",
            hardware = "malibu",
            device = "pixel11",
            model = "Pixel 11 Pro",
            totalRamBytes = 16_000_000_000L,
            availableRamBytes = 10_000_000_000L,
            hasNpuFeature = true,
            hasNativeTensorLib = true
        )

        val profile = DeviceHardwareDetector.classify(deviceInfo)

        assertEquals(HardwareTier.TPU, profile.tier)
        assertEquals(DelegateType.NPU_GOOGLE_TENSOR, profile.preferredDelegate)
        assertEquals("gemma-4-E2B-it_Google_Tensor_G6.litertlm", profile.recommendedLlmFileName)
        assertTrue(profile.recommendedLlmDownloadUrl.endsWith("gemma-4-E2B-it_Google_Tensor_G6.litertlm"))
    }

    @Test
    fun testTensorG1AndG2FallbackToGpuOn8GbDevice() {
        // Pixel 6 (Tensor G1, oriole)
        val pixel6 = SystemDeviceInfo(
            socModel = "Tensor",
            hardware = "whitefin",
            device = "oriole",
            model = "Pixel 6",
            totalRamBytes = 8_000_000_000L,
            availableRamBytes = 4_000_000_000L,
            hasNpuFeature = true,
            hasNativeTensorLib = true
        )
        val profile6 = DeviceHardwareDetector.classify(pixel6)
        assertEquals(HardwareTier.GPU, profile6.tier)
        assertEquals(DelegateType.GPU_LITERT, profile6.preferredDelegate)
        assertEquals(DeviceHardwareDetector.UNIVERSAL_GPU_LLM_FILENAME, profile6.recommendedLlmFileName)
        assertEquals(DeviceHardwareDetector.UNIVERSAL_GPU_LLM_URL, profile6.recommendedLlmDownloadUrl)
        assertTrue(profile6.autoEnableWhisperRescorer)

        // Pixel 7 (Tensor G2, panther)
        val pixel7 = SystemDeviceInfo(
            socModel = "Tensor G2",
            hardware = "cloudripper",
            device = "panther",
            model = "Pixel 7",
            totalRamBytes = 8_000_000_000L,
            availableRamBytes = 4_000_000_000L,
            hasNpuFeature = true,
            hasNativeTensorLib = true
        )
        val profile7 = DeviceHardwareDetector.classify(pixel7)
        assertEquals(HardwareTier.GPU, profile7.tier)
        assertEquals(DelegateType.GPU_LITERT, profile7.preferredDelegate)
        assertEquals(DeviceHardwareDetector.UNIVERSAL_GPU_LLM_FILENAME, profile7.recommendedLlmFileName)
    }

    @Test
    fun testAndroid17LowMemoryDeviceCeilingRoutesToCpuMmap() {
        // 6 GB physical RAM device (~5.8 GB reported)
        val lowRamDevice = SystemDeviceInfo(
            socModel = "SM8450",
            hardware = "taro",
            device = "phone",
            model = "Mid-tier Android 17 Phone",
            totalRamBytes = 5_800_000_000L,
            availableRamBytes = 2_000_000_000L,
            hasNpuFeature = false,
            hasNativeTensorLib = false
        )

        val profile = DeviceHardwareDetector.classify(lowRamDevice)

        assertEquals(HardwareTier.CPU, profile.tier)
        assertEquals(DelegateType.CPU_MMAP, profile.preferredDelegate)
        assertEquals(DeviceHardwareDetector.UNIVERSAL_GPU_LLM_FILENAME, profile.recommendedLlmFileName)
        assertFalse(profile.autoEnableWhisperRescorer)
    }

    @Test
    fun testMissingNativeNpuLibOnTensorRoutesToGpuTier() {
        val tensorWithoutNpuLib = SystemDeviceInfo(
            socModel = "Tensor G4",
            hardware = "zumapro",
            device = "caiman",
            model = "Pixel 9",
            totalRamBytes = 12_000_000_000L,
            availableRamBytes = 6_000_000_000L,
            hasNpuFeature = false,
            hasNativeTensorLib = false
        )

        val profile = DeviceHardwareDetector.classify(tensorWithoutNpuLib)

        assertEquals(HardwareTier.GPU, profile.tier)
        assertEquals(DelegateType.GPU_LITERT, profile.preferredDelegate)
        assertEquals(DeviceHardwareDetector.UNIVERSAL_GPU_LLM_FILENAME, profile.recommendedLlmFileName)
    }

    @Test
    fun testExact7500MbRamBoundaryClassifiesAsGpuTier() {
        // Exactly 7500 MB (>= 7500 MB)
        val ram7500Mb = SystemDeviceInfo(
            socModel = "SM8550",
            hardware = "kalama",
            device = "galaxy_s23",
            model = "Galaxy S23",
            totalRamBytes = 7500L * 1024L * 1024L,
            availableRamBytes = 4000L * 1024L * 1024L,
            hasNpuFeature = false,
            hasNativeTensorLib = false
        )
        val profile = DeviceHardwareDetector.classify(ram7500Mb)
        assertEquals(HardwareTier.GPU, profile.tier)
        assertEquals(DelegateType.GPU_LITERT, profile.preferredDelegate)
        assertTrue("Whisper rescorer should be enabled on 7500MB+ devices", profile.autoEnableWhisperRescorer)
    }

    @Test
    fun testExact7499MbRamBoundaryClassifiesAsCpuMmapTier() {
        // Exactly 7499 MB (< 7500 MB)
        val ram7499Mb = SystemDeviceInfo(
            socModel = "SM8550",
            hardware = "kalama",
            device = "galaxy_s23_6gb",
            model = "Galaxy S23 6GB",
            totalRamBytes = 7499L * 1024L * 1024L,
            availableRamBytes = 2000L * 1024L * 1024L,
            hasNpuFeature = false,
            hasNativeTensorLib = false
        )
        val profile = DeviceHardwareDetector.classify(ram7499Mb)
        assertEquals(HardwareTier.CPU, profile.tier)
        assertEquals(DelegateType.CPU_MMAP, profile.preferredDelegate)
        assertFalse("Whisper rescorer should be disabled by default on <7500MB devices", profile.autoEnableWhisperRescorer)
    }

    @Test
    fun testCaseInsensitiveAndWhitespacePaddedSocModelDetection() {
        val paddedTensor = SystemDeviceInfo(
            socModel = "  Google Tensor G4 (Zuma Pro)  ",
            hardware = " ZUMAPRO ",
            device = " Caiman ",
            model = " Pixel 9 Pro ",
            totalRamBytes = 16_000_000_000L,
            availableRamBytes = 8_000_000_000L,
            hasNpuFeature = true,
            hasNativeTensorLib = true
        )
        val profile = DeviceHardwareDetector.classify(paddedTensor)
        assertEquals(HardwareTier.TPU, profile.tier)
        assertEquals(DelegateType.NPU_GOOGLE_TENSOR, profile.preferredDelegate)
        assertEquals("gemma-4-E2B-it_Google_Tensor_G4.litertlm", profile.recommendedLlmFileName)
    }
}
