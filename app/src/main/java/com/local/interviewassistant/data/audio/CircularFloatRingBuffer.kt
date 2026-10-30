package com.local.interviewassistant.data.audio

/**
 * Zero-allocation circular float ring buffer for real-time audio frame windowing.
 * Eliminates per-frame FloatArray allocations and copyOfRange GC churn during
 * sustained 16 kHz audio streaming.
 */
class CircularFloatRingBuffer(initialCapacity: Int = 32_768) {
    private var buffer = FloatArray(initialCapacity.coerceAtLeast(512))
    private var head = 0
    private var tail = 0
    private var count = 0

    val size: Int
        @Synchronized get() = count

    val capacity: Int
        @Synchronized get() = buffer.size

    @Synchronized
    fun write(samples: FloatArray, offset: Int = 0, length: Int = samples.size): Int {
        if (length <= 0) return 0
        ensureCapacity(count + length)
        for (i in 0 until length) {
            buffer[head] = samples[offset + i]
            head = (head + 1) % buffer.size
        }
        count += length
        return length
    }

    @Synchronized
    fun read(destination: FloatArray, offset: Int = 0, length: Int = destination.size): Int {
        val toRead = minOf(length, count)
        for (i in 0 until toRead) {
            destination[offset + i] = buffer[tail]
            tail = (tail + 1) % buffer.size
        }
        count -= toRead
        return toRead
    }

    @Synchronized
    fun clear() {
        head = 0
        tail = 0
        count = 0
    }

    private fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity <= buffer.size) return
        var newCapacity = buffer.size
        while (newCapacity < requiredCapacity) {
            newCapacity *= 2
        }
        val newBuffer = FloatArray(newCapacity)
        for (i in 0 until count) {
            newBuffer[i] = buffer[(tail + i) % buffer.size]
        }
        buffer = newBuffer
        tail = 0
        head = count
    }
}

