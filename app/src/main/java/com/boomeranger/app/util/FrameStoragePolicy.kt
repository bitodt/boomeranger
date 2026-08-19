package com.boomeranger.app.util

import com.boomeranger.app.media.FrameStorageMode
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decides whether extracted frames should stay in RAM or spill to JPEG files.
 *
 * Hybrid rules:
 * 1. Soft budget — prefer disk above ~720p or above 30 fps.
 * 2. App heap — [availMemBytes] is *system* RAM and is not the process heap.
 *    In-memory storage must fit in remaining Java/ART heap or extraction GC-thrashes
 *    and appears to hang.
 * 3. Runtime memory — honor [lowMemory]; still require system RAM headroom.
 * 4. Low-end heap — very small [memoryClassBytes] always uses disk.
 */
object FrameStoragePolicy {

    /** Soft cap: HD landscape pixel count (1280×720). */
    const val MAX_MEMORY_FRAME_PIXELS: Int = 1280 * 720

    /** Soft cap: in-memory path only at 30 fps (60 fps roughly doubles RAM). */
    const val MAX_MEMORY_FPS: Float = 30f

    /** Keep this much system RAM free for UI / encoder / OS. */
    const val SYSTEM_RESERVE_BYTES: Long = 64L * 1024L * 1024L

    /** Absolute ARGB storage cap even when availMem looks high. */
    const val MAX_MEMORY_STORAGE_BYTES: Long = 384L * 1024L * 1024L

    /**
     * Never spend more than this fraction of the process heap on the ARGB frame list.
     * HD@30 for 3s is ~316MB; a typical largeHeap is 256–512MB, so that clip must
     * stay on disk.
     */
    const val MAX_HEAP_STORAGE_FRACTION: Double = 0.40

    /** Devices below this memory class skip the RAM path. */
    const val MIN_MEMORY_CLASS_BYTES: Long = 192L * 1024L * 1024L

    /** Extra decoded / YUV working set beyond the frame list. */
    const val ENCODER_OVERHEAD_FRAMES: Int = 3

    /** Inflate estimate slightly so we do not ride the OOM edge. */
    const val SAFETY_MULTIPLIER: Double = 1.25

    data class MemorySnapshot(
        val availMemBytes: Long,
        val totalMemBytes: Long,
        val lowMemory: Boolean,
        val memoryClassBytes: Long,
        /** [Runtime.maxMemory] — process heap cap, not device RAM. */
        val heapMaxBytes: Long = memoryClassBytes,
        /** Currently used Java/ART heap (`totalMemory - freeMemory`). */
        val heapUsedBytes: Long = 0L,
    ) {
        val heapRemainingBytes: Long
            get() = (heapMaxBytes - heapUsedBytes).coerceAtLeast(0L)
    }

    data class Decision(
        val mode: FrameStorageMode,
        val reason: String,
        val estimatedStorageBytes: Long,
        val estimatedNeedBytes: Long,
    )

    fun expectedFrameCount(durationMs: Long, targetFps: Float): Int {
        val fps = targetFps.coerceIn(12f, 60f)
        val clipMs = durationMs.coerceAtLeast(1L)
        return max(2, ((clipMs / 1000.0) * fps).roundToInt())
    }

    fun estimateArgbStorageBytes(width: Int, height: Int, frameCount: Int): Long {
        val w = width.coerceAtLeast(1).toLong()
        val h = height.coerceAtLeast(1).toLong()
        val frames = frameCount.coerceAtLeast(0).toLong()
        return w * h * 4L * frames
    }

