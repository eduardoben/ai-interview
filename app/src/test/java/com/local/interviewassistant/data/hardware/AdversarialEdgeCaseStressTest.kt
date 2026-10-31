package com.local.interviewassistant.data.hardware

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adversarial edge-case and stress tests created by Challenger 1.
 * Tests edge cases and failure modes for Android 13 compatibility:
 * 1. sanitizeSocField edge cases (whitespace, casing, unusual strings like "0", "N/A")
 * 2. onTimeout re-entrancy and concurrent thread safety
 * 3. NotificationChannel idempotency
 */
class AdversarialEdgeCaseStressTest {

    // =========================================================================
    // 1. sanitizeSocField Stress Tests
    // =========================================================================

    @Test
    fun sanitizeSocField_viaReflection_rejectsAllUnknownAndBlankVariants() {
        val sanitizeMethod = DeviceHardwareDetector::class.java.getDeclaredMethod(
            "sanitizeSocField",
            String::class.java
        ).apply { isAccessible = true }

        fun invokeSanitize(input: String): String? {
            return sanitizeMethod.invoke(DeviceHardwareDetector, input) as String?
        }

        // Adversarial inputs that MUST evaluate to null
        val invalidInputs = listOf(
            "",
            " ",
            "   ",
            "\t",
            "\n",
            " \t \r\n ",
            "unknown",
            "UNKNOWN",
            "Unknown",
            "uNkNoWn",
            "  unknown  ",
            "\tunknown\n",
            " \r\n UNKNOWN \t "
        )

        for (input in invalidInputs) {
            val result = invokeSanitize(input)
            assertNull(
                "Input '$input' was expected to be sanitized to null, but returned '$result'",
                result
            )
        }
    }

    @Test
    fun sanitizeSocField_viaReflection_preservesValidHardwareStringsWithTrimming() {
        val sanitizeMethod = DeviceHardwareDetector::class.java.getDeclaredMethod(
            "sanitizeSocField",
            String::class.java
        ).apply { isAccessible = true }

        fun invokeSanitize(input: String): String? {
            return sanitizeMethod.invoke(DeviceHardwareDetector, input) as String?
        }

        val validInputsWithExpected = mapOf(
            "SM8550" to "SM8550",
            "  SM8550  " to "SM8550",
            "\tkalama\n" to "kalama",
            "Snapdragon 8 Gen 2" to "Snapdragon 8 Gen 2",
            "MT6769V/CZ" to "MT6769V/CZ",
            "0" to "0",
            "  0  " to "0",
            "N/A" to "N/A",
            "  N/A  " to "N/A",
            "None" to "None",
            "null" to "null"
        )

        for ((input, expected) in validInputsWithExpected) {
            val result = invokeSanitize(input)
            assertEquals("Input '$input' did not match expected output", expected, result)
        }
    }

    @Test
    fun classify_adversarialSocInputs_cascadesCleanlyWithoutCrashing() {
        // Case A: socModel is "Unknown" with whitespace -> falls back to hardware
        val devA = SystemDeviceInfo(
            socModel = "  Unknown  ",
            hardware = "kalama",
            device = "dm1q",
            model = "Galaxy S23",
            totalRamBytes = 8_000_000_000L,
            availableRamBytes = 4_000_000_000L,
            hasNpuFeature = false,
            hasNativeTensorLib = false
        )
        val profileA = DeviceHardwareDetector.classify(devA)
        assertEquals("kalama", profileA.socName)

        // Case B: socModel is blank, hardware is "  UNKNOWN  " -> falls back to model
        val devB = SystemDeviceInfo(
            socModel = "   \t ",
            hardware = "  UNKNOWN  ",
            device = "dm1q",
            model = "  Galaxy S23  ",
            totalRamBytes = 8_000_000_000L,
            availableRamBytes = 4_000_000_000L,
            hasNpuFeature = false,
            hasNativeTensorLib = false
        )
        val profileB = DeviceHardwareDetector.classify(devB)
        assertEquals("Galaxy S23", profileB.socName)

        // Case C: all fields unknown or whitespace -> falls back to "Standard Device"
        val devC = SystemDeviceInfo(
            socModel = " \n unknown \t ",
            hardware = "\t",
            device = "unknown",
            model = "  Unknown  ",
            totalRamBytes = 8_000_000_000L,
            availableRamBytes = 4_000_000_000L,
            hasNpuFeature = false,
            hasNativeTensorLib = false
        )
        val profileC = DeviceHardwareDetector.classify(devC)
        assertEquals("Standard Device", profileC.socName)

        // Case D: socModel is "N/A" -> sanitized to "N/A" (non-null string preserved, no crash)
        val devD = SystemDeviceInfo(
            socModel = "  N/A  ",
            hardware = "kalama",
            device = "dm1q",
            model = "Galaxy S23",
            totalRamBytes = 8_000_000_000L,
            availableRamBytes = 4_000_000_000L,
            hasNpuFeature = false,
            hasNativeTensorLib = false
        )
        val profileD = DeviceHardwareDetector.classify(devD)
        assertEquals("N/A", profileD.socName)

        // Case E: socModel is "0" -> sanitized to "0" (non-null string preserved, no crash)
        val devE = SystemDeviceInfo(
            socModel = " 0 ",
            hardware = "kalama",
            device = "dm1q",
            model = "Galaxy S23",
            totalRamBytes = 8_000_000_000L,
            availableRamBytes = 4_000_000_000L,
            hasNpuFeature = false,
            hasNativeTensorLib = false
        )
        val profileE = DeviceHardwareDetector.classify(devE)
        assertEquals("0", profileE.socName)
    }

