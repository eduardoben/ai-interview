package com.local.interviewassistant.data.llm

/**
 * Defines the execution backend for LLM inference.
 */
enum class BackendSpec {
    /**
     * Executes entirely on the CPU.
     */
    CPU,

    /**
     * Executes on the GPU using Vulkan or OpenCL.
     */
    GPU,

    /**
     * Executes on the system's Neural Processing Unit (NPU) / Edge TPU.
     */
    NPU
}
