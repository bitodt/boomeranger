package com.boomeranger.app.media

import androidx.media3.common.util.UnstableApi
import com.boomeranger.app.util.AppLogger
import java.io.File

/**
 * Concatenates forward + reverse into boomerang cycles using Media3 Transformer composition.
 */
@UnstableApi
class VideoConcatenationService(
    private val transformHelper: Media3TransformHelper,
) {

    suspend fun concatenateBoomerang(
        forwardFile: File,
        reverseFile: File,
        repeatCount: Int,
        outputFile: File,
        removeAudio: Boolean,
        onProgress: (Float) -> Unit = {},
    ) {
        AppLogger.i(
            "Concatenating boomerang: repeats=$repeatCount, " +
                "forward=${forwardFile.name}, reverse=${reverseFile.name}"
        )
        val composition = transformHelper.buildBoomerangComposition(
            forwardFile = forwardFile,
            reverseFile = reverseFile,
            repeatCount = repeatCount,
            removeAudio = removeAudio,
        )
        transformHelper.exportComposition(composition, outputFile, onProgress)
    }
}
