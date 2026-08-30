package com.boomeranger.app.util

import com.boomeranger.app.model.ExportFormat
import com.boomeranger.app.model.ResolutionOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for downscale-only resolution policy.
 */
class OutputSizeResolverTest {

    @Test
    fun originalKeepsSourceDimensionsEvenAligned() {
        val size = OutputSizeResolver.resolve(1920, 1080, ResolutionOption.ORIGINAL)
        assertEquals(1920, size.width)
        assertEquals(1080, size.height)
    }

    @Test
    fun fhdDoesNotUpscaleSmallerVideo() {
        val size = OutputSizeResolver.resolve(1280, 720, ResolutionOption.FHD)
        assertEquals(1280, size.width)
        assertEquals(720, size.height)
    }

    @Test
    fun hdDownscalesLandscapePreservingAspect() {
        val size = OutputSizeResolver.resolve(3840, 2160, ResolutionOption.HD)
        assertEquals(1280, size.width)
        assertEquals(720, size.height)
    }

    @Test
    fun hdDownscalesPortraitPreservingAspect() {
        val size = OutputSizeResolver.resolve(1080, 1920, ResolutionOption.HD)
        assertEquals(720, size.width)
        assertEquals(1280, size.height)
    }

    @Test
    fun gifCapsOriginal1080pTo480p() {
        val size = OutputSizeResolver.resolve(
            sourceWidth = 1920,
            sourceHeight = 1080,
            option = ResolutionOption.ORIGINAL,
            format = ExportFormat.GIF,
        )
        assertEquals(852, size.width)
        assertEquals(480, size.height)
    }

    @Test
    fun gifCapsPortraitTo480pBox() {
        val size = OutputSizeResolver.resolve(
            sourceWidth = 1080,
            sourceHeight = 1920,
            option = ResolutionOption.ORIGINAL,
            format = ExportFormat.GIF,
        )
        assertEquals(480, size.width)
        assertEquals(852, size.height)
    }

    @Test
    fun gifDoesNotUpscaleSmallerClip() {
        val size = OutputSizeResolver.resolve(
            sourceWidth = 640,
            sourceHeight = 360,
            option = ResolutionOption.ORIGINAL,
            format = ExportFormat.GIF,
        )
        assertEquals(640, size.width)
        assertEquals(360, size.height)
    }

    @Test
    fun gifSquareFitsInside480pBox() {
        val size = OutputSizeResolver.resolve(
            sourceWidth = 1080,
            sourceHeight = 1080,
            option = ResolutionOption.ORIGINAL,
            format = ExportFormat.GIF,
        )
        assertEquals(480, size.width)
        assertEquals(480, size.height)
        assertTrue(size.width <= OutputSizeResolver.GIF_MAX_SHORT_EDGE)
    }
}
