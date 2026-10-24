package com.local.interviewassistant.core.di

import com.local.interviewassistant.core.model.AppConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase6ReadinessTest {


    
    // Kotlin properties aren't easily overridable if not open. Let's just create files.
    private fun createValidReport(): AppConfig.ModelValidationReport {
        val statuses = listOf(
            AppConfig.ModelFileStatus("test1", "path1", exists = true, sizeBytes = 10L),
        )
        return AppConfig.ModelValidationReport(statuses)
    }

    private fun createInvalidReport(): AppConfig.ModelValidationReport {
        val statuses = listOf(
            AppConfig.ModelFileStatus("test1", "path1", exists = true, sizeBytes = 10L),
            AppConfig.ModelFileStatus("test2", "path2", exists = false, sizeBytes = 0L),
        )
        return AppConfig.ModelValidationReport(statuses)
    }

    @Test
    fun testCanStartAssistantFalseWhenPermissionsMissing() {
        val validReport = createValidReport()
        
        val status1 = AppContainer.ReadinessStatus(
            hasAudioPermission = false,
            hasNotificationPermission = true,
            hasOverlayPermission = true,
            modelReport = validReport,
        )
        assertFalse(status1.allPermissionsGranted)
        assertFalse(status1.canStartAssistant)

        val status2 = AppContainer.ReadinessStatus(
            hasAudioPermission = true,
            hasNotificationPermission = false,
            hasOverlayPermission = true,
            modelReport = validReport,
        )
        assertFalse(status2.allPermissionsGranted)
        assertFalse(status2.canStartAssistant)

        val status3 = AppContainer.ReadinessStatus(
            hasAudioPermission = true,
            hasNotificationPermission = true,
            hasOverlayPermission = false,
            modelReport = validReport,
        )
        assertFalse(status3.allPermissionsGranted)
        assertFalse(status3.canStartAssistant)
    }

    @Test
    fun testCanStartAssistantFalseWhenModelsMissing() {
        val invalidReport = createInvalidReport()
        
        val status = AppContainer.ReadinessStatus(
            hasAudioPermission = true,
            hasNotificationPermission = true,
            hasOverlayPermission = true,
            modelReport = invalidReport,
        )
        
        assertTrue(status.allPermissionsGranted)
        assertFalse(status.modelReport.allPresent)
        assertFalse(status.canStartAssistant)
    }

    @Test
    fun testCanStartAssistantTrueWhenFullyReady() {
        val validReport = createValidReport()
        
        val status = AppContainer.ReadinessStatus(
            hasAudioPermission = true,
            hasNotificationPermission = true,
            hasOverlayPermission = true,
            modelReport = validReport,
        )
        
        assertTrue(status.allPermissionsGranted)
        assertTrue(status.modelReport.allPresent)
        assertTrue(status.canStartAssistant)
    }
}
