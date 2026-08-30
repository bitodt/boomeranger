package com.boomeranger.app.util

import java.io.OutputStream
import kotlin.math.min

/**
 * GIF89a container + popularity quantizer + LZW.
 *
 * Kept free of Android types so the writer can be unit-tested on the JVM.
 */
object GifBytes {

    data class Quantized(
        val palette: IntArray,
        val indexed: ByteArray,
    )

    fun writePreamble(out: OutputStream, width: Int, height: Int, globalPalette: IntArray) {
        writeString(out, "GIF89a")
        writeShort(out, width)
        writeShort(out, height)
        // GCT flag + 8-bit color resolution + 256-entry table. Some Android
        // decoders paint a gray frame when the logical screen has no GCT.
        out.write(0xF7)
        out.write(0x00) // background index
        out.write(0x00) // pixel aspect
        writeColorTable(out, globalPalette, entries = 256)
        writeNetscapeLoop(out)
    }

    fun writeFrame(
        out: OutputStream,
        pixels: IntArray,
        width: Int,
        height: Int,
        delayCs: Int,
    ) {
        require(pixels.size == width * height) {
            "Pixel count ${pixels.size} != ${width}x$height"
        }
        val quantized = quantize(pixels, maxColors = 256)
        writeFrameIndexed(out, quantized, width, height, delayCs)
    }

    fun writeTrailer(out: OutputStream) {
        out.write(0x3B)
    }

    fun writeFrameIndexed(
        out: OutputStream,
        quantized: Quantized,
        width: Int,
        height: Int,
        delayCs: Int,
    ) {
        out.write(0x21)
        out.write(0xF9)
        out.write(0x04)
        out.write(0x04) // disposal = do not dispose, no transparent color
        writeShort(out, delayCs.coerceIn(1, 65535))
        out.write(0x00)
        out.write(0x00)

        out.write(0x2C)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, width)
        writeShort(out, height)
        val sizeFlag = paletteSizeToFlag(quantized.palette.size)
        out.write(0x80 or sizeFlag)
        writeColorTable(out, quantized.palette, entries = 1 shl (sizeFlag + 1))

        val minCodeSize = (sizeFlag + 1).coerceAtLeast(2)
        out.write(minCodeSize)
        writeLzw(out, quantized.indexed, minCodeSize)
    }

    fun quantize(pixels: IntArray, maxColors: Int = 256): Quantized {
        val counts = HashMap<Int, Int>(min(pixels.size, 4096))
        val keys = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val key = pack555(red(pixel), green(pixel), blue(pixel))
            keys[i] = key
            counts[key] = (counts[key] ?: 0) + 1
        }
        if (counts.isEmpty()) {
            return Quantized(intArrayOf(rgb(0, 0, 0), rgb(255, 255, 255)), ByteArray(0))
        }
        val sorted = counts.entries.sortedByDescending { it.value }
        val take = min(maxColors, sorted.size).coerceAtLeast(1)
        val palette = IntArray(take.coerceAtLeast(2)) { i ->
            val key = sorted[min(i, sorted.lastIndex)].key
            rgb(expand5(key ushr 10), expand5(key ushr 5), expand5(key))
        }
        val paletteKeys = IntArray(take) { sorted[it].key }
        val keyToIndex = HashMap<Int, Int>(counts.size)
        for (i in paletteKeys.indices) {
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
        return Quantized(palette, indexed)
    }

    /**
     * GIF LZW encode using the original string-dictionary algorithm.
     * The integer-code rewrite produced streams Android/Skia paint as gray.
     */
    fun writeLzw(out: OutputStream, indexed: ByteArray, minCodeSize: Int) {
        val clear = 1 shl minCodeSize
        val end = clear + 1
        var codeSize = minCodeSize + 1
        var nextCode = end + 1
        val maxCode = 4096
        val bits = BitAccumulator(out)
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
        bits.writeBits(clear, codeSize)

        if (indexed.isEmpty()) {
            bits.writeBits(end, codeSize)
            bits.flush()
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
                bits.writeBits(dictionary.getValue(w), codeSize)
                if (nextCode < maxCode) {
                    dictionary[wk] = nextCode++
                    if (nextCode >= (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    bits.writeBits(clear, codeSize)
                    resetDict()
                }
                w = k.toString()
            }
            i++
        }
        bits.writeBits(dictionary.getValue(w), codeSize)
        bits.writeBits(end, codeSize)
        bits.flush()
        out.write(0)
    }

    fun red(color: Int): Int = (color ushr 16) and 0xFF
    fun green(color: Int): Int = (color ushr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF
    fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun pack555(r: Int, g: Int, b: Int): Int =
        ((r ushr 3) shl 10) or ((g ushr 3) shl 5) or (b ushr 3)

    private fun expand5(bits: Int): Int = (bits and 0x1F) * 255 / 31

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

    private fun writeColorTable(out: OutputStream, palette: IntArray, entries: Int) {
        for (i in 0 until entries) {
            val color = if (i < palette.size) palette[i] else 0
            out.write(red(color))
            out.write(green(color))
            out.write(blue(color))
        }
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

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun writeString(out: OutputStream, value: String) {
        for (ch in value) out.write(ch.code)
    }

    class BitAccumulator(private val out: OutputStream) {
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
