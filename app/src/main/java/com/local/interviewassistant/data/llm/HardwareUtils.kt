package com.local.interviewassistant.data.llm

import android.content.Context
import android.os.Build
import java.util.Locale

object HardwareUtils {

    private val TENSOR_G3_OR_HIGHER_CODENAMES = setOf(
        // Tensor G3 (Pixel 8 / 8 Pro / 8a)
        "shiba", "husky", "akita", "zuma",
        // Tensor G4 (Pixel 9 / 9 Pro / 9 Pro XL / 9 Pro Fold / 9a)
        "tokay", "caiman", "komodo", "comet", "tegu", "zumapro",
        // Tensor G5 (Pixel 10 / 10 Pro / 10 Pro XL / 10 Pro Fold)
        "frankel", "blazer", "mustang", "rango", "laguna",
        // Tensor G6 (Pixel 11)
        "malibu"
    )

    /**
     * Detects if the current device is Google Tensor G3 or higher.
     */
    fun isTensorG3OrHigher(): Boolean {
        val lowerModel = Build.MODEL.lowercase(Locale.ROOT)
        val lowerDevice = Build.DEVICE.lowercase(Locale.ROOT)
        val lowerBoard = Build.BOARD.lowercase(Locale.ROOT)
        val lowerSocModel = runCatching { Build.SOC_MODEL }.getOrNull().orEmpty().lowercase(Locale.ROOT)
        val lowerHardware = Build.HARDWARE.lowercase(Locale.ROOT)

        val socManufacturer = runCatching { Build.SOC_MANUFACTURER }.getOrNull().orEmpty()
        val isGoogle = socManufacturer.ifBlank { Build.MANUFACTURER }.equals("Google", ignoreCase = true)
        
        // Check Pixel model name
        val pixelMatch = Regex("""pixel\s+(\d+)""").find(lowerModel)
        if (pixelMatch != null) {
            val generation = pixelMatch.groupValues[1].toIntOrNull() ?: 0
            if (generation >= 8) return true
        }

        // Check Tensor SoC model
        val tensorMatch = Regex("""tensor\s+g?(\d+)""").find(lowerSocModel)
        if (tensorMatch != null) {
            val gen = tensorMatch.groupValues[1].toIntOrNull() ?: 0
            if (gen >= 3) return true
        }

        return isGoogle && (
                lowerDevice in TENSOR_G3_OR_HIGHER_CODENAMES ||
                lowerBoard in TENSOR_G3_OR_HIGHER_CODENAMES ||
                lowerHardware in TENSOR_G3_OR_HIGHER_CODENAMES
        )
    }

    /**
     * Checks if a Neural Processing Unit (NPU) is available on the device.
     */
    @Suppress("unused")
    fun isTpuAvailable(context: Context): Boolean {
        // Strict API 37+ feature check
        if (context.packageManager.hasSystemFeature("android.hardware.neural_processing_unit")) {
            return true
        }
        
        // EdgeTPU requires native NPU dispatch libraries which are bundled in the app or system
        // The litertlm JNI loader looks for libedgetpu_client.google.so
        return isTensorG3OrHigher()
    }

    @Suppress("UNUSED_PARAMETER")
    fun isGpuAvailable(context: Context): Boolean {
        return true
    }
}

