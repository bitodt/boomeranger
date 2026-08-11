package com.boomeranger.app.util

import com.boomeranger.app.model.VideoMetadata

/**
 * Resolves a max-3s clip window inside [sourceDurationMs] starting at [requestedStartMs].
 */
object ClipWindowResolver {

    data class Window(
        val startMs: Long,
        val endMs: Long,
    ) {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    }

    fun resolve(
        sourceDurationMs: Long,
        requestedStartMs: Long,
        maxWindowMs: Long = VideoMetadata.MAX_INPUT_DURATION_MS,
    ): Window {
        require(sourceDurationMs > 0L) { "Invalid source duration." }
        val windowMs = minOf(sourceDurationMs, maxWindowMs)
        val maxStart = (sourceDurationMs - windowMs).coerceAtLeast(0L)
        val start = requestedStartMs.coerceIn(0L, maxStart)
        return Window(startMs = start, endMs = start + windowMs)
    }
}
