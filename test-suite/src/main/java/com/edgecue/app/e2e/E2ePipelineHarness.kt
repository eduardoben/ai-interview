package com.edgecue.app.e2e

import android.content.Context
import com.edgecue.app.core.di.AppContainer
import com.edgecue.app.core.model.AppConfig
import com.edgecue.app.core.model.AssistantState
import com.edgecue.app.core.model.CopilotUiState
import com.edgecue.app.data.audio.AudioSource
import com.edgecue.app.data.history.SessionHistoryRepository
import com.edgecue.app.data.llm.LiteRtGemmaEngine
import com.edgecue.app.data.llm.LlmEngine
import com.edgecue.app.data.stt.SherpaVadSttEngine
import com.edgecue.app.service.AssistantPipelineCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

/**
 * Thread-safe wrapper preventing per-turn [AssistantPipelineCoordinator.stop] from
 * unloading the 2.58 GB native LiteRT-LM C++ model on x86_64 CPU emulators.
 *
 * Implements [LlmEngine] via delegation. The standard [release] method is an intentional NO-OP
 * so per-turn coordinator teardowns do not unload native weights.
 * Explicit full teardown is performed via [releaseUnderlyingEngine] or [tearDownCompletely]
 * during test class completion (@AfterClass).
 */
class SharedWarmLlmEngineWrapper(
    val delegate: LlmEngine
) : LlmEngine by delegate {

    constructor(liteRtEngine: LiteRtGemmaEngine) : this(liteRtEngine as LlmEngine)

    override suspend fun initialize(): Result<Unit> {
        return if (delegate.isInitialized.value) {
            Result.success(Unit) // Instant 0 ms return if already warmed
        } else {
            delegate.initialize()
        }
    }

    override suspend fun release() {
        // Intentionally NO-OP: Coordinator stop() will not unload the warm native C++ model.
    }

    /**
     * Warms up the underlying engine, optionally running a prompt generation pass.
     */
    suspend fun warmUp(warmupPrompt: String? = null): Result<Unit> {
        val initRes = if (delegate.isInitialized.value) Result.success(Unit) else delegate.initialize()
        if (initRes.isFailure) return initRes
        if (!warmupPrompt.isNullOrBlank()) {
            runCatching {
                delegate.generateAnswerStream(warmupPrompt).collect { }
            }
        }
        return Result.success(Unit)
    }

    /**
     * Unconditionally releases the underlying engine. Intended for @AfterClass execution.
     */
    suspend fun releaseUnderlyingEngine() {
        delegate.release()
    }

    suspend fun tearDownCompletely() {
        releaseUnderlyingEngine()
    }

    fun releaseUnderlyingEngineBlocking() {
        runBlocking {
            releaseUnderlyingEngine()
        }
    }
}

/**
 * Test harness managing the lifecycle of an [AssistantPipelineCoordinator] instance
 * in end-to-end tests without modifying production source code.
 *
 * Features:
 * - Instantiates [AssistantPipelineCoordinator] with public constructor seams.
 * - Test-scoped [SupervisorJob] coroutine scope preventing cross-test job leaks.
 * - Integration with [SharedWarmLlmEngineWrapper] for zero-latency turn-to-turn warmup.
 * - Prevents Room DB pollution by passing `historyRepository = null` by default.
 * - Clean start, stop, manual trigger, and underlying engine release controls.
 */
