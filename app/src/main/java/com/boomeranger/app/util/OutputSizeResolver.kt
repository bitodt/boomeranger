package com.boomeranger.app.util

import com.boomeranger.app.model.ExportFormat
import com.boomeranger.app.model.ResolutionOption
import com.boomeranger.app.model.VideoSize
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Resolves export dimensions from oriented source size and the selected resolution policy.
 * Never upscales; always preserves aspect ratio.
 *
 * FHD/HD caps are orientation-aware: portrait sources use a swapped bounding box
 * (e.g. HD → 720×1280) so vertical clips are not crushed into a landscape frame.
 *
 * GIF always applies an extra 1080p box after the user option so 4K sources
 * do not encode multi-megapixel GIF frames.
 */
object OutputSizeResolver {

    /** 16:9 1080p. Portrait GIFs use the swapped box (1080×1920). */
    const val GIF_MAX_LONG_EDGE: Int = 1920
    const val GIF_MAX_SHORT_EDGE: Int = 1080

    fun resolve(
        sourceWidth: Int,
        sourceHeight: Int,
        option: ResolutionOption,
        format: ExportFormat = ExportFormat.MP4,
    ): VideoSize {
        require(sourceWidth > 0 && sourceHeight > 0) {
            "Invalid source size: ${sourceWidth}x$sourceHeight"
        }

        val selected = if (option == ResolutionOption.ORIGINAL) {
            evenSize(sourceWidth, sourceHeight)
        } else {
            fitToCap(sourceWidth, sourceHeight, optionCap(option, sourceWidth, sourceHeight))
        }

        if (format != ExportFormat.GIF) return selected
        return fitToCap(
            selected.width,
            selected.height,
            gifCap(selected.width, selected.height),
        )
    }

    private fun optionCap(
        option: ResolutionOption,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Pair<Int, Int> {
        val landscape = when (option) {
            ResolutionOption.FHD -> 1920 to 1080
            ResolutionOption.HD -> 1280 to 720
            ResolutionOption.ORIGINAL -> error("unreachable")
        }
        return orientedCap(landscape, sourceWidth, sourceHeight)
    }

    private fun gifCap(width: Int, height: Int): Pair<Int, Int> {
        return orientedCap(GIF_MAX_LONG_EDGE to GIF_MAX_SHORT_EDGE, width, height)
    }

    private fun orientedCap(
        landscape: Pair<Int, Int>,
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        return if (height > width) {
            landscape.second to landscape.first
        } else {
            landscape
        }
    }

    private fun fitToCap(width: Int, height: Int, cap: Pair<Int, Int>): VideoSize {
        val (maxWidth, maxHeight) = cap
        if (width <= maxWidth && height <= maxHeight) {
            return evenSize(width, height)
        }
        val scale = min(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        return evenSize(
            (width * scale).roundToInt(),
            (height * scale).roundToInt(),
        )
    }

    /** Many encoders require even dimensions for YUV420. */
    private fun evenSize(width: Int, height: Int): VideoSize {
        val w = width.downToEven().coerceAtLeast(2)
        val h = height.downToEven().coerceAtLeast(2)
        return VideoSize(w, h)
    }

    private fun Int.downToEven(): Int = this and -2
}
