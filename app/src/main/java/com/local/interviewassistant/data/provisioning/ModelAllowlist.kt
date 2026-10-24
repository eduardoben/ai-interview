package com.local.interviewassistant.data.provisioning

import com.local.interviewassistant.data.llm.BackendSpec

/**
 * Metadata for a `.litertlm` model in the catalog.
 */
data class ModelRegistryEntry(
    val id: String,
    val name: String,
    val url: String,
    val expectedBytes: Long,
    val sha256: String?,
    val backendSpec: BackendSpec
)

/**
 * Registry of known allowed models.
 */
object ModelAllowlist {
    val models = listOf(
        ModelRegistryEntry(
            id = "gemma-4-E2B-it-gpu",
            name = "Gemma 4 E2B IT (GPU)",
            url = "https://huggingface.co/google/gemma-4-E2B-it-litertlm/resolve/main/gemma-4-E2B-it.litertlm",
            expectedBytes = 1438914560L,
            sha256 = null,
            backendSpec = BackendSpec.GPU
        ),
        ModelRegistryEntry(
            id = "gemma-4-E2B-it-tpu",
            name = "Gemma 4 E2B IT (TPU Tensor G5)",
            url = "https://huggingface.co/google/gemma-4-E2B-it-litertlm-tpu/resolve/main/gemma-4-E2B-it-tpu.litertlm",
            expectedBytes = 1538914560L,
            sha256 = null,
            backendSpec = BackendSpec.NPU
        )
    )

    fun getModelById(id: String): ModelRegistryEntry? {
        return models.find { it.id == id }
    }
}
