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
        heapMaxMb: Long = memoryClassMb,
        heapUsedMb: Long = 0,
    ) = FrameStoragePolicy.MemorySnapshot(
        availMemBytes = availMb * 1024L * 1024L,
        totalMemBytes = 4096L * 1024L * 1024L,
        lowMemory = lowMemory,
        memoryClassBytes = memoryClassMb * 1024L * 1024L,
        heapMaxBytes = heapMaxMb * 1024L * 1024L,
        heapUsedBytes = heapUsedMb * 1024L * 1024L,
    )

    @Test
    fun smallClipUsesMemoryWhenHeapHasRoom() {
        val decision = FrameStoragePolicy.decide(
            width = 640,
            height = 360,
            expectedFrameCount = 30,
            targetFps = 30f,
            memory = ampleMemory(availMb = 1024, memoryClassMb = 512, heapMaxMb = 512),
        )
        assertEquals(FrameStorageMode.MEMORY, decision.mode)
        assertTrue(decision.reason.contains("within soft budget"))
    }

    @Test
    fun hdAt30FpsUsesMemoryOnlyWhenHeapIsGenuinelyHuge() {
        val decision = FrameStoragePolicy.decide(
            width = 1280,
            height = 720,
            expectedFrameCount = 90,
            targetFps = 30f,
            memory = ampleMemory(
                availMb = 4096,
                memoryClassMb = 2048,
                heapMaxMb = 2048,
            ),
        )
        assertEquals(FrameStorageMode.MEMORY, decision.mode)
        assertTrue(decision.reason.contains("within soft budget"))
    }

    @Test
    fun hdAt30FpsFallsBackToDiskWhenAvailMemLow() {
        val decision = FrameStoragePolicy.decide(
            width = 640,
            height = 360,
            expectedFrameCount = 30,
            targetFps = 30f,
            memory = ampleMemory(availMb = 8, memoryClassMb = 512, heapMaxMb = 512),
        )
        assertEquals(FrameStorageMode.DISK, decision.mode)
        assertTrue(decision.reason.contains("availMem"))
    }

    @Test
    fun ampleSystemRamButTypicalHeapUsesDiskForHd() {
        // Regression: ActivityManager.availMem is device RAM, not the app heap.
        // HD@30 for 3s is ~316MB of ARGB; a 256–512MB largeHeap cannot hold it
        // without GC-thrashing until export appears hung.
        val decision = FrameStoragePolicy.decide(
            width = 1280,
            height = 720,
            expectedFrameCount = 90,
            targetFps = 30f,
            memory = ampleMemory(
                availMb = 2048,
                memoryClassMb = 256,
                heapMaxMb = 256,
                heapUsedMb = 32,
            ),
        )
        assertEquals(FrameStorageMode.DISK, decision.mode)
        assertTrue(
            decision.reason.contains("heap") ||
                decision.reason.contains("memoryClass") ||
                decision.reason.contains("storage"),
        )
    }

    @Test
    fun typicalLargeHeapStillSpillsHdThreeSecondClipToDisk() {
        val decision = FrameStoragePolicy.decide(
            width = 1280,
            height = 720,
            expectedFrameCount = 90,
            targetFps = 30f,
            memory = ampleMemory(availMb = 2048, memoryClassMb = 512, heapMaxMb = 512),
        )
        assertEquals(FrameStorageMode.DISK, decision.mode)
        assertTrue(decision.reason.contains("heapMax") || decision.reason.contains("memoryClass"))
    }

    @Test
    fun fhdExceedsSoftPixelBudget() {
        val decision = FrameStoragePolicy.decide(
            width = 1920,
            height = 1080,
            expectedFrameCount = 90,
            targetFps = 30f,
            memory = ampleMemory(availMb = 2048, memoryClassMb = 2048, heapMaxMb = 2048),
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
            memory = ampleMemory(availMb = 2048, memoryClassMb = 2048, heapMaxMb = 2048),
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
            memory = ampleMemory(availMb = 2048, memoryClassMb = 128, heapMaxMb = 128),
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
