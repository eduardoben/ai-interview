package com.local.interviewassistant.data.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend

object BackendResolver {
    
    /**
     * Resolves the appropriate fallback chain of LiteRT backends for a given model.
     * Maps the logical [BackendSpec] down to physical LiteRT-LM [Backend] factories.
     */
    fun getFallbackSequence(
        context: Context,
        isTensorTpuModelFile: Boolean,
        useGpuDelegate: Boolean
    ): List<Pair<Backend, String>> {
        val attemptBackends = mutableListOf<Pair<Backend, String>>()
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        
        // Logical preference based on model name and config
        val preferredSpec = if (isTensorTpuModelFile) {
            BackendSpec.NPU
        } else if (useGpuDelegate) {
            BackendSpec.GPU
        } else {
            BackendSpec.CPU
        }

        // Build fallback chain based on actual hardware capability
        when (preferredSpec) {
            BackendSpec.NPU -> {
                // TPU models ONLY support NPU hardware execution.
                attemptBackends.add(Backend.NPU(nativeLibraryDir = nativeLibDir) to "TPU (Tensor NPU with native libs)")
                attemptBackends.add(Backend.NPU() to "System NPU")
            }
            BackendSpec.GPU -> {
                if (HardwareUtils.isGpuAvailable(context)) {
                    attemptBackends.add(Backend.GPU() to "GPU")
                }
                attemptBackends.add(Backend.CPU() to "CPU Fallback")
            }
            BackendSpec.CPU -> {
                attemptBackends.add(Backend.CPU() to "CPU")
            }
        }
        
        return attemptBackends
    }
}
