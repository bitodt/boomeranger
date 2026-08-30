package com.boomeranger.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GifColorQuantizerTest {

    @Test
    fun pack555RoundTripKeeps5BitRgb() {
        val color = GifColorQuantizer.packArgb(248, 16, 80)
        val packed = GifColorQuantizer.pack555(color)
        val restored = GifColorQuantizer.unpack555(packed)
        assertEquals(GifColorQuantizer.pack555(color), GifColorQuantizer.pack555(restored))
    }

    @Test
    fun paletteKeepsDominantColors() {
        val red = GifColorQuantizer.packArgb(255, 0, 0)
        val blue = GifColorQuantizer.packArgb(0, 0, 255)
        val pixels = IntArray(100) { if (it < 80) red else blue }

        val palette = GifColorQuantizer.buildPalette(pixels, maxColors = 8)
        assertTrue(palette.size in 2..8)

        val lut = GifColorQuantizer.buildLookupTable(palette)
        val indexed = GifColorQuantizer.indexPixels(pixels, lut)
        val redIndex = indexed[0]
        val blueIndex = indexed[90]
        assertTrue(redIndex != blueIndex)

        val mappedRed = palette[redIndex.toInt() and 0xFF]
        val mappedBlue = palette[blueIndex.toInt() and 0xFF]
        assertTrue(GifColorQuantizer.red(mappedRed) > GifColorQuantizer.blue(mappedRed))
        assertTrue(GifColorQuantizer.blue(mappedBlue) > GifColorQuantizer.red(mappedBlue))
    }

    @Test
    fun lookupTableMapsPaletteColorToItself() {
        val palette = intArrayOf(
            GifColorQuantizer.packArgb(255, 0, 0),
            GifColorQuantizer.packArgb(0, 255, 0),
            GifColorQuantizer.packArgb(0, 0, 255),
        )
        val lut = GifColorQuantizer.buildLookupTable(palette)
        palette.forEachIndexed { index, color ->
            val mapped = lut[GifColorQuantizer.pack555(color)].toInt() and 0xFF
            assertEquals("palette[$index] should map to itself", index, mapped)
        }
    }

    @Test
    fun tinyPaletteIsPaddedToAtLeastTwoEntries() {
        val pixels = IntArray(16) { GifColorQuantizer.packArgb(10, 10, 10) }
        val palette = GifColorQuantizer.buildPalette(pixels, maxColors = 256)
        assertTrue(palette.size >= 2)
    }
}