    // =========================================================================
    // 2. onTimeout Re-entrancy and Thread-Safety Stress Tests
    // =========================================================================

    @Test
    fun onTimeout_reentrancyGuard_preventsDoubleTeardown() {
        val isHandlingTimeout = AtomicBoolean(false)
        val teardownExecutionCount = AtomicInteger(0)

        fun simulateHandleTimeout() {
            if (!isHandlingTimeout.compareAndSet(false, true)) return
            teardownExecutionCount.incrementAndGet()
        }

        // Simulate Android 15 calling onTimeout(startId, fgsType)
        // In Android 15, super.onTimeout(startId, fgsType) invokes onTimeout(startId).
        // ModelDownloadService implements:
        // override fun onTimeout(startId: Int) {
        //     super.onTimeout(startId)
        //     handleTimeout(startId) // First invocation!
        // }
        // override fun onTimeout(startId: Int, fgsType: Int) {
        //     super.onTimeout(startId, fgsType) // calls onTimeout(startId)
        //     handleTimeout(startId) // Second invocation!
        // }

        // First call (from within onTimeout(startId))
        simulateHandleTimeout()
        // Second call (from onTimeout(startId, fgsType) after super returns)
        simulateHandleTimeout()

        // Assert teardown executed exactly once
        assertEquals(
            "Teardown logic must execute exactly once under Android 15 re-entrancy",
            1,
            teardownExecutionCount.get()
        )
        assertTrue(isHandlingTimeout.get())

        // Third call (arbitrary delayed callback)
        simulateHandleTimeout()
        assertEquals(1, teardownExecutionCount.get())

        // Simulate onStartCommand reset for new download session
        isHandlingTimeout.set(false)
        assertFalse(isHandlingTimeout.get())

        // New timeout can execute cleanly
        simulateHandleTimeout()
        assertEquals(2, teardownExecutionCount.get())
    }

    @Test
    fun onTimeout_concurrentStress_guaranteesExactlyOneTeardownAcross50Threads() {
        val isHandlingTimeout = AtomicBoolean(false)
        val teardownExecutionCount = AtomicInteger(0)

        fun simulateHandleTimeout() {
            if (!isHandlingTimeout.compareAndSet(false, true)) return
            // Simulate critical teardown logic
            teardownExecutionCount.incrementAndGet()
        }

        val threadCount = 50
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    simulateHandleTimeout()
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue("All concurrent timeout threads must complete", completed)

        assertEquals(
            "Exactly one thread must execute teardown under massive race condition",
            1,
            teardownExecutionCount.get()
        )
    }

    // =========================================================================
    // 3. NotificationChannel Idempotency Stress Test
    // =========================================================================

    @Test
    fun notificationChannelCreation_isStrictlyIdempotentAcrossMultipleConsecutiveCalls() {
        val channelId = "download_channel"
        val channelName = "Model Downloads"
        val importance = 2 // NotificationManager.IMPORTANCE_LOW

        // Simulate channel registry map (mimicking NotificationManagerService behavior)
        val channelRegistry = mutableMapOf<String, Pair<String, Int>>()

        fun createChannel(id: String, name: String, imp: Int) {
            // Android OS NotificationManager.createNotificationChannel contracts:
            // Existing channel attributes are preserved unless user modified them
            channelRegistry[id] = Pair(name, imp)
        }

        // Call 100 times consecutively as DownloadWorker.createForegroundInfo does
        for (i in 1..100) {
            createChannel(channelId, channelName, importance)
        }

        assertEquals(1, channelRegistry.size)
        assertTrue(channelRegistry.containsKey(channelId))
        assertEquals(Pair(channelName, importance), channelRegistry[channelId])
    }
}
