package com.boomeranger.app.media

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class GifBoomerangCycleTest {

    @Test
    fun cycleReusesTheSameFrameHandles() {
        val frames = (0..9).map { FrameHandle.Disk(File("frame_$it.jpg")) }
        val cycle = ReverseVideoBuilder().buildBoomerangFrameCycle(frames, repeatCount = 3)
        // reverse tail drops the duplicated turning-point frames
        assertEquals(3 * (10 + 8), cycle.size)
        assertEquals(10, cycle.distinct().size)
    }
}
