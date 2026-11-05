package com.edgecue.app.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.xml.parsers.DocumentBuilderFactory

class Milestone2AdversarialStressTest {

    // --------------------------------------------------------------------------
    // 1. Probe Network Security Config & CDN / Subdomain Routing
    // --------------------------------------------------------------------------

    @Test
    fun testNetworkSecurityConfigPolicyEnforcement() {
        val rootDir = File(".").canonicalFile
        val configFile = if (File(rootDir, "app/src/main/res/xml/network_security_config.xml").exists()) {
            File(rootDir, "app/src/main/res/xml/network_security_config.xml")
        } else {
            File(rootDir, "src/main/res/xml/network_security_config.xml")
        }
        assertTrue("network_security_config.xml must exist", configFile.exists())

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(configFile)
        doc.documentElement.normalize()

        // 1. Base config must disallow cleartext globally
        val baseConfig = doc.getElementsByTagName("base-config").item(0) as Element
        assertEquals("false", baseConfig.getAttribute("cleartextTrafficPermitted"))

        val certs = (baseConfig.getElementsByTagName("trust-anchors").item(0) as Element)
            .getElementsByTagName("certificates").item(0) as Element
        assertEquals("system", certs.getAttribute("src"))

        // 2. Domain configs must strictly prohibit cleartext and enforce includeSubdomains
        val domainConfigs = doc.getElementsByTagName("domain-config")
        assertTrue("Must have domain configs", domainConfigs.length > 0)
        val domainConfig = domainConfigs.item(0) as Element
        assertEquals("false", domainConfig.getAttribute("cleartextTrafficPermitted"))

        val domains = domainConfig.getElementsByTagName("domain")
        val domainMap = mutableMapOf<String, Boolean>()
        for (i in 0 until domains.length) {
            val d = domains.item(i) as Element
            domainMap[d.textContent.trim()] = d.getAttribute("includeSubdomains") == "true"
        }

        // Subdomains like cdn-lfs.huggingface.co and us.aws.cdn.hf.co must match
        assertTrue("huggingface.co must be present", domainMap.containsKey("huggingface.co"))
        assertTrue("hf.co must be present", domainMap.containsKey("hf.co"))
        assertTrue("huggingface.co must includeSubdomains", domainMap["huggingface.co"] == true)
        assertTrue("hf.co must includeSubdomains", domainMap["hf.co"] == true)

        // Adversarial check: verify subdomain matching logic
        fun isDomainCoveredBySubdomainRule(hostname: String, targetDomain: String): Boolean {
            return hostname == targetDomain || hostname.endsWith(".$targetDomain")
        }

        assertTrue("cdn-lfs.huggingface.co covered", isDomainCoveredBySubdomainRule("cdn-lfs.huggingface.co", "huggingface.co"))
        assertTrue("us.aws.cdn.hf.co covered", isDomainCoveredBySubdomainRule("us.aws.cdn.hf.co", "hf.co"))
        assertTrue("api.huggingface.co covered", isDomainCoveredBySubdomainRule("api.huggingface.co", "huggingface.co"))
        assertFalse("evil-huggingface.co not covered", isDomainCoveredBySubdomainRule("evil-huggingface.co", "huggingface.co"))
    }

    // --------------------------------------------------------------------------
    // 2. Probe Wake Lock Renewal & Lifecycle Corner Cases
    // --------------------------------------------------------------------------

