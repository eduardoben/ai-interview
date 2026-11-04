package com.local.interviewassistant.service

import com.local.interviewassistant.core.model.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class Phase3PolishAndStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testAndroidManifestPredictiveBackEnabled() {
        val rootDir = File(".").canonicalFile
        val manifestFile = if (File(rootDir, "app/src/main/AndroidManifest.xml").exists()) {
            File(rootDir, "app/src/main/AndroidManifest.xml")
        } else {
            File(rootDir, "src/main/AndroidManifest.xml")
        }
        assertTrue("AndroidManifest.xml should exist", manifestFile.exists())

        val dbFactory = DocumentBuilderFactory.newInstance()
        val doc = dbFactory.newDocumentBuilder().parse(manifestFile)
        doc.documentElement.normalize()

        val appElements = doc.getElementsByTagName("application")
        assertTrue("Manifest must have application element", appElements.length > 0)
        val app = appElements.item(0) as Element
        assertEquals(
            "P2-03: android:enableOnBackInvokedCallback must be true",
            "true",
            app.getAttribute("android:enableOnBackInvokedCallback")
        )
    }

    @Test
    fun testNotificationVectorDrawablesExistAndValid() {
        val rootDir = File(".").canonicalFile
        val drawableDir = if (File(rootDir, "app/src/main/res/drawable").exists()) {
            File(rootDir, "app/src/main/res/drawable")
        } else {
            File(rootDir, "src/main/res/drawable")
        }

        val requiredDrawables = listOf(
            "ic_notification_mic.xml",
            "ic_notification_stop.xml",
            "ic_notification_download.xml",
            "ic_notification_done.xml",
            "ic_notification_cancel.xml",
            "ic_notification_error.xml"
        )

        for (name in requiredDrawables) {
            val file = File(drawableDir, name)
            assertTrue("P2-04 drawable must exist: $name", file.exists())
            val content = file.readText()
            assertTrue("Drawable $name must be a vector drawable", content.contains("<vector"))
            assertTrue("Drawable $name must contain path data", content.contains("android:pathData="))
        }
    }

    @Test
    fun testPurgeDownloadedModelsDeletesAllModelFilesAndRecreatesDirs() {
        val root = tempFolder.newFolder("models_root")
        val config = AppConfig(root)
        assertTrue(config.ensureDirectoriesExist())

        // Create dummy model files across directories
        val dummyVad = File(config.vadDir, "silero_vad.onnx").apply { writeText("dummy vad") }
        val dummyEncoder = File(config.asrDir, "encoder.onnx").apply { writeText("dummy encoder") }
        val dummyDecoder = File(config.asrDir, "decoder.onnx").apply { writeText("dummy decoder") }
        val dummyJoiner = File(config.asrDir, "joiner.onnx").apply { writeText("dummy joiner") }
        val dummyTokens = File(config.asrDir, "tokens.txt").apply { writeText("dummy tokens") }
        val dummyLlm = File(config.llmDir, "gemma-4-E2B-it.litertlm").apply { writeText("dummy gemma") }
        val dummyWhisper = File(config.whisperDir, "tiny.en-encoder.int8.onnx").apply { writeText("dummy whisper") }

        assertTrue(dummyVad.exists())
        assertTrue(dummyEncoder.exists())
        assertTrue(dummyDecoder.exists())
        assertTrue(dummyJoiner.exists())
        assertTrue(dummyTokens.exists())
        assertTrue(dummyLlm.exists())
        assertTrue(dummyWhisper.exists())

        // Execute purge
        val success = config.purgeDownloadedModels()
        assertTrue("purgeDownloadedModels should report success", success)

        // Verify files are deleted
        assertFalse("VAD file must be purged", dummyVad.exists())
        assertFalse("ASR encoder must be purged", dummyEncoder.exists())
        assertFalse("ASR decoder must be purged", dummyDecoder.exists())
        assertFalse("ASR joiner must be purged", dummyJoiner.exists())
        assertFalse("ASR tokens must be purged", dummyTokens.exists())
        assertFalse("LLM file must be purged", dummyLlm.exists())
        assertFalse("Whisper file must be purged", dummyWhisper.exists())

        // Verify directories are recreated and writable
        assertTrue("vadDir must be recreated", config.vadDir.exists())
        assertTrue("asrDir must be recreated", config.asrDir.exists())
        assertTrue("whisperDir must be recreated", config.whisperDir.exists())
        assertTrue("llmDir must be recreated", config.llmDir.exists())

        // Verify model report reports none present
        val report = config.validateModelsOnDisk()
        assertFalse("No models should be present after purge", report.allPresent)
    }

    @Test
    fun testPrivacyPolicyConstantsAndFilesExist() {
        assertEquals("https://aiinterviewassistant.app/privacy", AppConfig.PRIVACY_POLICY_URL)
        assertEquals("https://himmler.github.io/AIInterviewAssistant/privacy.html", AppConfig.PRIVACY_POLICY_MIRROR_URL)

        val rootDir = File(".").canonicalFile
        val policyMd = if (File(rootDir, "docs/privacy_policy.md").exists()) {
            File(rootDir, "docs/privacy_policy.md")
        } else {
            File(rootDir, "../docs/privacy_policy.md")
        }
        val policyHtml = if (File(rootDir, "docs/privacy.html").exists()) {
            File(rootDir, "docs/privacy.html")
        } else {
            File(rootDir, "../docs/privacy.html")
        }

        assertTrue("docs/privacy_policy.md must exist", policyMd.exists())
        assertTrue("docs/privacy.html must exist", policyHtml.exists())

        val mdContent = policyMd.readText()
        assertTrue("Markdown policy must declare zero cloud transmission", mdContent.contains("Zero Cloud Transmission"))
        assertTrue("Markdown policy must declare microphone privacy", mdContent.contains("RECORD_AUDIO"))
        assertTrue("Markdown policy must declare data deletion rights", mdContent.contains("In-App Data Deletion"))

        val htmlContent = policyHtml.readText()
        assertTrue("HTML policy must declare zero cloud transmission", htmlContent.contains("Zero Cloud Transmission"))
        assertTrue("HTML policy must be valid HTML5", htmlContent.contains("<!DOCTYPE html>"))
    }
}
