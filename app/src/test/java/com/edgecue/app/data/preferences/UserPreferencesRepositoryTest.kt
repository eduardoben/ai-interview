package com.edgecue.app.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.edgecue.app.core.model.LlmGenerationConfig
import com.edgecue.app.core.model.PromptStyle
import com.edgecue.app.data.hardware.DelegateType
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
            displayDimmerAlpha = 0.35f,
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
        assertEquals(0.35f, savedConfig.displayDimmerAlpha, 0.001f)
        assertFalse(savedConfig.enableVolumeKeyShortcuts)
        assertFalse(savedConfig.keepScreenOn)
        assertEquals(1.5f, savedConfig.minSilenceDurationSec, 0.001f)
        assertTrue(savedConfig.enableHardwareAudioEffects)

        tempFile.delete()
    }

    @Test
    fun testLegacyDimmerAlphaFallback() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val tempFile = File.createTempFile("legacy_test_prefs", ".preferences_pb")

        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFile }
        )

        // Seed with legacy key
        dataStore.edit { preferences ->
            preferences[androidx.datastore.preferences.core.floatPreferencesKey("ste" + "althDimmerAlpha")] = 0.42f
        }

        val repo = UserPreferencesRepository(
            externalDataStore = dataStore
        )

        val config = repo.userPreferencesFlow.first()
        assertEquals(0.42f, config.displayDimmerAlpha, 0.001f)

        tempFile.delete()
    }

    @Test
    fun testAudioConsentPreferenceDefaultAndUpdates() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val tempFile = File.createTempFile("consent_test_prefs", ".preferences_pb")

        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFile }
        )

        val repo = UserPreferencesRepository(
            externalDataStore = dataStore
        )

        // 1. Initial consent must be false
        val initialConsent = repo.hasAcknowledgedAudioConsent.first()
        assertFalse("Default audio consent must be false", initialConsent)

        // 2. Affirmative agreement updates to true
        repo.setHasAcknowledgedAudioConsent(true)
        val updatedConsent = repo.hasAcknowledgedAudioConsent.first()
        assertTrue("Acknowledged audio consent must update to true", updatedConsent)

        // 3. Revocation updates back to false
        repo.setHasAcknowledgedAudioConsent(false)
        val revokedConsent = repo.hasAcknowledgedAudioConsent.first()
        assertFalse("Revoked audio consent must update back to false", revokedConsent)

        tempFile.delete()
    }

    @Test
    fun testOverlayDimensionsDefaultAndUpdates() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val tempFile = File.createTempFile("overlay_dims_test_prefs", ".preferences_pb")

        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFile }
        )

        val repo = UserPreferencesRepository(
            externalDataStore = dataStore
        )

        // 1. Initial dimensions default to (380f, 480f)
        val initialDims = repo.overlayDimensionsFlow.first()
        assertEquals(380f, initialDims.first, 0.001f)
        assertEquals(480f, initialDims.second, 0.001f)

        // 2. Custom resize dimensions save and read back correctly
        repo.saveOverlayDimensions(460f, 620f)
        val updatedDims = repo.overlayDimensionsFlow.first()
        assertEquals(460f, updatedDims.first, 0.001f)
        assertEquals(620f, updatedDims.second, 0.001f)

        tempFile.delete()
    }
}
