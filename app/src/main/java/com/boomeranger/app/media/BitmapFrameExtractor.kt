package com.boomeranger.app.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import com.boomeranger.app.util.AppLogger
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Extracts oriented frames from a prepared clip for reverse encoding / GIF assembly.
 *
 * Uses MediaMetadataRetriever so rotation/display orientation is already applied to bitmaps.
 * Frames are written to disk as JPEG to avoid holding a full ARGB frame list in RAM.
 */
class BitmapFrameExtractor {

    data class ExtractionResult(
        val frameFiles: List<File>,
        val width: Int,
        val height: Int,
        val frameRate: Float,
    )

    /**
     * @param targetFrameRate when set (e.g. 30 or 60), samples the clip at that rate.
     *                        when null, falls back to source capture fps / heuristics.
     */
    fun extractToJpegSequence(
        inputFile: File,
        outputDir: File,
        maxDurationMs: Long,
        targetFrameRate: Float? = null,
        onProgress: (Float) -> Unit = {},
    ): ExtractionResult {
        outputDir.mkdirs()
        outputDir.listFiles()?.forEach { it.delete() }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(inputFile.absolutePath)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: error("Missing duration for frame extraction.")
            val clipDurationMs = minOf(durationMs, maxDurationMs).coerceAtLeast(1L)

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: error("Missing width for frame extraction.")
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: error("Missing height for frame extraction.")
            val rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0

            val orientedWidth = if (rotation % 180 == 0) width else height
            val orientedHeight = if (rotation % 180 == 0) height else width

            val declaredFps = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
            )?.toFloatOrNull()

            val frameCountHint = if (Build.VERSION.SDK_INT >= 28) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                    ?.toIntOrNull()
            } else {
                null
            }

            val inferredFps = when {
                declaredFps != null && declaredFps > 1f -> declaredFps.coerceIn(12f, 60f)
                frameCountHint != null && durationMs > 0 -> {
                    (frameCountHint * 1000f / durationMs).coerceIn(12f, 60f)
                }
                else -> 30f
            }
            val fps = (targetFrameRate ?: inferredFps).coerceIn(12f, 60f)

            val expectedFrames = max(2, ((clipDurationMs / 1000.0) * fps).roundToInt())
            val frameFiles = ArrayList<File>(expectedFrames)

            // Time-based sampling honors the user-selected fps for both 30 and 60 paths.
            val intervalUs = 1_000_000.0 / fps
            val endUs = clipDurationMs * 1000L
            var timeUs = 0.0
            var index = 0
            while (timeUs <= endUs) {
                val bitmap = retriever.getFrameAtTime(
                    timeUs.toLong(),
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                if (bitmap != null) {
                    val file = File(outputDir, "frame_%05d.jpg".format(index))
                    writeJpeg(bitmap, file)
                    bitmap.recycle()
                    frameFiles += file
                    index++
                }
                timeUs += intervalUs
                onProgress((timeUs / endUs).toFloat().coerceIn(0f, 1f))
            }

            if (frameFiles.size < 2) {
                error("Need at least 2 frames to build a boomerang reverse segment.")
            }

            AppLogger.i(
                "Extracted ${frameFiles.size} frames at ${fps}fps " +
                    "(target=${targetFrameRate ?: "auto"}), " +
                    "oriented ${orientedWidth}x$orientedHeight"
            )

            val probe = android.graphics.BitmapFactory.decodeFile(frameFiles.first().absolutePath)
                ?: error("Failed to decode extracted frame.")
            val outW = probe.width
            val outH = probe.height
            probe.recycle()

            return ExtractionResult(
                frameFiles = frameFiles,
                width = outW,
                height = outH,
                frameRate = fps,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun writeJpeg(bitmap: Bitmap, file: File) {
        file.outputStream().use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                error("Failed to compress frame to JPEG: ${file.name}")
            }
        }
    }
}
