package com.boomeranger.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GifPlaybackTimingTest {

    @Test
    fun oneXKeepsEveryFrameAtThirtyFpsDelay() {
        val plan = GifPlaybackTiming.plan(90, 30f, 1)
        assertEquals(1, plan.frameStride)
        assertEquals(3, plan.delayCs)
        assertEquals(90, plan.outputFrameCount)
        assertEquals(2_700L, plan.durationMs())
    }

    @Test
    fun twoXIsHalfTheDurationOfOneX() {
        val oneX = GifPlaybackTiming.plan(80, 30f, 1)
        val twoX = GifPlaybackTiming.plan(80, 30f, 2)
        assertEquals(2, twoX.frameStride)
        assertEquals(oneX.delayCs, twoX.delayCs)
        assertEquals(oneX.durationMs() / 2, twoX.durationMs())
    }

    @Test
    fun fourXIsQuarterTheDurationOfOneX() {
        val oneX = GifPlaybackTiming.plan(80, 30f, 1)
        val fourX = GifPlaybackTiming.plan(80, 30f, 4)
        assertEquals(4, fourX.frameStride)
        assertEquals(oneX.delayCs, fourX.delayCs)
        assertEquals(oneX.durationMs() / 4, fourX.durationMs())
    }

    @Test
    fun fourXIsFasterThanTwoX() {
        val twoX = GifPlaybackTiming.plan(80, 30f, 2)
        val fourX = GifPlaybackTiming.plan(80, 30f, 4)
        assertTrue(fourX.durationMs() < twoX.durationMs())
        assertEquals(twoX.durationMs() / 2, fourX.durationMs())
    }

    @Test
    fun delayStaysDecoderSafeAtEverySpeed() {
        listOf(1, 2, 4).forEach { speed ->
            val plan = GifPlaybackTiming.plan(60, 30f, speed)
            assertTrue(
                "delay ${plan.delayCs}cs at ${speed}x is below decoder-safe floor",
                plan.delayCs >= GifPlaybackTiming.MIN_DELAY_CS,
            )
        }
    }

    @Test
    fun fourXKeepsAboutOneQuarterOfAThreeSecondClip() {
        val frames = (0 until 90).toList()
        val sampled = GifPlaybackTiming.selectFrames(frames, 4)
        assertEquals(23, sampled.size)
        val oneX = GifPlaybackTiming.plan(90, 30f, 1)
        val fourX = GifPlaybackTiming.plan(sampled.size, 30f, 1)
        assertEquals(oneX.delayCs, fourX.delayCs)
        assertTrue(fourX.durationMs() <= oneX.durationMs() / 3)
    }

    @Test
    fun selectFramesAppliesStrideAndKeepsEndsWhenNeeded() {
        val frames = (0 until 8).toList()
        assertEquals(frames, GifPlaybackTiming.selectFrames(frames, 1))
        assertEquals(listOf(0, 2, 4, 6), GifPlaybackTiming.selectFrames(frames, 2))
        assertEquals(listOf(0, 4), GifPlaybackTiming.selectFrames(frames, 4))
    }

    @Test
    fun veryShortClipDoesNotDropBelowTwoFrames() {
        val plan = GifPlaybackTiming.plan(3, 30f, 4)
        assertTrue(plan.outputFrameCount >= 2)
        assertTrue(plan.frameStride <= 2)
        val selected = GifPlaybackTiming.selectFrames(listOf("a", "b", "c"), plan.frameStride)
        assertTrue(selected.size >= 2)
    }
}