    fun decide(
        width: Int,
        height: Int,
        expectedFrameCount: Int,
        targetFps: Float,
        memory: MemorySnapshot,
    ): Decision {
        val pixels = width.toLong().coerceAtLeast(1) * height.toLong().coerceAtLeast(1)
        val frames = expectedFrameCount.coerceAtLeast(2)
        val storageBytes = estimateArgbStorageBytes(width, height, frames)
        val bytesPerFrame = pixels * 4L
        val yuvScratch = pixels * 3L / 2L
        val overhead = bytesPerFrame * ENCODER_OVERHEAD_FRAMES + yuvScratch
        val needBytes =
            ((storageBytes + overhead) * SAFETY_MULTIPLIER).toLong() + SYSTEM_RESERVE_BYTES

        if (memory.lowMemory) {
            return Decision(
                mode = FrameStorageMode.DISK,
                reason = "system lowMemory flag set",
                estimatedStorageBytes = storageBytes,
                estimatedNeedBytes = needBytes,
            )
        }

        if (memory.memoryClassBytes < MIN_MEMORY_CLASS_BYTES) {
            return Decision(
                mode = FrameStorageMode.DISK,
                reason = "memoryClass ${memory.memoryClassBytes / (1024 * 1024)}MB below minimum",
                estimatedStorageBytes = storageBytes,
                estimatedNeedBytes = needBytes,
            )
        }

        if (pixels > MAX_MEMORY_FRAME_PIXELS) {
            return Decision(
                mode = FrameStorageMode.DISK,
                reason = "frame ${width}x$height exceeds soft pixel budget",
                estimatedStorageBytes = storageBytes,
                estimatedNeedBytes = needBytes,
            )
        }

        if (targetFps > MAX_MEMORY_FPS + 0.01f) {
            return Decision(
                mode = FrameStorageMode.DISK,
                reason = "fps $targetFps exceeds soft ${MAX_MEMORY_FPS.toInt()}fps budget",
                estimatedStorageBytes = storageBytes,
                estimatedNeedBytes = needBytes,
            )
        }

        if (storageBytes > MAX_MEMORY_STORAGE_BYTES) {
            return Decision(
                mode = FrameStorageMode.DISK,
                reason = "estimated ARGB storage ${storageBytes / (1024 * 1024)}MB exceeds cap",
                estimatedStorageBytes = storageBytes,
                estimatedNeedBytes = needBytes,
            )
        }

        val heapStorageCap =
            (memory.heapMaxBytes.toDouble() * MAX_HEAP_STORAGE_FRACTION).toLong()
        if (memory.heapMaxBytes > 0L && storageBytes > heapStorageCap) {
            return Decision(
                mode = FrameStorageMode.DISK,
                reason = "estimated ARGB storage ${storageBytes / (1024 * 1024)}MB " +
                    "exceeds ${ (MAX_HEAP_STORAGE_FRACTION * 100).toInt() }% of " +
                    "heapMax ${memory.heapMaxBytes / (1024 * 1024)}MB",
                estimatedStorageBytes = storageBytes,
                estimatedNeedBytes = needBytes,
            )
        }

        if (needBytes > memory.memoryClassBytes && memory.memoryClassBytes > 0L) {
            return Decision(
                mode = FrameStorageMode.DISK,
                reason = "need ${needBytes / (1024 * 1024)}MB exceeds memoryClass " +
                    "${memory.memoryClassBytes / (1024 * 1024)}MB",
                estimatedStorageBytes = storageBytes,
                estimatedNeedBytes = needBytes,
            )
        }

        if (memory.heapRemainingBytes < needBytes) {
            return Decision(
                mode = FrameStorageMode.DISK,
                reason = "heap remaining ${memory.heapRemainingBytes / (1024 * 1024)}MB " +
                    "< need ${needBytes / (1024 * 1024)}MB",
                estimatedStorageBytes = storageBytes,
                estimatedNeedBytes = needBytes,
            )
        }

        if (memory.availMemBytes < needBytes) {
            return Decision(
                mode = FrameStorageMode.DISK,
                reason = "availMem ${memory.availMemBytes / (1024 * 1024)}MB " +
                    "< need ${needBytes / (1024 * 1024)}MB",
                estimatedStorageBytes = storageBytes,
                estimatedNeedBytes = needBytes,
            )
        }

        return Decision(
            mode = FrameStorageMode.MEMORY,
            reason = "within soft budget and heap remaining " +
                "${memory.heapRemainingBytes / (1024 * 1024)}MB >= need ${needBytes / (1024 * 1024)}MB",
            estimatedStorageBytes = storageBytes,
            estimatedNeedBytes = needBytes,
        )
    }
}
