package com.local.interviewassistant.data.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

enum class HardwareTier {
    TPU,
    GPU,
    CPU
}

enum class DelegateType {
    NPU_GOOGLE_TENSOR,
    GPU_LITERT,
    CPU_MMAP
}

data class HardwareProfile(
    val tier: HardwareTier,
    val preferredDelegate: DelegateType,
    val socName: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val recommendedLlmFileName: String,
    val recommendedLlmDownloadUrl: String,
    val expectedLlmBytes: Long,
    val autoEnableWhisperRescorer: Boolean
)

data class SystemDeviceInfo(
    val socModel: String,
    val hardware: String,
    val device: String,
    val model: String,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val hasNpuFeature: Boolean,
    val hasNativeTensorLib: Boolean
)

object DeviceHardwareDetector {

    private const val LLM_BASE_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/"
    const val UNIVERSAL_GPU_LLM_FILENAME = "gemma-4-E2B-it.litertlm"
    const val UNIVERSAL_GPU_LLM_URL = "$LLM_BASE_URL$UNIVERSAL_GPU_LLM_FILENAME"
    const val EXPECTED_GPU_LLM_BYTES = 2_588_147_712L // ~2.58 GB

    const val EXPECTED_TPU_LLM_BYTES = 3_113_545_589L // ~3.11 GB
    const val RAM_THRESHOLD_GPU_MB = 7500L // 8 GB+ devices

    /**
     * Probes live system metrics on Android 17 (API 37) and classifies the hardware profile.
     */
    fun detectHardware(context: Context): HardwareProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val soc = try { Build.SOC_MODEL } catch (_: Throwable) { "" }
        val hw = try { Build.HARDWARE } catch (_: Throwable) { "" }
        val dev = try { Build.DEVICE } catch (_: Throwable) { "" }
        val mdl = try { Build.MODEL } catch (_: Throwable) { "" }

        val hasNpuFeature = try {
            context.packageManager.hasSystemFeature("android.hardware.neural_processing_unit")
        } catch (_: Throwable) {
            false
        }

        val hasNativeTensorLib = checkNativeTensorLib(context)

        val deviceInfo = SystemDeviceInfo(
            socModel = soc,
            hardware = hw,
            device = dev,
            model = mdl,
            totalRamBytes = memInfo.totalMem,
            availableRamBytes = memInfo.availMem,
            hasNpuFeature = hasNpuFeature,
            hasNativeTensorLib = hasNativeTensorLib
        )

