package com.boomeranger.app.media

import com.boomeranger.app.model.VideoMetadata
import com.boomeranger.app.util.AppLogger
import java.io.File

/**
 * Builds a reversed MP4 segment from a prepared forward clip.
 *
 * Media3 has no first-class "reverse frames" edit, so reverse generation is explicit:
 * extract oriented frames → encode them last-to-first as H.264/MP4 (video only).
 *
 * Tradeoff: this re-encodes and typically yields SDR output even if the source was HDR,
 * because frames are materialized as bitmaps.
 */
class ReverseVideoBuilder(
    private val frameExtractor: BitmapFrameExtractor = BitmapFrameExtractor(),
    private val frameEncoder: FrameSequenceEncoder = FrameSequenceEncoder(),
) {

    data class ReverseResult(
        val reverseFile: File,
        val width: Int,
        val height: Int,
        val frameRate: Float,
        val frameCount: Int,
    )

    fun buildReversedSegment(
        preparedForwardFile: File,
        workDir: File,
        sourceMetadata: VideoMetadata,
        onProgress: (Float) -> Unit = {},
    ): ReverseResult {
        workDir.mkdirs()
        val framesDir = File(workDir, "frames").also { it.mkdirs() }
        val reverseFile = File(workDir, "reverse.mp4")

        AppLogger.i("Generating reverse segment from ${preparedForwardFile.name}")

        val extraction = frameExtractor.extractToJpegSequence(
            inputFile = preparedForwardFile,
            outputDir = framesDir,
            maxDurationMs = VideoMetadata.MAX_INPUT_DURATION_MS,
            onProgress = { p -> onProgress(p * 0.55f) },
        )

        val reversedFrames = extraction.frameFiles.asReversed()

        frameEncoder.encode(
            frameFiles = reversedFrames,
            outputFile = reverseFile,
            width = extraction.width,
            height = extraction.height,
            frameRate = extraction.frameRate,
            sourceBitrate = sourceMetadata.bitrate,
            sourceWidth = sourceMetadata.orientedWidth,
            sourceHeight = sourceMetadata.orientedHeight,
            onProgress = { p -> onProgress(0.55f + p * 0.45f) },
        )

        // Best-effort cleanup of bulky JPEG frames.
        framesDir.listFiles()?.forEach { runCatching { it.delete() } }

        return ReverseResult(
            reverseFile = reverseFile,
            width = extraction.width,
            height = extraction.height,
            frameRate = extraction.frameRate,
            frameCount = extraction.frameFiles.size,
        )
    }
}
