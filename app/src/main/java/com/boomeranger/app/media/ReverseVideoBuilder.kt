package com.boomeranger.app.media

import com.boomeranger.app.model.VideoMetadata
import com.boomeranger.app.util.AppLogger
import com.boomeranger.app.util.AvailableMemoryReader
import com.boomeranger.app.util.FrameStoragePolicy
import java.io.File

/**
 * Builds forward/reverse MP4 segments (and shared frame lists for GIF) from a prepared clip.
 *
 * Media3 has no first-class "reverse frames" edit, so reverse generation is explicit:
 * extract oriented frames at the chosen fps → encode last-to-first as H.264/MP4.
 *
 * Frame storage is hybrid: in-memory ARGB when within the soft budget and enough RAM is
 * available; otherwise JPEG-on-disk. Mid-extract OOM falls back to disk automatically.
 */
class ReverseVideoBuilder(
    private val frameExtractor: BitmapFrameExtractor = BitmapFrameExtractor(),
    private val frameEncoder: FrameSequenceEncoder = FrameSequenceEncoder(),
    private val memoryReader: AvailableMemoryReader? = null,
    private val memorySnapshotOverride: (() -> FrameStoragePolicy.MemorySnapshot)? = null,
) {

    data class FrameBundle(
        val frames: List<FrameHandle>,
        val width: Int,
        val height: Int,
        val frameRate: Float,
        val storageMode: FrameStorageMode,
    ) {
        val frameCount: Int get() = frames.size

        /** Recycles in-memory bitmaps. Safe to call more than once. No-op for disk mode. */
        fun release() {
            frames.forEach { handle ->
                if (handle is FrameHandle.Memory && !handle.bitmap.isRecycled) {
                    handle.bitmap.recycle()
                }
            }
        }
    }

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
        outputWidth: Int,
        outputHeight: Int,
        clipDurationMs: Long = VideoMetadata.MAX_INPUT_DURATION_MS,
        onProgress: (Float) -> Unit = {},
    ): FrameBundle {
        framesDir.mkdirs()
        val decision = decideStorage(
            width = outputWidth,
            height = outputHeight,
            targetFrameRate = targetFrameRate,
            clipDurationMs = clipDurationMs,
        )
        AppLogger.i(
            "Frame storage decision: ${decision.mode} (${decision.reason}); " +
                "estStorage=${decision.estimatedStorageBytes / (1024 * 1024)}MB"
        )

        val extraction = try {
            frameExtractor.extract(
                inputFile = preparedForwardFile,
                outputDir = framesDir,
                maxDurationMs = clipDurationMs,
                storageMode = decision.mode,
                targetFrameRate = targetFrameRate,
                onProgress = onProgress,
            )
        } catch (oom: OutOfMemoryError) {
            if (decision.mode != FrameStorageMode.MEMORY) throw oom
            AppLogger.w(
                "In-memory frame extract ran out of memory; falling back to disk JPEG path.",
                oom,
            )
            System.gc()
            frameExtractor.extract(
                inputFile = preparedForwardFile,
                outputDir = framesDir,
                maxDurationMs = clipDurationMs,
                storageMode = FrameStorageMode.DISK,
                targetFrameRate = targetFrameRate,
                onProgress = onProgress,
            )
        }

        return FrameBundle(
            frames = extraction.frames,
            width = extraction.width,
            height = extraction.height,
            frameRate = extraction.frameRate,
            storageMode = extraction.storageMode,
        )
    }

    fun buildSegments(
        preparedForwardFile: File,
        workDir: File,
        sourceMetadata: VideoMetadata,
        targetFrameRate: Float,
        speedMultiplier: Int,
        encodeForwardFromFrames: Boolean,
        outputWidth: Int,
        outputHeight: Int,
        clipDurationMs: Long = VideoMetadata.MAX_INPUT_DURATION_MS,
        onProgress: (Float) -> Unit = {},
    ): ReverseResult {
        workDir.mkdirs()
        val framesDir = File(workDir, "frames").also { it.mkdirs() }

        AppLogger.i(
            "Building segments from ${preparedForwardFile.name} " +
                "@ ${targetFrameRate}fps, ${speedMultiplier}x"
        )

        val bundle = extractFrames(
            preparedForwardFile = preparedForwardFile,
            framesDir = framesDir,
            targetFrameRate = targetFrameRate,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            clipDurationMs = clipDurationMs,
            onProgress = { p -> onProgress(p * 0.45f) },
        )

        try {
            var progressBase = 0.45f
            val forwardEncoded = if (encodeForwardFromFrames) {
                val out = File(workDir, "forward_reencoded.mp4")
                frameEncoder.encode(
                    frames = bundle.frames,
                    outputFile = out,
                    width = bundle.width,
                    height = bundle.height,
                    frameRate = bundle.frameRate,
                    sourceBitrate = sourceMetadata.bitrate,
                    sourceWidth = sourceMetadata.orientedWidth,
                    sourceHeight = sourceMetadata.orientedHeight,
                    speedMultiplier = speedMultiplier,
                    onProgress = { p -> onProgress(progressBase + p * 0.25f) },
                )
                progressBase = 0.70f
                out
            } else {
                null
            }

            val reverseFile = File(workDir, "reverse.mp4")
            frameEncoder.encode(
                frames = bundle.frames.asReversed(),
                outputFile = reverseFile,
                width = bundle.width,
                height = bundle.height,
                frameRate = bundle.frameRate,
                sourceBitrate = sourceMetadata.bitrate,
                sourceWidth = sourceMetadata.orientedWidth,
                sourceHeight = sourceMetadata.orientedHeight,
                speedMultiplier = speedMultiplier,
                onProgress = { p -> onProgress(progressBase + p * (1f - progressBase)) },
            )

            return ReverseResult(
                reverseFile = reverseFile,
                forwardFile = forwardEncoded,
                frameBundle = bundle,
                frameCount = bundle.frameCount,
            )
        } catch (t: Throwable) {
            bundle.release()
            throw t
        }
    }

    fun buildBoomerangFrameCycle(
        forwardFrames: List<FrameHandle>,
        repeatCount: Int,
    ): List<FrameHandle> {
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

    private fun decideStorage(
        width: Int,
        height: Int,
        targetFrameRate: Float,
        clipDurationMs: Long,
    ): FrameStoragePolicy.Decision {
        val expectedFrames = FrameStoragePolicy.expectedFrameCount(clipDurationMs, targetFrameRate)
        val memory = memorySnapshotOverride?.invoke()
            ?: memoryReader?.snapshot()
            ?: FrameStoragePolicy.MemorySnapshot(
                // Without a reader (tests / unexpected wiring), prefer the safe disk path.
                availMemBytes = 0L,
                totalMemBytes = 0L,
                lowMemory = true,
                memoryClassBytes = 0L,
                heapMaxBytes = 0L,
                heapUsedBytes = 0L,
            )
        return FrameStoragePolicy.decide(
            width = width,
            height = height,
            expectedFrameCount = expectedFrames,
            targetFps = targetFrameRate,
            memory = memory,
        )
    }
}
