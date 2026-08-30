package com.boomeranger.app.media

import android.graphics.Bitmap
import android.graphics.Canvas
import com.boomeranger.app.util.AppLogger
import com.boomeranger.app.util.GifPlaybackTiming
import com.boomeranger.app.util.GifWriter
import java.io.File

/**
 * Encodes a frame sequence into a looping GIF89a.
 *
 * Speed must already be applied to [frames] (drop every Nth source frame).
 * This writer keeps a decoder-safe ~30ms delay and does not subsample again.
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
        require(width > 0 && height > 0) { "Invalid GIF size ${width}x$height" }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        // Caller applies 2x/4x by sampling. Always encode the list as 1x timing
        // so we never drop twice or shrink delay into the 1cs decoder trap.
        val delayCs = GifPlaybackTiming.plan(
            sourceFrameCount = frames.size,
            sourceFps = frameRate,
            speedMultiplier = 1,
        ).delayCs

        outputFile.outputStream().use { out ->
            var headerWritten = false
            frames.forEachIndexed { index, frame ->
                val opened = frame.openBitmap(width, height)
                try {
                    if (!headerWritten) {
                        GifWriter.writeHeader(out, opened.bitmap.width, opened.bitmap.height)
                        headerWritten = true
                    }
                    val pixels = opened.bitmap.readArgbPixels()
                    GifWriter.writeFrame(
                        out,
                        GifWriter.Frame(
                            width = opened.bitmap.width,
                            height = opened.bitmap.height,
                            argb = pixels,
                            delayCs = delayCs,
                        ),
                    )
                } finally {
                    opened.recycleIfOwned()
                }
                onProgress((index + 1).toFloat() / frames.size)
            }
            GifWriter.writeTrailer(out)
        }

        AppLogger.i(
            "Encoded GIF ${outputFile.name}: ${frames.size} frames " +
                "(${speedMultiplier.coerceIn(1, 4)}x already sampled) " +
                "delay=${delayCs}cs, ${outputFile.length()} bytes"
        )
    }

    /**
     * Canvas-draw into a fresh software ARGB_8888 bitmap before [Bitmap.getPixels].
     * Hardware / F16 / wide-gamut frames otherwise yield empty or gray GIFs.
     */
    private fun Bitmap.readArgbPixels(): IntArray {
        if (isRecycled) error("Cannot read pixels from a recycled frame.")
        val w = width
        val h = height
        val software = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            software.setHasAlpha(false)
            val canvas = Canvas(software)
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.drawBitmap(this, 0f, 0f, null)
            val pixels = IntArray(w * h)
            software.getPixels(pixels, 0, w, 0, 0, w, h)
            return pixels
        } finally {
            if (!software.isRecycled) software.recycle()
        }
    }
}