    @Test
    fun testWakeLockRenewalIntervalAndStallProtectionSimulation() {
        // ModelDownloadService contracts:
        // WAKE_LOCK_TIMEOUT_MS = 30 * 60 * 1000L
        // WAKE_LOCK_RENEW_INTERVAL_MS = 10 * 60 * 1000L
        val timeoutMs = ModelDownloadService.WAKE_LOCK_TIMEOUT_MS
        val renewIntervalMs = ModelDownloadService.WAKE_LOCK_RENEW_INTERVAL_MS

        assertEquals(1_800_000L, timeoutMs)
        assertEquals(600_000L, renewIntervalMs)
        assertTrue("Renew interval must be substantially less than timeout", renewIntervalMs < timeoutMs)

        // WakeLock harness simulating non-refcounted wake lock with renewal throttle
        class MockWakeLockHarness {
            var isHeld = false
            var currentTimeout = 0L
            var acquireCount = 0
            var releaseCount = 0
            var lastRenewTime = 0L

            fun acquire(timeout: Long, now: Long) {
                isHeld = true
                currentTimeout = timeout
                acquireCount++
                lastRenewTime = now
            }

            fun renew(now: Long): Boolean {
                if (now - lastRenewTime >= renewIntervalMs) {
                    acquire(timeoutMs, now)
                    return true
                }
                return false
            }

            fun release() {
                if (isHeld) {
                    isHeld = false
                    releaseCount++
                }
            }

            fun checkStall(now: Long) {
                if (isHeld && (now - lastRenewTime >= timeoutMs)) {
                    // System PowerManager releases after timeout expiration
                    isHeld = false
                }
            }
        }

        val harness = MockWakeLockHarness()

        // 1. Service onCreate -> acquire initial 30m lock
        var simulatedTime = 1_000_000L
        harness.acquire(timeoutMs, simulatedTime)
        assertTrue(harness.isHeld)
        assertEquals(1, harness.acquireCount)

        // 2. Progress event at 2 minutes -> should be throttled (no re-acquire)
        simulatedTime += 2 * 60 * 1000L
        val renewedEarly = harness.renew(simulatedTime)
        assertFalse("Should not renew before 10 min", renewedEarly)
        assertEquals(1, harness.acquireCount)

        // 3. Progress event at 10 minutes -> should renew
        simulatedTime += 8 * 60 * 1000L // Total 10m elapsed
        val renewedOnSchedule = harness.renew(simulatedTime)
        assertTrue("Should renew at 10 min", renewedOnSchedule)
        assertEquals(2, harness.acquireCount)
        assertTrue(harness.isHeld)

        // 4. Download stall past 30 minutes (no progress events) -> OS timeout kicks in
        simulatedTime += 31 * 60 * 1000L
        harness.checkStall(simulatedTime)
        assertFalse("OS PowerManager must release stalled wake lock after 30 min", harness.isHeld)

        // 5. Completion or Cancellation release -> clean teardown
        harness.release()
        assertFalse(harness.isHeld)
    }

    // --------------------------------------------------------------------------
    // 3. Probe Async MainActivity.refreshStatus Rapid Concurrency & Job Cancellation
    // --------------------------------------------------------------------------

    @Test
    fun testRefreshStatusRapidConcurrencyJobCancellation() = runBlocking {
        // Stress harness simulating MainActivity.refreshStatus() cancellation logic
        val executedCount = AtomicInteger(0)
        val cancelledCount = AtomicInteger(0)
        var refreshStatusJob: Job? = null
        val scope = CoroutineScope(Dispatchers.Default)

        fun triggerRefresh(index: Int) {
            refreshStatusJob?.cancel()
            refreshStatusJob = scope.launch {
                try {
                    // Simulate off-main-thread IO operations (validateModelsOnDisk)
                    delay(50)
                    executedCount.incrementAndGet()
                } catch (e: CancellationException) {
                    cancelledCount.incrementAndGet()
                    throw e
                }
            }
        }

        // Rapid burst: simulate 20 rapid button clicks / lifecycle triggers within 10ms
        for (i in 1..20) {
            triggerRefresh(i)
            delay(1)
        }

        // Wait for final job to complete
        refreshStatusJob?.join()

        // Assert: At least 19 jobs must have been cancelled, exactly 1 completed
        assertEquals("Only the final job should have executed to completion", 1, executedCount.get())
        assertTrue("Earlier rapid jobs must have been cancelled", cancelledCount.get() >= 18)
    }

    // --------------------------------------------------------------------------
    // 4. Probe Release ProGuard / R8 Log Stripping
    // --------------------------------------------------------------------------

    @Test
    fun testProGuardRulesLogStrippingIntegrity() {
        val rootDir = File(".").canonicalFile
        val proguardFile = if (File(rootDir, "app/proguard-rules.pro").exists()) {
            File(rootDir, "app/proguard-rules.pro")
        } else {
            File(rootDir, "proguard-rules.pro")
        }
        assertTrue("app/proguard-rules.pro must exist", proguardFile.exists())

        val lines = proguardFile.readLines().map { it.trim() }

        // Must declare -assumenosideeffects class android.util.Log
        val hasRuleHeader = lines.any { it.contains("-assumenosideeffects class android.util.Log") }
        assertTrue("Must have Log -assumenosideeffects rule", hasRuleHeader)

        // Check required methods
        val hasV = lines.any { it.contains("public static int v(...);") }
        val hasD = lines.any { it.contains("public static int d(...);") }
        val hasI = lines.any { it.contains("public static int i(...);") }
        val hasIsLoggable = lines.any { it.contains("public static boolean isLoggable(java.lang.String, int);") }

        assertTrue("Must strip v", hasV)
        assertTrue("Must strip d", hasD)
        assertTrue("Must strip i", hasI)
        assertTrue("Must strip isLoggable", hasIsLoggable)

        // Check that w and e are NEVER stripped
        val hasW = lines.any { it.contains("public static int w(") }
        val hasE = lines.any { it.contains("public static int e(") }
        assertFalse("Must NOT strip Log.w", hasW)
        assertFalse("Must NOT strip Log.e", hasE)
    }
}
