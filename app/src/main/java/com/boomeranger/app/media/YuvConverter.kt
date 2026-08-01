package com.boomeranger.app.media

import android.graphics.Bitmap
import android.media.Image

/**
 * Writes ARGB bitmaps into MediaCodec input Images (YUV_420_888 / flexible).
 * Honors each plane's row and pixel stride.
 */
object YuvConverter {

    fun fillImageFromBitmap(image: Image, bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        require(image.width >= width && image.height >= height) {
            "Codec image ${image.width}x${image.height} smaller than bitmap ${width}x$height"
        }

        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        for (row in 0 until height) {
            for (col in 0 until width) {
                val color = argb[row * width + col]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff

                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                yBuf.put(row * yRowStride + col * yPixelStride, y.toByte())

                if (row % 2 == 0 && col % 2 == 0) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val cRow = row / 2
                    val cCol = col / 2
                    uBuf.put(cRow * uRowStride + cCol * uPixelStride, u.toByte())
                    vBuf.put(cRow * vRowStride + cCol * vPixelStride, v.toByte())
                }
            }
        }
    }
}
