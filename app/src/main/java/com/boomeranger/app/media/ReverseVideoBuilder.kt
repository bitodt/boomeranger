package com.boomeranger.app.media

import com.boomeranger.app.model.VideoMetadata
import com.boomeranger.app.util.AppLogger
import java.io.File

/**
 * Builds forward/reverse MP4 segments (and shared frame lists for GIF) from a prepared clip.
 *
 * Media3 has no first-class "reverse frames" edit, so reverse generation is explicit:
 * extract oriented frames at the chosen fps → encode last-to-first as H.264/MP4.
 */
class ReverseVideoBuilder(
    private val frameExtractor: BitmapFrameExtractor = BitmapFrameExtractor(),
    private val frameEncoder: FrameSequenceEncoder = FrameSequenceEncoder(),
) {

    data class FrameBundle(
        val forwardFrames: List<File>,
        val width: Int,
        val height: Int,
        val frameRate: Float,
    )

    data class ReverseResult(
        val reverseFile: File,
        val forwardFile: File?,
        val frameBundle: FrameBundle,
        val frameCount: Int,
    ) {
        val width: Int get() = frameBundle.width
        val height: Int get() = frameBundle.height
        val frameRate: Float get() = frameBundle.frameRate
    }

    fun extractFrames(
        preparedForwardFile: File,
        framesDir: File,
        targetFrameRate: Float,
        onProgress: (Float) -> Unit = {},
    ): FrameBundle {
        framesDir.mkdirs()
        val extraction = frameExtractor.extractToJpegSequence(
            inputFile = preparedForwardFile,
            outputDir = framesDir,
            maxDurationMs = VideoMetadata.MAX_INPUT_DURATION_MS,
            targetFrameRate = targetFrameRate,
            onProgress = onProgress,
        )
        return FrameBundle(
            forwardFrames = extraction.frameFiles,
            width = extraction.width,
            height = extraction.height,
            frameRate = extraction.frameRate,
        )
    }

    fun buildSegments(
        preparedForwardFile: File,
        workDir: File,
        sourceMetadata: VideoMetadata,
        targetFrameRate: Float,
        encodeForwardFromFrames: Boolean,
        onProgress: (Float) -> Unit = {},
    ): ReverseResult {
        workDir.mkdirs()
        val framesDir = File(workDir, "frames").also { it.mkdirs() }

        AppLogger.i(
            "Building segments from ${preparedForwardFile.name} @ ${targetFrameRate}fps"
        )

        val bundle = extractFrames(
            preparedForwardFile = preparedForwardFile,
            framesDir = framesDir,
            targetFrameRate = targetFrameRate,
            onProgress = { p -> onProgress(p * 0.45f) },
        )

        var progressBase = 0.45f
        val forwardEncoded = if (encodeForwardFromFrames) {
            val out = File(workDir, "forward_reencoded.mp4")
            frameEncoder.encode(
                frameFiles = bundle.forwardFrames,
                outputFile = out,
                width = bundle.width,
                height = bundle.height,
                frameRate = bundle.frameRate,
                sourceBitrate = sourceMetadata.bitrate,
                sourceWidth = sourceMetadata.orientedWidth,
                sourceHeight = sourceMetadata.orientedHeight,
                onProgress = { p -> onProgress(progressBase + p * 0.25f) },
            )
            progressBase = 0.70f
            out
        } else {
            null
        }

        val reverseFile = File(workDir, "reverse.mp4")
        frameEncoder.encode(
            frameFiles = bundle.forwardFrames.asReversed(),
            outputFile = reverseFile,
            width = bundle.width,
            height = bundle.height,
            frameRate = bundle.frameRate,
            sourceBitrate = sourceMetadata.bitrate,
            sourceWidth = sourceMetadata.orientedWidth,
            sourceHeight = sourceMetadata.orientedHeight,
            onProgress = { p -> onProgress(progressBase + p * (1f - progressBase)) },
        )

        return ReverseResult(
            reverseFile = reverseFile,
            forwardFile = forwardEncoded,
            frameBundle = bundle,
            frameCount = bundle.forwardFrames.size,
        )
    }

    fun buildBoomerangFrameCycle(
        forwardFrames: List<File>,
        repeatCount: Int,
    ): List<File> {
        val reverse = forwardFrames.asReversed()
        // Drop the duplicated turning-point frame once so the loop doesn't stutter.
        val reverseTail = if (reverse.size > 2) reverse.drop(1).dropLast(1) else reverse
        val cycle = forwardFrames + reverseTail
        return buildList {
            repeat(repeatCount.coerceIn(2, 4)) {
                addAll(cycle)
            }
        }
    }
}
