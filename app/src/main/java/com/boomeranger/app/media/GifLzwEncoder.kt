package com.boomeranger.app.media

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * GIF89a LZW compressor (GIF deferred bit-width increment).
 *
 * LZW is sequential — it cannot be usefully offloaded to a GPU.
 * Bit width grows after a write when the next free code exceeds the current
 * max, matching the classic GIF `output()` behavior used by Android
 * AnimatedGifEncoder. Incrementing immediately on table insert (TIFF-style)
 * produces streams Pillow and Android cannot decode once the table passes
 * 512 / 1024 / 2048 codes.
 */
object GifLzwEncoder {
    private const val MAX_BITS = 12
    private const val MAX_MAX_CODE = 1 shl MAX_BITS

    /**
     * Returns GIF image-data sub-blocks (length-prefixed chunks + a 0 terminator).
     * The caller writes [minCodeSize] as a single byte before this payload.
     */
    fun encode(indexed: ByteArray, minCodeSize: Int): ByteArray {
        val out = ByteArrayOutputStream(indexed.size / 2 + 64)
        write(out, indexed, minCodeSize)
        return out.toByteArray()
    }

    fun write(out: OutputStream, indexed: ByteArray, minCodeSize: Int) {
        val clear = 1 shl minCodeSize
        val end = clear + 1
        var nBits = minCodeSize + 1
        var maxCode = (1 shl nBits) - 1
        var nextCode = end + 1
        var clearFlag = false

        val bits = BitAccumulator(out)
        val dictionary = HashMap<Long, Int>(1024)

        fun pack(prefix: Int, pixel: Int): Long =
            (prefix.toLong() shl 12) or (pixel.toLong() and 0xFF)

        fun output(code: Int) {
            bits.writeBits(code, nBits)
            if (nextCode > maxCode || clearFlag) {
                if (clearFlag) {
                    nBits = minCodeSize + 1
                    maxCode = (1 shl nBits) - 1
                    clearFlag = false
                } else {
                    nBits++
                    maxCode = if (nBits == MAX_BITS) MAX_MAX_CODE else (1 shl nBits) - 1
                }
            }
        }

        fun resetTable() {
            dictionary.clear()
            nextCode = end + 1
            clearFlag = true
        }

        output(clear)

        if (indexed.isEmpty()) {
            output(end)
            bits.flush()
            out.write(0)
            return
        }

        var prefix = indexed[0].toInt() and 0xFF
        var i = 1
        while (i < indexed.size) {
            val pixel = indexed[i].toInt() and 0xFF
            val key = pack(prefix, pixel)
            val existing = dictionary[key]
            if (existing != null) {
                prefix = existing
            } else {
                output(prefix)
                if (nextCode < MAX_MAX_CODE) {
                    dictionary[key] = nextCode++
                } else {
                    resetTable()
                    output(clear)
                }
                prefix = pixel
            }
            i++
        }
        output(prefix)
        output(end)
        bits.flush()
        out.write(0)
    }

    /** Packs LZW codes into GIF sub-blocks of ≤255 bytes. */
    private class BitAccumulator(private val out: OutputStream) {
        private var acc = 0
        private var bits = 0
        private val block = ByteArray(255)
        private var blockLen = 0

        fun writeBits(code: Int, codeSize: Int) {
            if (bits > 0) {
                acc = acc and ((1 shl bits) - 1)
                acc = acc or (code shl bits)
            } else {
                acc = code
            }
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
