package com.boomeranger.app.util

/**
 * GIF graphic-control delay is an integer number of 1/100s.
 *
 * Speeding up by shrinking delay alone fails at 2x/4x of 30 fps: both want a
 * sub-10ms delay and collapse to the same 1cs floor. Many players also treat
 * 1cs as 10cs, which makes "faster" GIFs play *slower*.
 *
 * Match MP4 speed by keeping the 1x delay and dropping frames (stride = speed).
 */
object GifPlaybackTiming {

    /** 20ms — lowest delay most GIF decoders honor without clamping. */
    const val MIN_DELAY_CS: Int = 2

    const val MAX_DELAY_CS: Int = 20

    data class Plan(
        val delayCs: Int,
        val frameStride: Int,
        val outputFrameCount: Int,
    ) {
        val frameDelayMs: Long get() = delayCs * 10L

        fun durationMs(): Long = outputFrameCount * frameDelayMs
    }

    fun plan(
        sourceFrameCount: Int,
        sourceFps: Float,
        speedMultiplier: Int,
    ): Plan {
        val fps = sourceFps.coerceIn(12f, 60f)
        val speed = speedMultiplier.coerceIn(1, 4)
        // Hold ~30fps timing (3cs at 30fps). Faster speeds drop frames instead
        // of shrinking delay — 1cs is ignored or treated as 10cs by many players.
        val delayCs = (100f / fps).toInt().coerceIn(MIN_DELAY_CS, MAX_DELAY_CS)

        var stride = speed
        var outputCount = outputFrameCount(sourceFrameCount, stride)
        while (stride > 1 && outputCount < 2) {
            stride--
            outputCount = outputFrameCount(sourceFrameCount, stride)
        }
        return Plan(
            delayCs = delayCs,
            frameStride = stride,
            outputFrameCount = outputCount,
        )
    }

    fun <T> selectFrames(frames: List<T>, stride: Int): List<T> {
        val safeStride = stride.coerceAtLeast(1)
        if (safeStride == 1 || frames.size <= 2) return frames
        val selected = frames.filterIndexed { index, _ -> index % safeStride == 0 }
        return if (selected.size >= 2) selected else frames.take(2)
    }

    private fun outputFrameCount(sourceCount: Int, stride: Int): Int {
        if (sourceCount <= 0 || stride <= 0) return 0
        return (sourceCount + stride - 1) / stride
    }
}
