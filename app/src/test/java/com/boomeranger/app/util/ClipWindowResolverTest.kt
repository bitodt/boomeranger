package com.boomeranger.app.util

import com.boomeranger.app.model.VideoMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipWindowResolverTest {

    @Test
    fun shortClipUsesFullDurationFromZero() {
        val window = ClipWindowResolver.resolve(sourceDurationMs = 2_000L, requestedStartMs = 500L)
        assertEquals(0L, window.startMs)
        assertEquals(2_000L, window.endMs)
    }

    @Test
    fun longClipClampsStartAndKeepsThreeSeconds() {
        val window = ClipWindowResolver.resolve(
            sourceDurationMs = 10_000L,
            requestedStartMs = 4_500L,
        )
        assertEquals(4_500L, window.startMs)
        assertEquals(7_500L, window.endMs)
        assertEquals(VideoMetadata.MAX_INPUT_DURATION_MS, window.durationMs)
    }

    @Test
    fun startCannotExceedMaxWindowStart() {
        val window = ClipWindowResolver.resolve(
            sourceDurationMs = 10_000L,
            requestedStartMs = 9_000L,
        )
        assertEquals(7_000L, window.startMs)
        assertEquals(10_000L, window.endMs)
    }
}
