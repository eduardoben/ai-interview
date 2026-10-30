package com.local.interviewassistant.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.local.interviewassistant.core.model.PromptStyle
import com.local.interviewassistant.data.hardware.DelegateType
import com.local.interviewassistant.data.hardware.HardwareProfile
import com.local.interviewassistant.data.hardware.HardwareTier
import com.local.interviewassistant.data.hardware.RamReadinessTier
import com.local.interviewassistant.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Test
    fun testSettingsViewModelUpdates() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val tempFile = File.createTempFile("test_settings_vm", ".preferences_pb")

        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFile }
        )

        val repo = UserPreferencesRepository(externalDataStore = dataStore)
        val profile = HardwareProfile(
            tier = HardwareTier.TPU,
            preferredDelegate = DelegateType.NPU_GOOGLE_TENSOR,
            socName = "Google Tensor G5",
            totalRamMb = 16384L,
            availableRamMb = 12000L,
            recommendedLlmFileName = "gemma-4-E2B-it_Google_Tensor_G5.litertlm",
            recommendedLlmDownloadUrl = "https://example.com/model.litertlm",
            expectedLlmBytes = 3113545589L,
            autoEnableWhisperRescorer = true,
            ramTier = RamReadinessTier.OPTIMAL
        )

        val viewModel = SettingsViewModel(
            preferencesRepository = repo,
            initialHardwareProfile = profile
        )

        advanceUntilIdle()

        assertEquals(profile, viewModel.uiState.value.hardwareProfile)

        viewModel.updateTemperature(0.5f)
        advanceUntilIdle()
        assertEquals(0.5f, viewModel.uiState.value.config.temperature, 0.001f)

        viewModel.updateMaxTokens(300)
        advanceUntilIdle()
        assertEquals(300, viewModel.uiState.value.config.maxTokens)

        viewModel.updatePromptStyle(PromptStyle.VERBOSE)
        advanceUntilIdle()
        assertEquals(PromptStyle.VERBOSE, viewModel.uiState.value.config.promptStyle)

        viewModel.updatePreferredDelegate(DelegateType.NPU_GOOGLE_TENSOR)
        advanceUntilIdle()
        assertEquals(DelegateType.NPU_GOOGLE_TENSOR, viewModel.uiState.value.config.preferredDelegate)

        tempFile.delete()
    }
}
