package com.boomeranger.app.util

/**
 * High-quality bitrate heuristic when exact source bitrate cloning is unavailable.
 * Prefer the larger of source bitrate (scaled by pixel ratio) and a pixels-per-second baseline.
 */
object BitrateCalculator {

    fun calculateBitsPerSecond(
        width: Int,
        height: Int,
        frameRate: Float,
        sourceBitrate: Int?,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Int {
        val fps = frameRate.coerceIn(15f, 60f)
        // ~0.2 bits/pixel/frame is a solid high-quality H.264 starting point for short clips.
        val baseline = (width * height * fps * 0.20f).toInt()

        val scaledSource = if (sourceBitrate != null && sourceBitrate > 0 &&
            sourceWidth > 0 && sourceHeight > 0
        ) {
            val pixelRatio =
                (width.toDouble() * height) / (sourceWidth.toDouble() * sourceHeight)
            (sourceBitrate * pixelRatio).toInt()
        } else {
            0
        }

        val chosen = maxOf(baseline, scaledSource)
        // Clamp to a practical on-device range.
        return chosen.coerceIn(2_000_000, 40_000_000)
    }
}
