package com.local.interviewassistant.data.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import com.local.interviewassistant.core.model.AppConfig
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

sealed interface LlmStreamEvent {
    data class PartialToken(val tokenChunk: String, val accumulatedText: String, val ttftMs: Long) : LlmStreamEvent
    data class Completed(val fullText: String, val ttftMs: Long, val totalDurationMs: Long) : LlmStreamEvent
    data class Error(val throwable: Throwable) : LlmStreamEvent
}

interface LlmInferenceBackend {
    fun initialize(config: AppConfig)
    fun generateAsync(prompt: String, onPartialResult: (partialText: String, done: Boolean) -> Unit, onError: (Throwable) -> Unit)
    fun cancel()
    fun close()
}

@Suppress("DEPRECATION")
class MediaPipeLiteRtBackend(private val context: Context) : LlmInferenceBackend {
    private var llmInference: LlmInference? = null
    private var isGpuNpuAccelerated: Boolean = false
    
    private var isGenerating = false
    private val generationLock = ReentrantLock()
    private val generationCondition = generationLock.newCondition()

    @Volatile private var currentGenerationId = 0

    private fun waitForCompletion() {
        generationLock.withLock {
            while (isGenerating) {
                generationCondition.await()
            }
        }
    }

    private fun setGenerating(generating: Boolean) = generationLock.withLock {
        isGenerating = generating
        if (!generating) {
            generationCondition.signalAll()
        }
    }

    override fun initialize(config: AppConfig) {
        val modelFile = config.activeLlmModelFile
        if (!modelFile.exists() || modelFile.length() <= 0L) {
            throw IllegalStateException("LLM model file missing at ${modelFile.absolutePath}")
        }

        if (modelFile.name.endsWith(".litertlm", ignoreCase = true)) {
            throw IllegalStateException(
                "MediaPipe LlmInference cannot parse '.litertlm' models (requires .bin or .task files)."
            )
        }

        waitForCompletion()
        llmInference?.close()
        llmInference = null

        val backendsToTry = if (config.useGpuDelegate) {
            listOf(
                LlmInference.Backend.DEFAULT,
                LlmInference.Backend.GPU,
                LlmInference.Backend.CPU
            )
        } else {
            listOf(
                LlmInference.Backend.CPU,
                LlmInference.Backend.DEFAULT
            )
        }

        val errors = mutableListOf<String>()
        for (backend in backendsToTry) {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(config.maxTokens)
                    .setMaxTopK(config.topK)
                    .setPreferredBackend(backend)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                isGpuNpuAccelerated = (backend != LlmInference.Backend.CPU)
                return
            } catch (t: Throwable) {
                val msg = t.message ?: t.toString()
                errors.add("$backend: $msg")
            }
        }

        throw IllegalStateException(
            "Failed to initialize LiteRT LLM Inference on all backends. Attempts: ${errors.joinToString("; ")}"
        )
    }

    override fun generateAsync(
        prompt: String,
        onPartialResult: (partialText: String, done: Boolean) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        waitForCompletion()

        val inference = llmInference
        if (inference == null) {
            onError(IllegalStateException("LlmInference not initialized"))
            return
        }

        val genId = ++currentGenerationId
        setGenerating(true)

        try {
            val progressListener = ProgressListener<String> { partialResult, done ->
                if (currentGenerationId == genId) {
                    onPartialResult(partialResult ?: "", done)
                }
                if (done) {
                    setGenerating(false)
                }
            }
            val future = inference.generateResponseAsync(prompt, progressListener)
            future.addListener(
                {
                    try {
                        future.get()
                    } catch (e: Exception) {
                        if (currentGenerationId == genId) {
                            onError(e)
                        }
                    } finally {
                        setGenerating(false)
                    }
                },
            ) { command ->
                Thread(command).start()
            }
        } catch (e: Exception) {
            setGenerating(false)
            onError(e)
        }
    }

    override fun cancel() {
        // MediaPipe doesn't support pre-emption. We just drop the callbacks for the current generation.
        currentGenerationId++
    }

    override fun close() {
        waitForCompletion()
        llmInference?.close()
        llmInference = null
        isGpuNpuAccelerated = false
    }
}
