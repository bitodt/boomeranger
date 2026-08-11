package com.boomeranger.app.media

import androidx.media3.common.util.UnstableApi
import com.boomeranger.app.model.VideoMetadata
import com.boomeranger.app.model.VideoSize
import com.boomeranger.app.util.AppLogger
import com.boomeranger.app.util.ClipWindowResolver
import java.io.File

/**
 * Trims to a ≤3s window, applies optional downscale, and removes audio when requested
 * using Media3 Transformer — where it is strongest.
 */
@UnstableApi
class ForwardClipPreparer(
    private val transformHelper: Media3TransformHelper,
) {

    suspend fun prepare(
        inputFile: File,
        metadata: VideoMetadata,
        outputSize: VideoSize,
        removeAudio: Boolean,
        outputFile: File,
        trimStartMs: Long = 0L,
        onProgress: (Float) -> Unit = {},
    ): File {
        val window = ClipWindowResolver.resolve(
            sourceDurationMs = metadata.durationMs,
            requestedStartMs = trimStartMs,
        )
        AppLogger.i(
            "Preparing forward clip: ${window.startMs}→${window.endMs}ms " +
                "(${window.durationMs}ms), size=${outputSize.width}x${outputSize.height}, " +
                "mute=$removeAudio"
        )

        val edited = transformHelper.buildTrimmedScaledItem(
            inputUri = inputFile.absolutePath,
            startPositionMs = window.startMs,
            endPositionMs = window.endMs,
            outputWidth = outputSize.width,
            outputHeight = outputSize.height,
            removeAudio = removeAudio,
        )
        transformHelper.exportEditedItem(edited, outputFile, onProgress)
        return outputFile
    }
}
