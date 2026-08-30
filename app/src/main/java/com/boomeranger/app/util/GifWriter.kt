package com.boomeranger.app.util

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.min

/**
 * GIF89a writer used by [com.boomeranger.app.media.GifSequenceEncoder].
 *
 * Byte layout matches the last device-playable encoder:
 * no global color table, per-frame local tables, string-dictionary LZW.
 *
 * Kept free of Android types so the stream can be unit-tested on the JVM.
 */
object GifWriter {

    data class Frame(
        val width: Int,
        val height: Int,
        val argb: IntArray,
        val delayCs: Int,
    ) {
        init {
            require(width > 0 && height > 0) { "Invalid frame size ${width}x$height" }
            require(argb.size == width * height) {
                "Pixel buffer ${argb.size} does not match ${width}x$height"
            }
        }
    }

    fun writeHeader(out: OutputStream, width: Int, height: Int) {
        require(width > 0 && height > 0) { "Invalid GIF size ${width}x$height" }
        writeString(out, "GIF89a")
        writeLogicalScreen(out, width, height)
        writeNetscapeLoop(out)
    }

    fun writeFrame(out: OutputStream, frame: Frame) {
        writeImage(out, frame)
    }

    fun writeTrailer(out: OutputStream) {
        out.write(0x3B)
        out.flush()
    }

    fun write(out: OutputStream, frames: List<Frame>) {
        require(frames.size >= 2) { "GIF needs at least 2 frames." }
        val screenW = frames.first().width
        val screenH = frames.first().height
        writeHeader(out, screenW, screenH)
        for (frame in frames) {
            require(frame.width == screenW && frame.height == screenH) {
                "Frame size ${frame.width}x${frame.height} does not match ${screenW}x$screenH"
            }
            writeFrame(out, frame)
        }
        writeTrailer(out)
    }

    fun toByteArray(frames: List<Frame>): ByteArray {
        val buffer = ByteArrayOutputStream()
        write(buffer, frames)
        return buffer.toByteArray()
    }

    private fun writeLogicalScreen(out: OutputStream, width: Int, height: Int) {
        writeShort(out, width)
        writeShort(out, height)
        // No global color table — each frame carries its own local table.
        out.write(0x00)
        out.write(0x00)
        out.write(0x00)
    }

    private fun writeNetscapeLoop(out: OutputStream) {
        out.write(0x21)
        out.write(0xFF)
        out.write(0x0B)
        writeString(out, "NETSCAPE2.0")
        out.write(0x03)
        out.write(0x01)
        writeShort(out, 0)
        out.write(0x00)
    }

    private fun writeImage(out: OutputStream, frame: Frame) {
        val palette = buildPalette(frame.argb, maxColors = 256)
        val indexed = ByteArray(frame.argb.size)
        for (i in frame.argb.indices) {
            indexed[i] = nearestIndex(frame.argb[i], palette).toByte()
        }

        out.write(0x21)
        out.write(0xF9)
        out.write(0x04)
        out.write(0x04)
        writeShort(out, frame.delayCs.coerceIn(2, 20))
        out.write(0x00)
        out.write(0x00)

        out.write(0x2C)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, frame.width)
        writeShort(out, frame.height)
        val sizeFlag = paletteSizeToFlag(palette.size)
        out.write(0x80 or sizeFlag)
        val tableEntries = 1 shl (sizeFlag + 1)
        for (i in 0 until tableEntries) {
            if (i < palette.size) {
                val c = palette[i]
                out.write(red(c))
                out.write(green(c))
                out.write(blue(c))
            } else {
                out.write(0)
                out.write(0)
                out.write(0)
            }
        }

