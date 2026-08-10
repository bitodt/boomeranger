package com.boomeranger.app.util

import com.boomeranger.app.model.ResolutionOption
import org.junit.Assert.assertEquals
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
}
