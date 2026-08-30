package com.boomeranger.app.media

/**
 * CPU color quantization for GIF89a.
 *
 * GIF LZW is sequential and a poor GPU fit (upload + readback of full frames usually
 * costs more than a 15-bit lookup table on device CPUs). This quantizer:
 * - builds a 256-color popularity palette in 5-5-5 RGB space
 * - maps pixels through a 32×32×32 lookup table (O(1) per pixel)
 */
object GifColorQuantizer {
    const val MAX_COLORS = 256
    const val BUCKETS = 32 * 32 * 32

    fun red(argb: Int): Int = (argb ushr 16) and 0xFF

    fun green(argb: Int): Int = (argb ushr 8) and 0xFF

    fun blue(argb: Int): Int = argb and 0xFF

    fun packArgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    fun pack555(argb: Int): Int {
        val r = red(argb) ushr 3
        val g = green(argb) ushr 3
        val b = blue(argb) ushr 3
        return (r shl 10) or (g shl 5) or b
    }

    fun unpack555(key: Int): Int {
        val r = ((key ushr 10) and 0x1F) * 255 / 31
        val g = ((key ushr 5) and 0x1F) * 255 / 31
        val b = (key and 0x1F) * 255 / 31
        return packArgb(r, g, b)
    }

    fun buildPalette(pixels: IntArray, maxColors: Int = MAX_COLORS): IntArray {
        val counts = IntArray(BUCKETS)
        for (pixel in pixels) {
            counts[pack555(pixel)]++
        }

        val used = ArrayList<Int>(256)
        for (key in counts.indices) {
            if (counts[key] > 0) used += key
        }
        used.sortByDescending { counts[it] }

        val take = used.size.coerceAtMost(maxColors).coerceAtLeast(2)
        return IntArray(take) { i ->
            if (i < used.size) unpack555(used[i]) else 0
        }
    }

    /**
     * 32 768-entry table: each 5-5-5 RGB bucket maps to the nearest palette index.
     * Build once per palette; index pixels in O(1).
     */
    fun buildLookupTable(palette: IntArray): ByteArray {
        val lut = ByteArray(BUCKETS)
        val pr = IntArray(palette.size)
        val pg = IntArray(palette.size)
        val pb = IntArray(palette.size)
        for (i in palette.indices) {
            pr[i] = red(palette[i])
            pg[i] = green(palette[i])
            pb[i] = blue(palette[i])
        }
        for (key in 0 until BUCKETS) {
            val r = ((key ushr 10) and 0x1F) * 255 / 31
            val g = ((key ushr 5) and 0x1F) * 255 / 31
            val b = (key and 0x1F) * 255 / 31
            var best = 0
            var bestDist = Int.MAX_VALUE
            for (i in palette.indices) {
                val dr = r - pr[i]
                val dg = g - pg[i]
                val db = b - pb[i]
                val dist = dr * dr + dg * dg + db * db
                if (dist < bestDist) {
                    bestDist = dist
                    best = i
                    if (dist == 0) break
                }
            }
            lut[key] = best.toByte()
        }
        return lut
    }

    fun indexPixels(pixels: IntArray, lookupTable: ByteArray): ByteArray {
        val indexed = ByteArray(pixels.size)
        for (i in pixels.indices) {
            indexed[i] = lookupTable[pack555(pixels[i])]
        }
        return indexed
    }
}
