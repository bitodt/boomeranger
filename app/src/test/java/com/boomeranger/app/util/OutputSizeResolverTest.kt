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
    fun gifKeeps720pSource() {
        val size = OutputSizeResolver.resolve(
            sourceWidth = 1280,
            sourceHeight = 720,
            option = ResolutionOption.ORIGINAL,
            format = ExportFormat.GIF,
        )
        assertEquals(1280, size.width)
        assertEquals(720, size.height)
    }

    @Test
    fun gifCaps1080pAnd4kLandscapeTo720p() {
        val fhd = OutputSizeResolver.resolve(
            sourceWidth = 1920,
            sourceHeight = 1080,
            option = ResolutionOption.ORIGINAL,
            format = ExportFormat.GIF,
        )
        assertEquals(1280, fhd.width)
        assertEquals(720, fhd.height)

        val size = OutputSizeResolver.resolve(
            sourceWidth = 3840,
            sourceHeight = 2160,
            option = ResolutionOption.ORIGINAL,
            format = ExportFormat.GIF,
        )
        assertEquals(1280, size.width)
        assertEquals(720, size.height)
    }

    @Test
    fun gifCaps4kPortraitTo720pBox() {
        val size = OutputSizeResolver.resolve(
            sourceWidth = 2160,
            sourceHeight = 3840,
            option = ResolutionOption.ORIGINAL,
            format = ExportFormat.GIF,
        )
        assertEquals(720, size.width)
        assertEquals(1280, size.height)
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
    fun gifSquareFitsInside720pBox() {
        val size = OutputSizeResolver.resolve(
            sourceWidth = 2160,
            sourceHeight = 2160,
            option = ResolutionOption.ORIGINAL,
            format = ExportFormat.GIF,
        )
        assertEquals(720, size.width)
        assertEquals(720, size.height)
        assertTrue(size.width <= OutputSizeResolver.GIF_MAX_SHORT_EDGE)
        assertTrue(size.height <= OutputSizeResolver.GIF_MAX_LONG_EDGE)
    }
}
