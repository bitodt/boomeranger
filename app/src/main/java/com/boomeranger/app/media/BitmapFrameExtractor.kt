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
 *
 * Storage is hybrid:
 * - [FrameStorageMode.MEMORY] copies retriever frames into app-owned ARGB bitmaps
 *   (the original retriever bitmap is recycled immediately so extract cannot stall)
 * - [FrameStorageMode.DISK] writes JPEGs to avoid holding a full frame list in RAM
 *
 * If the memory path runs out of RAM mid-extract, callers should fall back to disk
 * (see [ReverseVideoBuilder]).
 */
class BitmapFrameExtractor {

    data class ExtractionResult(
        val frames: List<FrameHandle>,
        val width: Int,
        val height: Int,
        val frameRate: Float,
        val storageMode: FrameStorageMode,
    ) {
        val frameCount: Int get() = frames.size
    }

    /**
     * @param targetFrameRate when set (e.g. 30 or 60), samples the clip at that rate.
     *                        when null, falls back to source capture fps / heuristics.
     * @param storageMode MEMORY keeps bitmaps; DISK writes JPEG files under [outputDir].
     */
    fun extract(
        inputFile: File,
        outputDir: File,
        maxDurationMs: Long,
        storageMode: FrameStorageMode,
        targetFrameRate: Float? = null,
        onProgress: (Float) -> Unit = {},
    ): ExtractionResult {
        outputDir.mkdirs()
        outputDir.listFiles()?.forEach { it.delete() }

        val retriever = MediaMetadataRetriever()
        val memoryFrames = if (storageMode == FrameStorageMode.MEMORY) {
            ArrayList<Bitmap>()
        } else {
            null
        }
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
            val frames = ArrayList<FrameHandle>(expectedFrames)

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
                    when (storageMode) {
                        FrameStorageMode.MEMORY -> {
                            // Copy then recycle the retriever bitmap. Keeping native frames
                            // alive makes subsequent getFrameAtTime calls progressively
                            // slower and can stall the extract loop entirely.
                            val copy = try {
                                copyRetrieverBitmap(bitmap)
                            } finally {
                                if (!bitmap.isRecycled) bitmap.recycle()
                            }
                            memoryFrames!!.add(copy)
                            frames += FrameHandle.Memory(copy)
                        }
                        FrameStorageMode.DISK -> {
                            val file = File(outputDir, "frame_%05d.jpg".format(index))
                            writeJpeg(bitmap, file)
                            bitmap.recycle()
                            frames += FrameHandle.Disk(file)
                        }
                    }
                    index++
                }
                timeUs += intervalUs
                onProgress((timeUs / endUs).toFloat().coerceIn(0f, 1f))
            }

            if (frames.size < 2) {
                recycleBitmaps(memoryFrames)
                error("Need at least 2 frames to build a boomerang reverse segment.")
            }

            val (outW, outH) = when (storageMode) {
                FrameStorageMode.MEMORY -> {
                    val first = memoryFrames!!.first()
                    first.width to first.height
                }
                FrameStorageMode.DISK -> {
                    val probe = android.graphics.BitmapFactory.decodeFile(
                        (frames.first() as FrameHandle.Disk).file.absolutePath
                    ) ?: error("Failed to decode extracted frame.")
                    val w = probe.width
                    val h = probe.height
                    probe.recycle()
                    w to h
                }
            }

            AppLogger.i(
                "Extracted ${frames.size} frames at ${fps}fps " +
                    "(target=${targetFrameRate ?: "auto"}, storage=$storageMode), " +
                    "oriented ${orientedWidth}x$orientedHeight → ${outW}x$outH"
            )

            // Successful memory extract: clear tracker so finally does not recycle.
            memoryFrames?.clear()

            return ExtractionResult(
                frames = frames,
                width = outW,
                height = outH,
                frameRate = fps,
                storageMode = storageMode,
            )
        } catch (oom: OutOfMemoryError) {
            recycleBitmaps(memoryFrames)
            throw oom
        } catch (t: Throwable) {
            recycleBitmaps(memoryFrames)
            throw t
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** @deprecated Prefer [extract]; kept name clarity for disk-only call sites. */
    fun extractToJpegSequence(
        inputFile: File,
        outputDir: File,
        maxDurationMs: Long,
        targetFrameRate: Float? = null,
        onProgress: (Float) -> Unit = {},
    ): ExtractionResult = extract(
        inputFile = inputFile,
        outputDir = outputDir,
        maxDurationMs = maxDurationMs,
        storageMode = FrameStorageMode.DISK,
        targetFrameRate = targetFrameRate,
        onProgress = onProgress,
    )

    private fun writeJpeg(bitmap: Bitmap, file: File) {
        file.outputStream().use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                error("Failed to compress frame to JPEG: ${file.name}")
            }
        }
    }

    /**
     * Takes ownership of pixels from a [MediaMetadataRetriever] frame.
     * Hardware configs are promoted to software ARGB so later [Bitmap.getPixels] works.
     */
    private fun copyRetrieverBitmap(source: Bitmap): Bitmap {
        val destConfig = when (val config = source.config) {
            Bitmap.Config.HARDWARE, null -> Bitmap.Config.ARGB_8888
            else -> config
        }
        return source.copy(destConfig, false)
            ?: throw OutOfMemoryError(
                "Failed to copy retrieved frame ${source.width}x${source.height}."
            )
    }

    private fun recycleBitmaps(bitmaps: MutableList<Bitmap>?) {
        bitmaps?.forEach { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        bitmaps?.clear()
    }
}