        val minCodeSize = (sizeFlag + 1).coerceAtLeast(2)
        out.write(minCodeSize)
        writeLzw(out, indexed, minCodeSize)
    }

    internal fun buildPalette(pixels: IntArray, maxColors: Int): IntArray {
        val counts = HashMap<Int, Int>(min(pixels.size, 4096))
        for (pixel in pixels) {
            val r = (red(pixel) ushr 3) shl 10
            val g = (green(pixel) ushr 3) shl 5
            val b = blue(pixel) ushr 3
            val key = r or g or b
            counts[key] = (counts[key] ?: 0) + 1
        }
        val sorted = counts.entries.sortedByDescending { it.value }
        val take = min(maxColors, sorted.size).coerceAtLeast(2)
        return IntArray(take) { i ->
            val key = sorted[min(i, sorted.lastIndex)].key
            rgb(
                ((key ushr 10) and 0x1F) * 255 / 31,
                ((key ushr 5) and 0x1F) * 255 / 31,
                (key and 0x1F) * 255 / 31,
            )
        }
    }

    internal fun nearestIndex(color: Int, palette: IntArray): Int {
        val r = red(color)
        val g = green(color)
        val b = blue(color)
        var best = 0
        var bestDist = Int.MAX_VALUE
        for (i in palette.indices) {
            val pr = red(palette[i])
            val pg = green(palette[i])
            val pb = blue(palette[i])
            val dr = r - pr
            val dg = g - pg
            val db = b - pb
            val dist = dr * dr + dg * dg + db * db
            if (dist < bestDist) {
                bestDist = dist
                best = i
            }
        }
        return best
    }

    private fun paletteSizeToFlag(size: Int): Int {
        var entries = 2
        var flag = 0
        while (entries < size && flag < 7) {
            entries = entries shl 1
            flag++
        }
        return flag
    }

    private fun writeLzw(out: OutputStream, indexed: ByteArray, minCodeSize: Int) {
        val clear = 1 shl minCodeSize
        val end = clear + 1
        var codeSize = minCodeSize + 1
        var nextCode = end + 1
        val maxCode = 4096

        val bitBuffer = BitAccumulator(out)
        val dictionary = HashMap<String, Int>(512)

        fun resetDict() {
            dictionary.clear()
            for (i in 0 until clear) {
                dictionary[i.toChar().toString()] = i
            }
            codeSize = minCodeSize + 1
            nextCode = end + 1
        }

        resetDict()
        bitBuffer.writeBits(clear, codeSize)

        if (indexed.isEmpty()) {
            bitBuffer.writeBits(end, codeSize)
            bitBuffer.flush()
            out.write(0)
            return
        }

        var w = (indexed[0].toInt() and 0xFF).toChar().toString()
        var i = 1
        while (i < indexed.size) {
            val k = (indexed[i].toInt() and 0xFF).toChar()
            val wk = w + k
            if (dictionary.containsKey(wk)) {
                w = wk
            } else {
                bitBuffer.writeBits(dictionary.getValue(w), codeSize)
                if (nextCode < maxCode) {
                    dictionary[wk] = nextCode++
                    if (nextCode >= (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    bitBuffer.writeBits(clear, codeSize)
                    resetDict()
                }
                w = k.toString()
            }
            i++
        }
        bitBuffer.writeBits(dictionary.getValue(w), codeSize)
        bitBuffer.writeBits(end, codeSize)
        bitBuffer.flush()
        out.write(0)
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun writeString(out: OutputStream, value: String) {
        for (ch in value) out.write(ch.code)
    }

    internal fun red(color: Int): Int = (color ushr 16) and 0xFF

    internal fun green(color: Int): Int = (color ushr 8) and 0xFF

    internal fun blue(color: Int): Int = color and 0xFF

    internal fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private class BitAccumulator(private val out: OutputStream) {
        private var acc = 0
        private var bits = 0
        private val block = ByteArray(255)
        private var blockLen = 0

        fun writeBits(code: Int, codeSize: Int) {
            acc = acc or (code shl bits)
            bits += codeSize
            while (bits >= 8) {
                appendByte(acc and 0xFF)
                acc = acc ushr 8
                bits -= 8
            }
        }

        fun flush() {
            if (bits > 0) {
                appendByte(acc and 0xFF)
                acc = 0
                bits = 0
            }
            if (blockLen > 0) flushBlock()
        }

        private fun appendByte(value: Int) {
            block[blockLen++] = value.toByte()
            if (blockLen == 255) flushBlock()
        }

        private fun flushBlock() {
            out.write(blockLen)
            out.write(block, 0, blockLen)
            blockLen = 0
        }
    }
}
