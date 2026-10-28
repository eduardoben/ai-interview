package com.local.interviewassistant.data.llm

import com.google.ai.edge.litertlm.Backend
import com.local.interviewassistant.data.hardware.DelegateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendResolverTest {

    @Test
    fun testCpuMmapYieldsOnlyMemoryMappedCpu() {
        val sequence = BackendResolver.getFallbackSequence(
            nativeLibDir = "/data/app/lib",
            isGpuAvailable = true,
            isTensorTpuModelFile = false,
            useGpuDelegate = true, // even if config requested GPU, preferredDelegate CPU_MMAP overrides it
            preferredDelegate = DelegateType.CPU_MMAP
        )

        assertEquals("CPU_MMAP must yield exactly 1 backend", 1, sequence.size)
        assertEquals("CPU (Memory-Mapped)", sequence[0].second)
        assertTrue(sequence[0].first is Backend.CPU)
    }

    @Test
    fun testGpuLitertYieldsGpuAndCpuFallbackWhenGpuAvailable() {
        val sequence = BackendResolver.getFallbackSequence(
            nativeLibDir = "/data/app/lib",
            isGpuAvailable = true,
            isTensorTpuModelFile = false,
            useGpuDelegate = true,
            preferredDelegate = DelegateType.GPU_LITERT
        )

        assertEquals("GPU_LITERT with GPU available must yield 2 backends", 2, sequence.size)
        assertEquals("GPU", sequence[0].second)
        assertTrue(sequence[0].first is Backend.GPU)
        assertEquals("CPU Fallback", sequence[1].second)
        assertTrue(sequence[1].first is Backend.CPU)
    }

    @Test
    fun testGpuLitertWhenGpuUnavailableYieldsOnlyCpuFallback() {
        val sequence = BackendResolver.getFallbackSequence(
            nativeLibDir = "/data/app/lib",
            isGpuAvailable = false,
            isTensorTpuModelFile = false,
            useGpuDelegate = true,
            preferredDelegate = DelegateType.GPU_LITERT
        )

        assertEquals("GPU_LITERT without GPU must yield 1 backend", 1, sequence.size)
        assertEquals("CPU Fallback", sequence[0].second)
        assertTrue(sequence[0].first is Backend.CPU)
    }

    @Test
    fun testNpuGoogleTensorYieldsNpuWithNativeLibsAndSystemNpu() {
        val sequence = BackendResolver.getFallbackSequence(
            nativeLibDir = "/data/app/lib/arm64",
            isGpuAvailable = true,
            isTensorTpuModelFile = true,
            useGpuDelegate = false,
            preferredDelegate = DelegateType.NPU_GOOGLE_TENSOR
        )

        assertEquals("NPU_GOOGLE_TENSOR must yield 2 NPU backends", 2, sequence.size)
        assertEquals("TPU (Tensor NPU with native libs)", sequence[0].second)
        assertTrue(sequence[0].first is Backend.NPU)
        assertEquals("System NPU", sequence[1].second)
        assertTrue(sequence[1].first is Backend.NPU)
    }

    @Test
    fun testPreferredDelegateNullWithTpuModelFileYieldsNpu() {
        val sequence = BackendResolver.getFallbackSequence(
            nativeLibDir = "/data/app/lib/arm64",
            isGpuAvailable = true,
            isTensorTpuModelFile = true,
            useGpuDelegate = false,
            preferredDelegate = null
        )

        assertEquals(2, sequence.size)
        assertEquals("TPU (Tensor NPU with native libs)", sequence[0].second)
        assertTrue(sequence[0].first is Backend.NPU)
    }

    @Test
    fun testPreferredDelegateNullWithGpuDelegateYieldsGpu() {
        val sequence = BackendResolver.getFallbackSequence(
            nativeLibDir = null,
            isGpuAvailable = true,
            isTensorTpuModelFile = false,
            useGpuDelegate = true,
            preferredDelegate = null
        )

        assertEquals(2, sequence.size)
        assertEquals("GPU", sequence[0].second)
        assertTrue(sequence[0].first is Backend.GPU)
    }

    @Test
    fun testPreferredDelegateNullWithCpuDelegateYieldsCpuOnly() {
        val sequence = BackendResolver.getFallbackSequence(
            nativeLibDir = null,
            isGpuAvailable = true,
            isTensorTpuModelFile = false,
            useGpuDelegate = false,
            preferredDelegate = null
        )

        assertEquals(1, sequence.size)
        assertEquals("CPU", sequence[0].second)
        assertTrue(sequence[0].first is Backend.CPU)
    }
}
