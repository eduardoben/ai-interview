package com.local.interviewassistant.data.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.local.interviewassistant.data.hardware.DelegateType

object BackendResolver {
    
    /**
     * Resolves the appropriate fallback chain of LiteRT backends for a given model.
     * Maps the logical [BackendSpec] or [DelegateType] down to physical LiteRT-LM [Backend] factories.
     */
    fun getFallbackSequence(
        context: Context,
        isTensorTpuModelFile: Boolean,
        useGpuDelegate: Boolean,
        preferredDelegate: DelegateType? = null
    ): List<Pair<Backend, String>> {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val isGpu = HardwareUtils.isGpuAvailable(context)
        return getFallbackSequence(
            nativeLibDir = nativeLibDir,
            isGpuAvailable = isGpu,
            isTensorTpuModelFile = isTensorTpuModelFile,
            useGpuDelegate = useGpuDelegate,
            preferredDelegate = preferredDelegate
        )
    }

    fun getFallbackSequence(
        nativeLibDir: String?,
        isGpuAvailable: Boolean,
        isTensorTpuModelFile: Boolean,
        useGpuDelegate: Boolean,
        preferredDelegate: DelegateType? = null
    ): List<Pair<Backend, String>> {
        val attemptBackends = mutableListOf<Pair<Backend, String>>()
        
        if (preferredDelegate != null) {
            when (preferredDelegate) {
                DelegateType.CPU_MMAP -> {
                    // CPU memory-mapping: DO NOT attempt GPU buffer allocation!
                    attemptBackends.add(Backend.CPU() to "CPU (Memory-Mapped)")
                    return attemptBackends
                }
                DelegateType.NPU_GOOGLE_TENSOR -> {
                    if (!nativeLibDir.isNullOrBlank()) {
                        attemptBackends.add(Backend.NPU(nativeLibraryDir = nativeLibDir) to "TPU (Tensor NPU with native libs)")
                    }
                    attemptBackends.add(Backend.NPU() to "System NPU")
                    return attemptBackends
                }
                DelegateType.GPU_LITERT -> {
                    if (isGpuAvailable) {
                        attemptBackends.add(Backend.GPU() to "GPU")
                    }
                    attemptBackends.add(Backend.CPU() to "CPU Fallback")
                    return attemptBackends
                }
            }
        }

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
                if (!nativeLibDir.isNullOrBlank()) {
                    attemptBackends.add(Backend.NPU(nativeLibraryDir = nativeLibDir) to "TPU (Tensor NPU with native libs)")
                }
                attemptBackends.add(Backend.NPU() to "System NPU")
            }
            BackendSpec.GPU -> {
                if (isGpuAvailable) {
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