        return classify(deviceInfo)
    }

    private fun checkNativeTensorLib(context: Context): Boolean {
        return try {
            val libFile = File(context.applicationInfo.nativeLibraryDir, "libLiteRtDispatch_GoogleTensor.so")
            if (libFile.exists()) return true
            System.loadLibrary("LiteRtDispatch_GoogleTensor")
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Pure, deterministic classification logic based on [SystemDeviceInfo].
     * Completely decoupled from Android platform statics for 100% JVM testability.
     */
    fun classify(info: SystemDeviceInfo): HardwareProfile {
        val totalRamMb = info.totalRamBytes / (1024 * 1024)
        val availableRamMb = info.availableRamBytes / (1024 * 1024)

        val socLower = info.socModel.lowercase().trim()
        val hwLower = info.hardware.lowercase().trim()
        val devLower = info.device.lowercase().trim()
        val mdlLower = info.model.lowercase().trim()

        val isTensorBrand = socLower.contains("tensor") || hwLower.contains("zuma") ||
                devLower.contains("pixel") || mdlLower.contains("pixel")

        // Tensor G1 & G2 exclusion: Explicitly route to GPU/CPU tier
        val isTensorG1OrG2 = isTensorBrand && (
            socLower.contains("g1") || socLower.contains("g2") ||
            hwLower.contains("whitefin") || hwLower.contains("cloudripper") ||
            devLower in listOf("oriole", "raven", "bluejay", "panther", "cheetah", "lynx", "felix", "tangorpro") ||
            mdlLower.contains("pixel 6") || mdlLower.contains("pixel 7")
        )

        // Check if device is Tensor G3..G6 with NPU capability and dispatch library
        if (isTensorBrand && !isTensorG1OrG2 && (info.hasNpuFeature || info.hasNativeTensorLib)) {
            return resolveTensorTpuProfile(
                socLower = socLower,
                hwLower = hwLower,
                devLower = devLower,
                mdlLower = mdlLower,
                totalRamMb = totalRamMb,
                availableRamMb = availableRamMb
            )
        }

        // Memory ceiling evaluation for GPU vs CPU tier
        val effectiveSocName = when {
            info.socModel.isNotBlank() -> info.socModel
            info.hardware.isNotBlank() -> info.hardware
            info.model.isNotBlank() -> info.model
            else -> "Standard Device"
        }

        return if (totalRamMb >= RAM_THRESHOLD_GPU_MB) {
            // GPU Tier (8 GB+ devices)
            HardwareProfile(
                tier = HardwareTier.GPU,
                preferredDelegate = DelegateType.GPU_LITERT,
                socName = effectiveSocName,
                totalRamMb = totalRamMb,
                availableRamMb = availableRamMb,
                recommendedLlmFileName = UNIVERSAL_GPU_LLM_FILENAME,
                recommendedLlmDownloadUrl = UNIVERSAL_GPU_LLM_URL,
                expectedLlmBytes = EXPECTED_GPU_LLM_BYTES,
                autoEnableWhisperRescorer = true
            )
        } else {
            // CPU Tier (6 GB devices / low memory)
            HardwareProfile(
                tier = HardwareTier.CPU,
                preferredDelegate = DelegateType.CPU_MMAP,
                socName = effectiveSocName,
                totalRamMb = totalRamMb,
                availableRamMb = availableRamMb,
                recommendedLlmFileName = UNIVERSAL_GPU_LLM_FILENAME,
                recommendedLlmDownloadUrl = UNIVERSAL_GPU_LLM_URL,
                expectedLlmBytes = EXPECTED_GPU_LLM_BYTES,
                autoEnableWhisperRescorer = false
            )
        }
    }

    private fun resolveTensorTpuProfile(
        socLower: String,
        hwLower: String,
        devLower: String,
        mdlLower: String,
        totalRamMb: Long,
        availableRamMb: Long
    ): HardwareProfile {
        val (generation, fileName) = when {
            socLower.contains("g6") || hwLower.contains("malibu") || mdlLower.contains("pixel 11") ->
                "G6" to "gemma-4-E2B-it_Google_Tensor_G6.litertlm"
            socLower.contains("g5") || hwLower.contains("laguna") || mdlLower.contains("pixel 10") ->
                "G5" to "gemma-4-E2B-it_Google_Tensor_G5.litertlm"
            socLower.contains("g4") || hwLower.contains("zumapro") || mdlLower.contains("pixel 9") ||
            devLower in listOf("tokay", "caiman", "komodo", "comet") ->
                "G4" to "gemma-4-E2B-it_Google_Tensor_G4.litertlm"
            socLower.contains("g3") || hwLower.contains("zuma") || mdlLower.contains("pixel 8") ||
            devLower in listOf("shiba", "husky", "akita") ->
                "G3" to "gemma-4-E2B-it_Google_Tensor_G3.litertlm"
            else ->
                // Default fallback for unidentified Tensor G3+ family
                "G4" to "gemma-4-E2B-it_Google_Tensor_G4.litertlm"
        }

        return HardwareProfile(
            tier = HardwareTier.TPU,
            preferredDelegate = DelegateType.NPU_GOOGLE_TENSOR,
            socName = "Google Tensor $generation",
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            recommendedLlmFileName = fileName,
            recommendedLlmDownloadUrl = "$LLM_BASE_URL$fileName",
            expectedLlmBytes = EXPECTED_TPU_LLM_BYTES,
            autoEnableWhisperRescorer = true
        )
    }
}
