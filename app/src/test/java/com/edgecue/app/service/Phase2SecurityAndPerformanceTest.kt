package com.edgecue.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class Phase2SecurityAndPerformanceTest {

    @Test
    fun testProGuardRulesStripVerboseDebugInfoLogs() {
        // Resolve proguard-rules.pro relative to current test run directory
        val rootDir = File(".").canonicalFile
        val proguardFile = if (File(rootDir, "app/proguard-rules.pro").exists()) {
            File(rootDir, "app/proguard-rules.pro")
        } else {
            File(rootDir, "proguard-rules.pro")
        }
        assertTrue("proguard-rules.pro should exist at: ${proguardFile.absolutePath}", proguardFile.exists())

        val content = proguardFile.readText()
        assertTrue("Should contain -assumenosideeffects rule", content.contains("-assumenosideeffects class android.util.Log"))
        assertTrue("Should strip isLoggable", content.contains("public static boolean isLoggable(java.lang.String, int);"))
        assertTrue("Should strip Log.v", content.contains("public static int v(...);"))
        assertTrue("Should strip Log.d", content.contains("public static int d(...);"))
        assertTrue("Should strip Log.i", content.contains("public static int i(...);"))

        // Must NOT strip Log.w or Log.e
        assertFalse("Must preserve Log.w", content.contains("public static int w("))
        assertFalse("Must preserve Log.e", content.contains("public static int e("))
    }

    @Test
    fun testNetworkSecurityConfigDisallowsCleartextAndAllowsHuggingFace() {
        val rootDir = File(".").canonicalFile
        val configFile = if (File(rootDir, "app/src/main/res/xml/network_security_config.xml").exists()) {
            File(rootDir, "app/src/main/res/xml/network_security_config.xml")
        } else {
            File(rootDir, "src/main/res/xml/network_security_config.xml")
        }
        assertTrue("network_security_config.xml should exist at: ${configFile.absolutePath}", configFile.exists())

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(configFile)
        doc.documentElement.normalize()

        assertEquals("network-security-config", doc.documentElement.tagName)

        // Check base-config cleartextTrafficPermitted="false"
        val baseConfigNodes = doc.getElementsByTagName("base-config")
        assertTrue("Must have base-config", baseConfigNodes.length > 0)
        val baseConfig = baseConfigNodes.item(0) as Element
        assertEquals("false", baseConfig.getAttribute("cleartextTrafficPermitted"))

        // Check trust-anchors certificates src="system"
        val trustAnchors = baseConfig.getElementsByTagName("trust-anchors")
        assertTrue("Must have trust-anchors", trustAnchors.length > 0)
        val certificates = (trustAnchors.item(0) as Element).getElementsByTagName("certificates")
        assertTrue("Must have certificates tag", certificates.length > 0)
        assertEquals("system", (certificates.item(0) as Element).getAttribute("src"))

        // Check domain-config for huggingface.co and hf.co
        val domainConfigNodes = doc.getElementsByTagName("domain-config")
        assertTrue("Must have domain-config", domainConfigNodes.length > 0)
        val domainConfig = domainConfigNodes.item(0) as Element
        assertEquals("false", domainConfig.getAttribute("cleartextTrafficPermitted"))

        val domainNodes = domainConfig.getElementsByTagName("domain")
        val domainNames = mutableListOf<String>()
        for (i in 0 until domainNodes.length) {
            val domainElem = domainNodes.item(i) as Element
            assertEquals("true", domainElem.getAttribute("includeSubdomains"))
            domainNames.add(domainElem.textContent.trim())
        }

        assertTrue("Must include huggingface.co", domainNames.contains("huggingface.co"))
        assertTrue("Must include hf.co", domainNames.contains("hf.co"))
    }

    @Test
    fun testAndroidManifestDeclaresNetworkSecurityConfig() {
        val rootDir = File(".").canonicalFile
        val manifestFile = if (File(rootDir, "app/src/main/AndroidManifest.xml").exists()) {
            File(rootDir, "app/src/main/AndroidManifest.xml")
        } else {
            File(rootDir, "src/main/AndroidManifest.xml")
        }
        assertTrue("AndroidManifest.xml should exist at: ${manifestFile.absolutePath}", manifestFile.exists())

        val content = manifestFile.readText()
        assertTrue(
            "AndroidManifest.xml must declare android:networkSecurityConfig=\"@xml/network_security_config\"",
            content.contains("android:networkSecurityConfig=\"@xml/network_security_config\"")
        )
    }

    @Test
    fun testModelDownloadServiceWakeLockConstants() {
        assertEquals(
            "WakeLock timeout must be 30 minutes (1,800,000 ms)",
            30 * 60 * 1000L,
            ModelDownloadService.WAKE_LOCK_TIMEOUT_MS
        )
        assertEquals(
            "WakeLock renewal interval must be 10 minutes (600,000 ms)",
            10 * 60 * 1000L,
            ModelDownloadService.WAKE_LOCK_RENEW_INTERVAL_MS
        )
    }

    @Test
    fun testAssistantForegroundServiceDoesNotHoldWakeLock() {
        val rootDir = File(".").canonicalFile
        val serviceFile = if (File(rootDir, "app/src/main/java/com/edgecue/app/service/AssistantForegroundService.kt").exists()) {
            File(rootDir, "app/src/main/java/com/edgecue/app/service/AssistantForegroundService.kt")
        } else {
            File(rootDir, "src/main/java/com/edgecue/app/service/AssistantForegroundService.kt")
        }
        assertTrue("AssistantForegroundService.kt should exist", serviceFile.exists())

        val content = serviceFile.readText()
        assertFalse(
            "AssistantForegroundService must not declare wakeLock",
            content.contains("wakeLock")
        )
        assertFalse(
            "AssistantForegroundService must not call acquireWakeLock",
            content.contains("acquireWakeLock")
        )
        assertFalse(
            "AssistantForegroundService must not call releaseWakeLock",
            content.contains("releaseWakeLock")
        )
    }

    @Test
    fun testMainActivityRefreshStatusRunsOnIoDispatcher() {
        val rootDir = File(".").canonicalFile
        val mainActivityFile = if (File(rootDir, "app/src/main/java/com/edgecue/app/ui/main/MainActivity.kt").exists()) {
            File(rootDir, "app/src/main/java/com/edgecue/app/ui/main/MainActivity.kt")
        } else {
            File(rootDir, "src/main/java/com/edgecue/app/ui/main/MainActivity.kt")
        }
        assertTrue("MainActivity.kt should exist", mainActivityFile.exists())

        val content = mainActivityFile.readText()
        assertTrue("MainActivity must import Dispatchers", content.contains("import kotlinx.coroutines.Dispatchers"))
        assertTrue("MainActivity must import withContext", content.contains("import kotlinx.coroutines.withContext"))
        assertTrue("MainActivity must declare refreshStatusJob", content.contains("refreshStatusJob: Job? = null"))
        assertTrue("refreshStatus must use withContext(Dispatchers.IO)", content.contains("withContext(Dispatchers.IO)"))
        assertTrue("refreshStatus must cancel previous job", content.contains("refreshStatusJob?.cancel()"))
    }
}
