package com.boomeranger.app.media

import com.boomeranger.app.util.AppLogger
import com.boomeranger.app.util.GifBytes
import com.boomeranger.app.util.GifPlaybackTiming
import java.io.File

/**
 * Encodes a frame sequence (in-memory bitmaps or JPEG files) into a looping GIF89a.
 *
 * Pixel work lives in [GifBytes] (fast 5-5-5 palette + LZW). A global color table
 * is written so Android's decoder does not fall back to a gray empty frame.
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
            val firstOpened = outputFrames[0].openBitmap(width, height)
            try {
                val screenW = firstOpened.bitmap.width
                val screenH = firstOpened.bitmap.height
                val firstPixels = IntArray(screenW * screenH)
                firstOpened.bitmap.getPixels(firstPixels, 0, screenW, 0, 0, screenW, screenH)
                val firstQuantized = GifBytes.quantize(firstPixels)
                GifBytes.writePreamble(out, screenW, screenH, firstQuantized.palette)
                GifBytes.writeFrameIndexed(
                    out,
                    firstQuantized,
                    screenW,
                    screenH,
                    timing.delayCs,
                )
            } finally {
                firstOpened.recycleIfOwned()
            }
            onProgress(1f / outputFrames.size)

            for (index in 1 until outputFrames.size) {
                onProgress(index.toFloat() / outputFrames.size)
                val opened = outputFrames[index].openBitmap(width, height)
                try {
                    val w = opened.bitmap.width
                    val h = opened.bitmap.height
                    val pixels = IntArray(w * h)
                    opened.bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                    GifBytes.writeFrame(out, pixels, w, h, timing.delayCs)
                } finally {
                    opened.recycleIfOwned()
                }
                onProgress((index + 1).toFloat() / outputFrames.size)
            }

            GifBytes.writeTrailer(out)
            out.flush()
        }

        AppLogger.i(
            "Encoded GIF ${outputFile.name}: ${outputFrames.size} frames " +
                "(stride=${timing.frameStride}, ${speedMultiplier.coerceIn(1, 4)}x) " +
                "delay=${timing.delayCs}cs, ${outputFile.length()} bytes"
        )
    }
}
