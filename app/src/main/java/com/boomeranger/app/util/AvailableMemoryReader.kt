package com.boomeranger.app.util

import android.app.ActivityManager
import android.content.Context

/**
 * Reads a [FrameStoragePolicy.MemorySnapshot] from [ActivityManager].
 */
class AvailableMemoryReader(context: Context) {

    private val appContext = context.applicationContext
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun snapshot(): FrameStoragePolicy.MemorySnapshot {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        val memoryClassMb = if (appContext.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_LARGE_HEAP != 0
        ) {
            activityManager.largeMemoryClass
        } else {
            activityManager.memoryClass
        }
        return FrameStoragePolicy.MemorySnapshot(
            availMemBytes = info.availMem,
            totalMemBytes = info.totalMem,
            lowMemory = info.lowMemory,
            memoryClassBytes = memoryClassMb.toLong() * 1024L * 1024L,
        )
    }
}
