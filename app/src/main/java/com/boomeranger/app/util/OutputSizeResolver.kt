package com.boomeranger.app.util

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
 */
object OutputSizeResolver {

    fun resolve(
        sourceWidth: Int,
        sourceHeight: Int,
        option: ResolutionOption,
    ): VideoSize {
        require(sourceWidth > 0 && sourceHeight > 0) {
            "Invalid source size: ${sourceWidth}x$sourceHeight"
        }

        if (option == ResolutionOption.ORIGINAL) {
            return evenSize(sourceWidth, sourceHeight)
        }

        val landscapeCap = when (option) {
            ResolutionOption.FHD -> 1920 to 1080
            ResolutionOption.HD -> 1280 to 720
            ResolutionOption.ORIGINAL -> error("unreachable")
        }

        val isPortrait = sourceHeight > sourceWidth
        val (maxWidth, maxHeight) = if (isPortrait) {
            landscapeCap.second to landscapeCap.first
        } else {
            landscapeCap
        }

        val exceeds = sourceWidth > maxWidth || sourceHeight > maxHeight
        if (!exceeds) {
            return evenSize(sourceWidth, sourceHeight)
        }

        val widthScale = maxWidth.toFloat() / sourceWidth
        val heightScale = maxHeight.toFloat() / sourceHeight
        val scale = min(widthScale, heightScale)
        val width = (sourceWidth * scale).roundToInt()
        val height = (sourceHeight * scale).roundToInt()
        return evenSize(width, height)
    }

    /** Many encoders require even dimensions for YUV420. */
    private fun evenSize(width: Int, height: Int): VideoSize {
        val w = width.downToEven().coerceAtLeast(2)
        val h = height.downToEven().coerceAtLeast(2)
        return VideoSize(w, h)
    }

    private fun Int.downToEven(): Int = this and -2
}
