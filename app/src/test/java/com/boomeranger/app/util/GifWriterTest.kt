package com.boomeranger.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class GifWriterTest {

    @Test
    fun writesGif89aWithoutGlobalColorTable() {
        val bytes = GifWriter.toByteArray(sampleFrames())
        assertEquals('G'.code, bytes[0].toInt() and 0xFF)
        assertEquals('I'.code, bytes[1].toInt() and 0xFF)
        assertEquals('F'.code, bytes[2].toInt() and 0xFF)
        assertEquals('8'.code, bytes[3].toInt() and 0xFF)
        assertEquals('9'.code, bytes[4].toInt() and 0xFF)
        assertEquals('a'.code, bytes[5].toInt() and 0xFF)
        assertEquals(8, readShort(bytes, 6))
        assertEquals(8, readShort(bytes, 8))
        assertEquals(0, bytes[10].toInt() and 0xFF)
        assertEquals(0x3B, bytes.last().toInt() and 0xFF)
    }

    @Test
    fun localTablesKeepRedAndBlueNotGray() {
        val red = GifWriter.rgb(220, 20, 20)
        val blue = GifWriter.rgb(20, 40, 220)
        val bytes = GifWriter.toByteArray(
            listOf(
                solidFrame(8, 8, red, delayCs = 3),
                solidFrame(8, 8, blue, delayCs = 3),
            )
        )
        val tables = readLocalColorTables(bytes)
        assertEquals(2, tables.size)
        assertTrue(
            "first LCT should be red-ish, was ${tables[0].first().toString(16)}",
            tables[0].any { isNear(it, 220, 20, 20) },
        )
        assertTrue(
            "second LCT should be blue-ish, was ${tables[1].first().toString(16)}",
            tables[1].any { isNear(it, 20, 40, 220) },
        )
        tables.forEach { table ->
            assertTrue(
                "LCT should not be a gray wash: ${table.map { it.toString(16) }}",
                table.any { !isGray(it) },
            )
        }
    }

    @Test
    fun delayIsWrittenAsCentiseconds() {
        val bytes = GifWriter.toByteArray(sampleFrames())
        val gce = bytes.indices.first { i ->
            (bytes[i].toInt() and 0xFF) == 0x21 &&
                i + 1 < bytes.size &&
                (bytes[i + 1].toInt() and 0xFF) == 0xF9
        }
        assertEquals(3, readShort(bytes, gce + 4))
    }

    private fun sampleFrames(): List<GifWriter.Frame> {
        return listOf(
            solidFrame(8, 8, GifWriter.rgb(220, 20, 20), 3),
            solidFrame(8, 8, GifWriter.rgb(20, 40, 220), 3),
        )
    }

    private fun solidFrame(width: Int, height: Int, color: Int, delayCs: Int): GifWriter.Frame {
        return GifWriter.Frame(
            width = width,
            height = height,
            argb = IntArray(width * height) { color },
            delayCs = delayCs,
        )
    }

    private fun isNear(color: Int, r: Int, g: Int, b: Int): Boolean {
        val dr = GifWriter.red(color) - r
        val dg = GifWriter.green(color) - g
        val db = GifWriter.blue(color) - b
        return dr * dr + dg * dg + db * db < 40 * 40
    }

    private fun isGray(color: Int): Boolean {
        val r = GifWriter.red(color)
        val g = GifWriter.green(color)
        val b = GifWriter.blue(color)
        return maxOf(r, g, b) - minOf(r, g, b) < 12
    }

    private fun readShort(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    /** Walk GIF89a (no GCT) and collect each frame's local color table. */
    private fun readLocalColorTables(bytes: ByteArray): List<IntArray> {
        val input = ByteArrayInputStream(bytes)
        val header = ByteArray(6)
        check(input.read(header) == 6)
        check(String(header) == "GIF89a")
        readShort(input); readShort(input)
        val packed = input.read()
        check(packed and 0x80 == 0) { "test expects no GCT" }
        input.read(); input.read()
        val tables = mutableListOf<IntArray>()
        while (true) {
            when (val b = input.read()) {
                -1 -> error("GIF ended without trailer")
                0x3B -> return tables
                0x21 -> {
                    when (val label = input.read()) {
                        0xF9 -> {
                            val len = input.read()
                            repeat(len) { input.read() }
                            check(input.read() == 0)
                        }
                        0xFF -> {
                            val len = input.read()
                            repeat(len) { input.read() }
                            skipBlocks(input)
                        }
                        else -> skipBlocks(input)
                    }
                }
                0x2C -> {
                    readShort(input); readShort(input)
                    readShort(input); readShort(input)
                    val desc = input.read()
                    check(desc and 0x80 != 0) { "expected local color table" }
                    val tableSize = 1 shl ((desc and 0x07) + 1)
                    val palette = IntArray(tableSize)
                    repeat(tableSize) { i ->
                        palette[i] = GifWriter.rgb(input.read(), input.read(), input.read())
                    }
                    tables += palette
                    input.read() // min code size
                    skipBlocks(input)
                }
                else -> error("Unexpected GIF byte 0x${b.toString(16)}")
            }
        }
    }

    private fun skipBlocks(input: ByteArrayInputStream) {
        while (true) {
            val len = input.read()
            if (len <= 0) return
            repeat(len) { input.read() }
        }
    }

    private fun readShort(input: ByteArrayInputStream): Int {
        val lo = input.read()
        val hi = input.read()
        return lo or (hi shl 8)
    }
}
