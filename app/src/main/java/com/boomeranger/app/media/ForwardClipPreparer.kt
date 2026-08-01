package com.boomeranger.app.media

import androidx.media3.common.util.UnstableApi
import com.boomeranger.app.model.VideoMetadata
import com.boomeranger.app.model.VideoSize
import com.boomeranger.app.util.AppLogger
import java.io.File

/**
 * Trims to max duration, applies optional downscale, and removes audio when requested
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
        onProgress: (Float) -> Unit = {},
    ): File {
        val endMs = minOf(metadata.durationMs, VideoMetadata.MAX_INPUT_DURATION_MS)
        AppLogger.i(
            "Preparing forward clip: endMs=$endMs, size=${outputSize.width}x${outputSize.height}, " +
                "mute=$removeAudio"
        )

        val edited = transformHelper.buildTrimmedScaledItem(
            inputUri = inputFile.absolutePath,
            endPositionMs = endMs,
            outputWidth = outputSize.width,
            outputHeight = outputSize.height,
            removeAudio = removeAudio,
        )
        transformHelper.exportEditedItem(edited, outputFile, onProgress)
        return outputFile
    }
}