class E2ePipelineHarness(
    val audioSource: AudioSource,
    val sttEngine: SherpaVadSttEngine,
    val llmEngine: LlmEngine,
    val appConfig: AppConfig? = null,
    val historyRepository: SessionHistoryRepository? = null,
    val testDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * Secondary constructor bootstrapping defaults from [AppContainer].
     */
    constructor(
        context: Context,
        audioSource: AudioSource,
        sttEngine: SherpaVadSttEngine = AppContainer.getInstance(context).createSttEngine(),
        llmEngine: LlmEngine = AppContainer.getInstance(context).createLlmEngine(),
        appConfig: AppConfig = AppContainer.getInstance(context).appConfig,
        historyRepository: SessionHistoryRepository? = null,
        testDispatcher: CoroutineDispatcher = Dispatchers.Default
    ) : this(
        audioSource = audioSource,
        sttEngine = sttEngine,
        llmEngine = llmEngine,
        appConfig = appConfig,
        historyRepository = historyRepository,
        testDispatcher = testDispatcher
    )

    private val testJob = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(testJob + testDispatcher)

    val coordinator: AssistantPipelineCoordinator = AssistantPipelineCoordinator(
        audioSource = audioSource,
        sttEngine = sttEngine,
        llmEngine = llmEngine,
        scope = scope,
        appConfig = appConfig,
        historyRepository = historyRepository
    )

    val state: StateFlow<AssistantState> get() = coordinator.state
    val copilotUiState: StateFlow<CopilotUiState> get() = coordinator.copilotUiState
    val isThermalThrottled: StateFlow<Boolean> get() = coordinator.isThermalThrottled

    val scriptedAudioSource: ScriptedWavAudioSource?
        get() = audioSource as? ScriptedWavAudioSource

    suspend fun start(): Result<Unit> = runCatching {
        coordinator.start()
    }

    fun startBlocking(): Result<Unit> = runCatching {
        runBlocking { coordinator.start() }
    }

    suspend fun stop() {
        runCatching { coordinator.stop() }
        testJob.cancel()
    }

    fun stopBlocking() {
        runBlocking {
            stop()
        }
    }

    fun triggerAnswerGeneration(question: String) {
        coordinator.triggerAnswerGeneration(question)
    }

    fun clearCurrentAnswer() {
        coordinator.clearCurrentAnswer()
    }

    suspend fun resetForNextTurn() {
        coordinator.clearCurrentAnswer()
    }

    /**
     * Explicitly releases the underlying LLM engine.
     */
    suspend fun releaseUnderlyingEngine() {
        if (llmEngine is SharedWarmLlmEngineWrapper) {
            llmEngine.releaseUnderlyingEngine()
        } else {
            llmEngine.release()
        }
    }

    fun releaseUnderlyingEngineBlocking() {
        runBlocking {
            releaseUnderlyingEngine()
        }
    }

    companion object {
        /**
         * Creates and warms a [SharedWarmLlmEngineWrapper] for use across tests or turns.
         * Ideal for @BeforeClass setup.
         */
        suspend fun createAndWarmLlmEngine(
            context: Context,
            appConfig: AppConfig? = null,
            warmupPrompt: String? = null
        ): SharedWarmLlmEngineWrapper {
            val config = appConfig ?: AppContainer.getInstance(context).appConfig
            val rawEngine = LiteRtGemmaEngine(context, config)
            val wrapper = SharedWarmLlmEngineWrapper(rawEngine)
            wrapper.warmUp(warmupPrompt).getOrThrow()
            return wrapper
        }

        /**
         * Blocking variant of [createAndWarmLlmEngine] for non-suspending JUnit @BeforeClass methods.
         */
        fun createAndWarmLlmEngineBlocking(
            context: Context,
            appConfig: AppConfig? = null,
            warmupPrompt: String? = null
        ): SharedWarmLlmEngineWrapper = runBlocking {
            createAndWarmLlmEngine(context, appConfig, warmupPrompt)
        }

        /**
         * Releases the underlying engine cleanly. Ideal for JUnit @AfterClass teardown.
         */
        suspend fun releaseUnderlyingEngine(wrapper: SharedWarmLlmEngineWrapper) {
            wrapper.releaseUnderlyingEngine()
        }

        /**
         * Blocking variant of [releaseUnderlyingEngine] for non-suspending JUnit @AfterClass methods.
         */
        fun releaseUnderlyingEngineBlocking(wrapper: SharedWarmLlmEngineWrapper) {
            wrapper.releaseUnderlyingEngineBlocking()
        }
    }
}
