package com.boomeranger.app.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class GifLzwEncoderTest {

    @Test
    fun solidFrameCompressesAndRoundTrips() {
        val indexed = ByteArray(64) { 3 }
        val payload = GifLzwEncoder.encode(indexed, minCodeSize = 8)
        assertTrue(payload.isNotEmpty())
        assertEquals(0, payload.last().toInt() and 0xFF)
        assertArrayEquals(indexed, decodeLzw(payload, minCodeSize = 8, pixelCount = indexed.size))
    }

    @Test
    fun repeatingPatternRoundTrips() {
        val indexed = ByteArray(200) { i -> (i % 7).toByte() }
        val payload = GifLzwEncoder.encode(indexed, minCodeSize = 8)
        assertArrayEquals(indexed, decodeLzw(payload, minCodeSize = 8, pixelCount = indexed.size))
    }

    @Test
    fun longStreamRoundTripsAcrossCodeSizeGrowth() {
        val indexed = ByteArray(8000) { i -> (i % 17).toByte() }
        val payload = GifLzwEncoder.encode(indexed, minCodeSize = 8)
        assertArrayEquals(indexed, decodeLzw(payload, minCodeSize = 8, pixelCount = indexed.size))
    }

    @Test
    fun encodeIsDeterministic() {
        val indexed = ByteArray(128) { i -> (i * 13).toByte() }
        val first = GifLzwEncoder.encode(indexed, minCodeSize = 8)
        val second = GifLzwEncoder.encode(indexed, minCodeSize = 8)
        assertArrayEquals(first, second)
    }

    @Test
    fun emptyInputStillWritesTerminator() {
        val payload = GifLzwEncoder.encode(ByteArray(0), minCodeSize = 8)
        assertEquals(0, payload.last().toInt() and 0xFF)
    }

    /**
     * Minimal GIF LZW decoder for the sub-block stream produced by [GifLzwEncoder].
     */
    private fun decodeLzw(payload: ByteArray, minCodeSize: Int, pixelCount: Int): ByteArray {
        val bits = BlockBitReader(payload)
        val clear = 1 shl minCodeSize
        val end = clear + 1
        var codeSize = minCodeSize + 1
        val table = ArrayList<ByteArray>(4096)

        fun reset() {
            table.clear()
            repeat(clear) { value -> table += byteArrayOf(value.toByte()) }
            table += byteArrayOf() // clear
            table += byteArrayOf() // end
            codeSize = minCodeSize + 1
        }

        reset()
        val out = ArrayList<Byte>(pixelCount)
        var previous: ByteArray? = null
        while (true) {
            val code = bits.readBits(codeSize) ?: break
            if (code == clear) {
                reset()
                previous = null
                continue
            }
            if (code == end) break
            val sequence = when {
                code < table.size -> table[code]
                previous != null -> previous + byteArrayOf(previous[0])
                else -> error("Invalid LZW stream")
            }
            sequence.forEach { out += it }
            if (previous != null && table.size < 4096) {
                table += previous + byteArrayOf(sequence[0])
                if (table.size >= (1 shl codeSize) && codeSize < 12) {
                    codeSize++
                }
            }
            previous = sequence
        }
        return out.toByteArray()
    }

    private class BlockBitReader(payload: ByteArray) {
        private val bytes: ByteArray
        private var byteIndex = 0
        private var acc = 0
        private var bits = 0

        init {
            val unpacked = ArrayList<Byte>()
            val input = ByteArrayInputStream(payload)
            while (true) {
                val len = input.read()
                if (len <= 0) break
                repeat(len) {
                    val value = input.read()
                    if (value < 0) error("Truncated GIF LZW block")
                    unpacked += value.toByte()
                }
            }
            bytes = unpacked.toByteArray()
        }

        fun readBits(codeSize: Int): Int? {
            while (bits < codeSize) {
                if (byteIndex >= bytes.size) return null
                acc = acc or ((bytes[byteIndex].toInt() and 0xFF) shl bits)
                byteIndex++
                bits += 8
            }
            val value = acc and ((1 shl codeSize) - 1)
            acc = acc ushr codeSize
            bits -= codeSize
            return value
        }
    }
}
