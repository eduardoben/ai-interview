package com.local.interviewassistant.data.llm

import android.app.ActivityManager
import android.content.Context
import android.util.Log

object MemoryMonitor {
    fun logMemoryUsage(context: Context, tag: String = "MemoryMonitor") {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalMemMb = memoryInfo.totalMem / (1024 * 1024)
        val availMemMb = memoryInfo.availMem / (1024 * 1024)
        val usedMemMb = totalMemMb - availMemMb

        Log.i(tag, "Memory Status - Used: $usedMemMb MB | Available: $availMemMb MB | Total: $totalMemMb MB | LowMemory: ${memoryInfo.lowMemory}")
    }
}
