package com.boomeranger.app.util

import com.boomeranger.app.media.FrameStorageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for hybrid in-memory vs disk frame storage policy.
 */
class FrameStoragePolicyTest {

    private fun ampleMemory(
        availMb: Long = 1024,
        memoryClassMb: Long = 512,
        lowMemory: Boolean = false,
    ) = FrameStoragePolicy.MemorySnapshot(
        availMemBytes = availMb * 1024L * 1024L,
        totalMemBytes = 4096L * 1024L * 1024L,
        lowMemory = lowMemory,
        memoryClassBytes = memoryClassMb * 1024L * 1024L,
    )

    @Test
    fun hdAt30FpsUsesMemoryWhenRamIsAmple() {
        val decision = FrameStoragePolicy.decide(
            width = 1280,
            height = 720,
            expectedFrameCount = 90,
            targetFps = 30f,
            memory = ampleMemory(availMb = 1024),
        )
        assertEquals(FrameStorageMode.MEMORY, decision.mode)
        assertTrue(decision.reason.contains("within soft budget"))
    }

    @Test
    fun hdAt30FpsFallsBackToDiskWhenAvailMemLow() {
        val decision = FrameStoragePolicy.decide(
            width = 1280,
            height = 720,
            expectedFrameCount = 90,
            targetFps = 30f,
            memory = ampleMemory(availMb = 128),
        )
        assertEquals(FrameStorageMode.DISK, decision.mode)
        assertTrue(decision.reason.contains("availMem"))
    }

    @Test
    fun fhdExceedsSoftPixelBudget() {
        val decision = FrameStoragePolicy.decide(
            width = 1920,
            height = 1080,
            expectedFrameCount = 90,
            targetFps = 30f,
            memory = ampleMemory(availMb = 2048),
        )
        assertEquals(FrameStorageMode.DISK, decision.mode)
        assertTrue(decision.reason.contains("soft pixel budget"))
    }

    @Test
    fun sixtyFpsExceedsSoftFpsBudget() {
        val decision = FrameStoragePolicy.decide(
            width = 1280,
            height = 720,
            expectedFrameCount = 180,
            targetFps = 60f,
            memory = ampleMemory(availMb = 2048),
        )
        assertEquals(FrameStorageMode.DISK, decision.mode)
        assertTrue(decision.reason.contains("fps"))
    }

    @Test
    fun lowMemoryFlagForcesDisk() {
        val decision = FrameStoragePolicy.decide(
            width = 640,
            height = 360,
            expectedFrameCount = 30,
            targetFps = 30f,
            memory = ampleMemory(availMb = 2048, lowMemory = true),
        )
        assertEquals(FrameStorageMode.DISK, decision.mode)
        assertTrue(decision.reason.contains("lowMemory"))
    }

    @Test
    fun tinyMemoryClassForcesDisk() {
        val decision = FrameStoragePolicy.decide(
            width = 640,
            height = 360,
            expectedFrameCount = 30,
            targetFps = 30f,
            memory = ampleMemory(availMb = 2048, memoryClassMb = 128),
        )
        assertEquals(FrameStorageMode.DISK, decision.mode)
        assertTrue(decision.reason.contains("memoryClass"))
    }

    @Test
    fun expectedFrameCountHonorsDurationAndFps() {
        assertEquals(90, FrameStoragePolicy.expectedFrameCount(3_000L, 30f))
        assertEquals(180, FrameStoragePolicy.expectedFrameCount(3_000L, 60f))
        assertEquals(30, FrameStoragePolicy.expectedFrameCount(1_000L, 30f))
    }
}
