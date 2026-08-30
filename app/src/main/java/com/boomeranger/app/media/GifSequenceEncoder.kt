package com.boomeranger.app.media

import android.graphics.Bitmap
import android.graphics.Color
import com.boomeranger.app.util.AppLogger
import com.boomeranger.app.util.GifPlaybackTiming
import java.io.File
import java.io.OutputStream
import kotlin.math.min

/**
 * Encodes a frame sequence (in-memory bitmaps or JPEG files) into a looping GIF89a.
 *
 * Uses a per-frame popularity palette (256 colors) + LZW. Palette mapping is
 * O(unique 5-5-5 buckets); LZW uses integer keys. Combined with the 1080p GIF
 * cap this stays interactive for short boomerang clips.
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
            writeLogicalScreen(out, width, height)
            writeNetscapeLoop(out)

            outputFrames.forEachIndexed { index, frame ->
                onProgress(index.toFloat() / outputFrames.size)
                val opened = frame.openBitmap(width, height)
                try {
                    writeFrame(out, opened.bitmap, timing.delayCs)
                } finally {
                    opened.recycleIfOwned()
                }
                onProgress((index + 1).toFloat() / outputFrames.size)
            }

            out.write(0x3B) // trailer
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
        // Global color table flag off; will use local tables per frame.
        out.write(0x00)
        out.write(0x00) // background color index
        out.write(0x00) // pixel aspect ratio
    }

    private fun writeNetscapeLoop(out: OutputStream) {
        out.write(0x21) // extension
        out.write(0xFF) // application extension
        out.write(0x0B)
        writeString(out, "NETSCAPE2.0")
        out.write(0x03)
        out.write(0x01)
        writeShort(out, 0) // loop forever
        out.write(0x00)
    }

    private fun writeFrame(out: OutputStream, bitmap: Bitmap, delayCs: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val (palette, indexed) = quantize(pixels, maxColors = 256)

        // Graphics Control Extension
        out.write(0x21)
        out.write(0xF9)
        out.write(0x04)
        out.write(0x04) // disposal = do not dispose, no transparent color
        writeShort(out, delayCs)
        out.write(0x00) // transparent color index
        out.write(0x00)

        // Image Descriptor
        out.write(0x2C)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, width)
        writeShort(out, height)
        val paletteSize = palette.size
        val sizeFlag = paletteSizeToFlag(paletteSize)
        out.write(0x80 or sizeFlag) // local color table flag + size

        // Local color table (padded to 2^(sizeFlag+1) entries)
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
    }

    /**
     * Popularity quantizer on 5-5-5 buckets, then map every unique bucket to a
     * palette index once. Per-pixel nearest-neighbor against 256 colors is far
     * too slow for 1080p frame sequences.
     */
    private fun quantize(pixels: IntArray, maxColors: Int): Pair<IntArray, ByteArray> {
        val counts = HashMap<Int, Int>(min(pixels.size, 4096))
        val keys = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val key = ((Color.red(pixel) ushr 3) shl 10) or
                ((Color.green(pixel) ushr 3) shl 5) or
                (Color.blue(pixel) ushr 3)
            keys[i] = key
            counts[key] = (counts[key] ?: 0) + 1
        }
        val sorted = counts.entries.sortedByDescending { it.value }
        val take = min(maxColors, sorted.size).coerceAtLeast(2)
        val palette = IntArray(take) { i ->
            val key = sorted[i].key
            Color.rgb(
                ((key ushr 10) and 0x1F) * 255 / 31,
                ((key ushr 5) and 0x1F) * 255 / 31,
                (key and 0x1F) * 255 / 31,
            )
        }
        val paletteKeys = IntArray(take) { sorted[it].key }
        val keyToIndex = HashMap<Int, Int>(counts.size)
        for (i in 0 until take) {
            keyToIndex[paletteKeys[i]] = i
        }
        for (key in counts.keys) {
            if (key !in keyToIndex) {
                keyToIndex[key] = nearestBucket(key, paletteKeys)
            }
        }
        val indexed = ByteArray(pixels.size)
        for (i in keys.indices) {
            indexed[i] = keyToIndex.getValue(keys[i]).toByte()
        }
        return palette to indexed
    }

    private fun nearestBucket(key: Int, paletteKeys: IntArray): Int {
        val r = (key ushr 10) and 0x1F
        val g = (key ushr 5) and 0x1F
        val b = key and 0x1F
        var best = 0
        var bestDist = Int.MAX_VALUE
        for (i in paletteKeys.indices) {
            val pk = paletteKeys[i]
            val dr = r - ((pk ushr 10) and 0x1F)
            val dg = g - ((pk ushr 5) and 0x1F)
            val db = b - (pk and 0x1F)
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
        // (prefixCode << 8) | suffixByte. Single-byte codes 0..clear-1 are implicit.
        val dictionary = HashMap<Int, Int>(512)

        fun pack(prefix: Int, suffix: Int): Int = (prefix shl 8) or suffix

        fun resetDict() {
            dictionary.clear()
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

        var w = indexed[0].toInt() and 0xFF
        var i = 1
        while (i < indexed.size) {
            val k = indexed[i].toInt() and 0xFF
            val wk = pack(w, k)
            val existing = dictionary[wk]
            if (existing != null) {
                w = existing
            } else {
                bitBuffer.writeBits(w, codeSize)
                if (nextCode < maxCode) {
                    dictionary[wk] = nextCode++
                    if (nextCode >= (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    bitBuffer.writeBits(clear, codeSize)
                    resetDict()
                }
                w = k
            }
            i++
        }
        bitBuffer.writeBits(w, codeSize)
        bitBuffer.writeBits(end, codeSize)
        bitBuffer.flush()
        out.write(0) // block terminator
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun writeString(out: OutputStream, value: String) {
        for (ch in value) out.write(ch.code)
    }

    /** Packs LZW codes into GIF sub-blocks of ≤255 bytes. */
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
