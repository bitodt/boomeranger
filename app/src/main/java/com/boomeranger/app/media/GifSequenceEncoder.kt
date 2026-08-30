package com.boomeranger.app.media

import android.graphics.Bitmap
import android.graphics.Color
import com.boomeranger.app.util.AppLogger
import com.boomeranger.app.util.GifPlaybackTiming
import java.io.File
import java.io.OutputStream
import kotlin.math.min

/**
 * Encodes a frame sequence into a looping GIF89a.
 *
 * Byte layout matches the last known-good writer: no global color table,
 * per-frame local tables, string-dictionary LZW. Speed is applied by dropping
 * frames and keeping a decoder-safe delay — not by shrinking delay below 2cs.
 */
class GifSequenceEncoder {

    fun encode(
        frames: List<FrameHandle>,
        outputFile: File,
        frameRate: Float,
        width: Int,
        height: Int,
        speedMultiplier: Int = 1,
        onProgress: (Float) -> Unit = {},
    ) {
        require(frames.size >= 2) { "GIF needs at least 2 frames." }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val timing = GifPlaybackTiming.plan(
            sourceFrameCount = frames.size,
            sourceFps = frameRate,
            speedMultiplier = speedMultiplier,
        )
        val outputFrames = GifPlaybackTiming.selectFrames(frames, timing.frameStride)
        require(outputFrames.size >= 2) { "GIF needs at least 2 frames after speed sampling." }

        outputFile.outputStream().use { out ->
            writeString(out, "GIF89a")
            val firstOpened = outputFrames[0].openBitmap(width, height)
            val screenW = firstOpened.bitmap.width
            val screenH = firstOpened.bitmap.height
            writeLogicalScreen(out, screenW, screenH)
            writeNetscapeLoop(out)
            try {
                writeFrame(out, firstOpened.bitmap, timing.delayCs)
            } finally {
                firstOpened.recycleIfOwned()
            }
            onProgress(1f / outputFrames.size)

            for (index in 1 until outputFrames.size) {
                onProgress(index.toFloat() / outputFrames.size)
                val opened = outputFrames[index].openBitmap(screenW, screenH)
                try {
                    writeFrame(out, opened.bitmap, timing.delayCs)
                } finally {
                    opened.recycleIfOwned()
                }
                onProgress((index + 1).toFloat() / outputFrames.size)
            }

            out.write(0x3B)
            out.flush()
        }

        AppLogger.i(
            "Encoded GIF ${outputFile.name}: ${outputFrames.size} frames " +
                "(stride=${timing.frameStride}, ${speedMultiplier.coerceIn(1, 4)}x) " +
                "delay=${timing.delayCs}cs, ${outputFile.length()} bytes"
        )
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

    private fun writeFrame(out: OutputStream, bitmap: Bitmap, delayCs: Int) {
        val software = bitmap.softwareCopy()
        try {
            val width = software.width
            val height = software.height
            val pixels = IntArray(width * height)
            software.getPixels(pixels, 0, width, 0, 0, width, height)

            val palette = buildPalette(pixels, maxColors = 256)
            val indexed = ByteArray(pixels.size)
            for (i in pixels.indices) {
                indexed[i] = nearestIndex(pixels[i], palette).toByte()
            }

            out.write(0x21)
            out.write(0xF9)
            out.write(0x04)
            out.write(0x04)
            writeShort(out, delayCs)
            out.write(0x00)
            out.write(0x00)

            out.write(0x2C)
            writeShort(out, 0)
            writeShort(out, 0)
            writeShort(out, width)
            writeShort(out, height)
            val sizeFlag = paletteSizeToFlag(palette.size)
            out.write(0x80 or sizeFlag)
            val tableEntries = 1 shl (sizeFlag + 1)
            for (i in 0 until tableEntries) {
                if (i < palette.size) {
                    val c = palette[i]
                    out.write(Color.red(c))
                    out.write(Color.green(c))
                    out.write(Color.blue(c))
                } else {
                    out.write(0)
                    out.write(0)
                    out.write(0)
                }
            }

            val minCodeSize = (sizeFlag + 1).coerceAtLeast(2)
            out.write(minCodeSize)
            writeLzw(out, indexed, minCodeSize)
        } finally {
            if (software !== bitmap && !software.isRecycled) software.recycle()
        }
    }

    private fun Bitmap.softwareCopy(): Bitmap {
        val config = config
        if (config == Bitmap.Config.ARGB_8888 || config == Bitmap.Config.RGB_565) {
            return this
        }
        return copy(Bitmap.Config.ARGB_8888, false)
            ?: error("Could not copy frame ${width}x$height to ARGB_8888.")
    }

    private fun buildPalette(pixels: IntArray, maxColors: Int): IntArray {
        val counts = HashMap<Int, Int>(min(pixels.size, 4096))
        for (pixel in pixels) {
            val r = (Color.red(pixel) ushr 3) shl 10
            val g = (Color.green(pixel) ushr 3) shl 5
            val b = Color.blue(pixel) ushr 3
            val key = r or g or b
            counts[key] = (counts[key] ?: 0) + 1
        }
        val sorted = counts.entries.sortedByDescending { it.value }
        val take = min(maxColors, sorted.size).coerceAtLeast(2)
        return IntArray(take) { i ->
            val key = sorted[min(i, sorted.lastIndex)].key
            Color.rgb(
                ((key ushr 10) and 0x1F) * 255 / 31,
                ((key ushr 5) and 0x1F) * 255 / 31,
                (key and 0x1F) * 255 / 31,
            )
        }
    }

    private fun nearestIndex(color: Int, palette: IntArray): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        var best = 0
        var bestDist = Int.MAX_VALUE
        for (i in palette.indices) {
            val pr = Color.red(palette[i])
            val pg = Color.green(palette[i])
            val pb = Color.blue(palette[i])
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
