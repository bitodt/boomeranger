package com.boomeranger.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs

class GifBytesTest {

    @Test
    fun quantizeKeepsDominantPrimaryColors() {
        val red = GifBytes.rgb(220, 20, 20)
        val blue = GifBytes.rgb(20, 40, 220)
        val pixels = IntArray(16) { i -> if (i % 2 == 0) red else blue }
        val quantized = GifBytes.quantize(pixels, maxColors = 16)
        quantized.indexed.forEachIndexed { i, index ->
            val color = quantized.palette[index.toInt() and 0xFF]
            val dist = colorDistance(pixels[i], color)
            assertTrue("pixel $i too far from source (dist=$dist)", dist < 40)
        }
    }

    @Test
    fun writtenGifHasHeaderGctAndTwoFrames() {
        val bytes = twoFrameGif()
        assertEquals('G'.code, bytes[0].toInt() and 0xFF)
        assertEquals('I'.code, bytes[1].toInt() and 0xFF)
        assertEquals('F'.code, bytes[2].toInt() and 0xFF)
        assertEquals(0xF7, bytes[10].toInt() and 0xFF)
        assertEquals(0x3B, bytes.last().toInt() and 0xFF)
        val imageCount = bytes.count { it.toInt() and 0xFF == 0x2C }
        assertTrue("expected at least 2 image separators, found $imageCount", imageCount >= 2)
    }

    @Test
    fun ffmpegDecodesFirstFrameAsRedNotGray() {
        val ffmpeg = File("/usr/bin/ffmpeg")
        assumeTrue("ffmpeg required to verify GIF decode", ffmpeg.canExecute())

        val gif = File.createTempFile("boomeranger", ".gif")
        val raw = File.createTempFile("boomeranger", ".rgb")
        try {
            gif.writeBytes(twoFrameGif(width = 16, height = 16))
            val proc = ProcessBuilder(
                ffmpeg.absolutePath,
                "-v", "error",
                "-y",
                "-i", gif.absolutePath,
                "-frames:v", "1",
                "-f", "rawvideo",
                "-pix_fmt", "rgb24",
                raw.absolutePath,
            ).redirectErrorStream(true).start()
            val log = proc.inputStream.bufferedReader().readText()
            assertEquals("ffmpeg failed: $log", 0, proc.waitFor())
            val rgb = raw.readBytes()
            assertEquals(16 * 16 * 3, rgb.size)
            var redSum = 0
            var greenSum = 0
            var blueSum = 0
            var i = 0
            while (i < rgb.size) {
                redSum += rgb[i].toInt() and 0xFF
                greenSum += rgb[i + 1].toInt() and 0xFF
                blueSum += rgb[i + 2].toInt() and 0xFF
                i += 3
            }
            val n = 16 * 16
            val r = redSum / n
            val g = greenSum / n
            val b = blueSum / n
            assertTrue("first frame should be red, got rgb($r,$g,$b)", r > 150 && r > g + 80 && r > b + 80)
            assertTrue("first frame looks gray rgb($r,$g,$b)", abs(r - g) > 40)
        } finally {
            gif.delete()
            raw.delete()
        }
    }

    private fun twoFrameGif(width: Int = 8, height: Int = 8): ByteArray {
        val red = IntArray(width * height) { GifBytes.rgb(200, 10, 10) }
        val blue = IntArray(width * height) { GifBytes.rgb(10, 20, 200) }
        return ByteArrayOutputStream().use { out ->
            val first = GifBytes.quantize(red)
            GifBytes.writePreamble(out, width, height, first.palette)
            GifBytes.writeFrame(out, red, width, height, delayCs = 3)
            GifBytes.writeFrame(out, blue, width, height, delayCs = 3)
            GifBytes.writeTrailer(out)
            out.toByteArray()
        }
    }

    private fun colorDistance(a: Int, b: Int): Int {
        val dr = GifBytes.red(a) - GifBytes.red(b)
        val dg = GifBytes.green(a) - GifBytes.green(b)
        val db = GifBytes.blue(a) - GifBytes.blue(b)
        return dr * dr + dg * dg + db * db
    }
}
