package com.local.interviewassistant.ui.prompter

import com.local.interviewassistant.core.model.AppConfig
import com.local.interviewassistant.core.model.AssistantState
import com.local.interviewassistant.core.model.InterviewCategory
import com.local.interviewassistant.core.model.TranscriptSegment
import com.local.interviewassistant.data.audio.AudioSource
import com.local.interviewassistant.data.llm.LlmEngine
import com.local.interviewassistant.data.llm.LlmStreamEvent
import com.local.interviewassistant.data.stt.SherpaVadSttEngine
import com.local.interviewassistant.data.stt.VadSttBackend
import com.local.interviewassistant.service.AssistantPipelineCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PrompterViewModelTest {

    private class TestAudioSource : AudioSource {
        override fun audioStream(): Flow<FloatArray> = flow { }
    }

    private class TestVadBackend : VadSttBackend {
        override suspend fun initialize(config: AppConfig): Result<Unit> = Result.success(Unit)
        override fun acceptWaveform(samples: FloatArray, sampleRate: Int): Boolean = false
        override fun getPartialTranscript(): String = ""
        override fun finalizeAndResetStream(): String = ""
        override fun release() {}
    }

    private class TestLlmEngine : LlmEngine {
        private val _isInit = MutableStateFlow(true)
        override val isInitialized: StateFlow<Boolean> = _isInit.asStateFlow()
        var lastQuestion: String? = null

        override suspend fun initialize(): Result<Unit> = Result.success(Unit)
        override fun generateAnswerStream(question: String): Flow<LlmStreamEvent> = flow {
            lastQuestion = question
            emit(LlmStreamEvent.PartialToken("Answer", "Answer", 20L))
            emit(LlmStreamEvent.Completed("Answer", 20L, 50L))
        }
        override suspend fun release() {}
    }

    @Test
    fun testInitialUiState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val tempDir = File.createTempFile("test_prompter", "dir").apply { delete(); mkdirs() }
        val config = AppConfig(tempDir)

        val coordinator = AssistantPipelineCoordinator(
            audioSource = TestAudioSource(),
            sttEngine = SherpaVadSttEngine(config, TestVadBackend()),
            llmEngine = TestLlmEngine(),
            scope = scope,
            appConfig = config
        )

        val runningFlow = MutableStateFlow(false)
        val viewModel = PrompterViewModel(
            coordinator = coordinator,
            isServiceRunningFlow = runningFlow,
            hardwareSocName = "Google Tensor G5",
            coroutineScope = scope
        )

        advanceUntilIdle()

        assertEquals("Google Tensor G5", viewModel.uiState.value.hardwareSocName)
        assertFalse(viewModel.uiState.value.isServiceRunning)
        assertFalse(viewModel.uiState.value.isFocusMode)
        assertEquals(InterviewCategory.GENERAL, viewModel.uiState.value.copilotState.selectedCategory)

        tempDir.deleteRecursively()
    }

    @Test
    fun testCategorySelectionUpdatesState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val tempDir = File.createTempFile("test_prompter_cat", "dir").apply { delete(); mkdirs() }
        val config = AppConfig(tempDir)

        val coordinator = AssistantPipelineCoordinator(
            audioSource = TestAudioSource(),
            sttEngine = SherpaVadSttEngine(config, TestVadBackend()),
            llmEngine = TestLlmEngine(),
            scope = scope,
            appConfig = config
        )

        val viewModel = PrompterViewModel(
            coordinator = coordinator,
            hardwareSocName = "Google Tensor G5",
            coroutineScope = scope
        )

        advanceUntilIdle()

        viewModel.onSelectCategory(InterviewCategory.SYSTEM_DESIGN)
        advanceUntilIdle()

        assertEquals(InterviewCategory.SYSTEM_DESIGN, coordinator.copilotUiState.value.selectedCategory)
        assertEquals(InterviewCategory.SYSTEM_DESIGN, viewModel.uiState.value.copilotState.selectedCategory)

        tempDir.deleteRecursively()
    }

    @Test
    fun testCustomQuestionSubmission() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val tempDir = File.createTempFile("test_prompter_q", "dir").apply { delete(); mkdirs() }
        val config = AppConfig(tempDir)
        val fakeLlm = TestLlmEngine()

        val coordinator = AssistantPipelineCoordinator(
            audioSource = TestAudioSource(),
            sttEngine = SherpaVadSttEngine(config, TestVadBackend()),
            llmEngine = fakeLlm,
            scope = scope,
            appConfig = config
        )

        val viewModel = PrompterViewModel(
            coordinator = coordinator,
            hardwareSocName = "Google Tensor G5",
            coroutineScope = scope
        )

        viewModel.onUpdateCustomQuestionInput("How does Paxos consensus work?")
        assertEquals("How does Paxos consensus work?", viewModel.uiState.value.customQuestionInput)

        viewModel.onSubmitCustomQuestion("How does Paxos consensus work?")
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.customQuestionInput)
        assertEquals("How does Paxos consensus work?", fakeLlm.lastQuestion)

        tempDir.deleteRecursively()
    }

    @Test
    fun testFocusModeAndDimmerDialogToggles() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val tempDir = File.createTempFile("test_prompter_toggles", "dir").apply { delete(); mkdirs() }
        val config = AppConfig(tempDir)

        val coordinator = AssistantPipelineCoordinator(
            audioSource = TestAudioSource(),
            sttEngine = SherpaVadSttEngine(config, TestVadBackend()),
            llmEngine = TestLlmEngine(),
            scope = scope,
            appConfig = config
        )

        val viewModel = PrompterViewModel(
            coordinator = coordinator,
            hardwareSocName = "Google Tensor G5",
            coroutineScope = scope
        )

        assertFalse(viewModel.uiState.value.isFocusMode)
        viewModel.onToggleFocusMode()
        assertTrue(viewModel.uiState.value.isFocusMode)
        viewModel.onToggleFocusMode()
        assertFalse(viewModel.uiState.value.isFocusMode)

        assertFalse(viewModel.uiState.value.showDimmerDialog)
        viewModel.onToggleDimmerDialog(true)
        assertTrue(viewModel.uiState.value.showDimmerDialog)
        viewModel.onToggleDimmerDialog(false)
        assertFalse(viewModel.uiState.value.showDimmerDialog)

        tempDir.deleteRecursively()
    }
}
