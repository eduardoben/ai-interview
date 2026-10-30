package com.local.interviewassistant.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.local.interviewassistant.core.model.LlmGenerationConfig
import com.local.interviewassistant.core.model.PromptStyle
import com.local.interviewassistant.data.hardware.DelegateType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {

    @Test
    fun testSaveAndReadPreferences() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val tempFile = File.createTempFile("test_prefs", ".preferences_pb")

        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFile }
        )

        val repo = UserPreferencesRepository(
            externalDataStore = dataStore
        )

        val initialConfig = repo.userPreferencesFlow.first()
        assertEquals(0.2f, initialConfig.temperature, 0.001f)
        assertEquals(200, initialConfig.maxTokens)
        assertEquals(PromptStyle.CONCISE, initialConfig.promptStyle)

        val updatedConfig = LlmGenerationConfig(
            temperature = 0.7f,
            topK = 40,
            maxTokens = 350,
            promptStyle = PromptStyle.VERBOSE,
            interviewContext = "Senior Android Lead Role",
            preferredDelegate = DelegateType.NPU_GOOGLE_TENSOR,
            enableWhisperRescorer = false,
            teleprompterFontSizeSp = 24f,
            stealthDimmerAlpha = 0.35f,
            enableVolumeKeyShortcuts = false,
            keepScreenOn = false,
            minSilenceDurationSec = 1.5f,
            enableHardwareAudioEffects = true
        )

        repo.saveConfig(updatedConfig)

        val savedConfig = repo.userPreferencesFlow.first()
        assertEquals(0.7f, savedConfig.temperature, 0.001f)
        assertEquals(40, savedConfig.topK)
        assertEquals(350, savedConfig.maxTokens)
        assertEquals(PromptStyle.VERBOSE, savedConfig.promptStyle)
        assertEquals("Senior Android Lead Role", savedConfig.interviewContext)
        assertEquals(DelegateType.NPU_GOOGLE_TENSOR, savedConfig.preferredDelegate)
        assertFalse(savedConfig.enableWhisperRescorer)
        assertEquals(24f, savedConfig.teleprompterFontSizeSp, 0.001f)
        assertEquals(0.35f, savedConfig.stealthDimmerAlpha, 0.001f)
        assertFalse(savedConfig.enableVolumeKeyShortcuts)
        assertFalse(savedConfig.keepScreenOn)
        assertEquals(1.5f, savedConfig.minSilenceDurationSec, 0.001f)
        assertTrue(savedConfig.enableHardwareAudioEffects)

        tempFile.delete()
    }
}
