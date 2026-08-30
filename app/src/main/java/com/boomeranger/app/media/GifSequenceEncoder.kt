package com.boomeranger.app.media

import com.boomeranger.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.OutputStream
import kotlin.math.max

/**
 * Encodes a frame sequence (in-memory bitmaps or JPEG files) into a looping GIF89a.
 *
 * Why this is CPU work, not GPU:
 * - GIF has no hardware encoder on Android.
 * - LZW is sequential (each code depends on the previous).
 * - Palette mapping is data-parallel, but a 15-bit CPU lookup table beats
 *   uploading each frame to the GPU and reading indices back.
 * Media3 already uses the GPU for the earlier trim/scale step.
 *
 * Speed tactics:
 * - One global 256-color palette + lookup table for the whole clip
 * - Encode each unique [FrameHandle] once (boomerang repeats 2–4 cycles)
 * - Parallel unique-frame encode on [Dispatchers.Default]
 */
class GifSequenceEncoder {

    suspend fun encode(
        frames: List<FrameHandle>,
        outputFile: File,
        frameRate: Float,
        width: Int,
        height: Int,
        speedMultiplier: Int = 1,
        onProgress: (Float) -> Unit = {},
    ) {
        require(frames.size >= 2) { "GIF needs at least 2 frames." }
        require(width >= 2 && height >= 2) { "GIF size must be at least 2x2." }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val fps = frameRate.coerceIn(12f, 60f)
        val speed = speedMultiplier.coerceIn(1, 4)
        val playbackFps = (fps * speed).coerceIn(12f, 240f)
        // GIF delay unit is 1/100s.
        val delayCs = (100f / playbackFps).toInt().coerceIn(1, 20)

        val uniqueFrames = frames.distinct()
        val startedAt = System.nanoTime()

        val palette = buildGlobalPalette(uniqueFrames, width, height)
        val lookupTable = GifColorQuantizer.buildLookupTable(palette)
        val minCodeSize = 8 // 256-color global table
        onProgress(0.08f)

        val encodedByFrame = encodeUniqueFrames(
            uniqueFrames = uniqueFrames,
            width = width,
            height = height,
            lookupTable = lookupTable,
            minCodeSize = minCodeSize,
            onProgress = { p -> onProgress(0.08f + p * 0.84f) },
        )

        outputFile.outputStream().use { out ->
            writeString(out, "GIF89a")
            writeLogicalScreen(out, width, height)
            writeGlobalColorTable(out, palette)
            writeNetscapeLoop(out)

            for (frame in frames) {
                val lzw = encodedByFrame[frame]
                    ?: error("Missing encoded GIF data for a cycle frame.")
                writeGraphicsControl(out, delayCs)
                writeImageDescriptor(out, width, height)
                out.write(minCodeSize)
                out.write(lzw)
            }

            out.write(0x3B) // trailer
            out.flush()
        }

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
        AppLogger.i(
            "Encoded GIF ${outputFile.name}: ${frames.size} cycle frames " +
                "(${uniqueFrames.size} unique) @ ${playbackFps}fps (${speed}x), " +
                "${outputFile.length()} bytes in ${elapsedMs}ms"
        )
        onProgress(1f)
    }

    private fun buildGlobalPalette(
        uniqueFrames: List<FrameHandle>,
        width: Int,
        height: Int,
    ): IntArray {
        val sources = pickPaletteSources(uniqueFrames)
        val samples = ArrayList<Int>(sources.size * 8_192)
        val targetSamples = 180_000
        val pixelsPerFrame = width * height
        val stride = max(1, (pixelsPerFrame * sources.size) / targetSamples)
        val scratch = IntArray(pixelsPerFrame)

        for (frame in sources) {
            val opened = frame.openBitmap(width, height)
            try {
                opened.bitmap.getPixels(scratch, 0, width, 0, 0, width, height)
                var i = 0
                while (i < scratch.size) {
                    samples += scratch[i]
                    i += stride
                }
            } finally {
                opened.recycleIfOwned()
            }
        }
        if (samples.size < 2) {
            samples += 0
            samples += 0x00FFFFFF
        }
        return GifColorQuantizer.buildPalette(samples.toIntArray())
    }

    private fun pickPaletteSources(uniqueFrames: List<FrameHandle>): List<FrameHandle> {
        if (uniqueFrames.size <= 6) return uniqueFrames
        val last = uniqueFrames.lastIndex
        val indexes = listOf(0, last / 4, last / 2, (last * 3) / 4, last).distinct()
        return indexes.map { uniqueFrames[it] }
    }

    private suspend fun encodeUniqueFrames(
        uniqueFrames: List<FrameHandle>,
        width: Int,
        height: Int,
        lookupTable: ByteArray,
        minCodeSize: Int,
        onProgress: (Float) -> Unit,
    ): Map<FrameHandle, ByteArray> = coroutineScope {
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val progressLock = Mutex()
        uniqueFrames.map { frame ->
            async(Dispatchers.Default) {
                ensureActive()
                val lzw = encodeOneFrame(frame, width, height, lookupTable, minCodeSize)
                val done = completed.incrementAndGet()
                progressLock.withLock {
                    onProgress(done.toFloat() / uniqueFrames.size)
                }
                frame to lzw
            }
        }.awaitAll().toMap()
    }

    private fun encodeOneFrame(
        frame: FrameHandle,
        width: Int,
        height: Int,
        lookupTable: ByteArray,
        minCodeSize: Int,
    ): ByteArray {
        val opened = frame.openBitmap(width, height)
        try {
            val pixels = IntArray(width * height)
            opened.bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val indexed = GifColorQuantizer.indexPixels(pixels, lookupTable)
            return GifLzwEncoder.encode(indexed, minCodeSize)
        } finally {
            opened.recycleIfOwned()
        }
    }

    private fun writeLogicalScreen(out: OutputStream, width: Int, height: Int) {
        writeShort(out, width)
        writeShort(out, height)
        // Global color table, 8-bit color resolution, 256 entries.
        out.write(0xF7)
        out.write(0x00) // background color index
        out.write(0x00) // pixel aspect ratio
    }

    private fun writeGlobalColorTable(out: OutputStream, palette: IntArray) {
        for (i in 0 until GifColorQuantizer.MAX_COLORS) {
            if (i < palette.size) {
                val c = palette[i]
                out.write(GifColorQuantizer.red(c))
                out.write(GifColorQuantizer.green(c))
                out.write(GifColorQuantizer.blue(c))
            } else {
                out.write(0)
                out.write(0)
                out.write(0)
            }
        }
    }

    private fun writeNetscapeLoop(out: OutputStream) {
        out.write(0x21) // extension
        out.write(0xFF) // application extension
        out.write(0x0B)
        writeString(out, "NETSCAPE2.0")
        out.write(0x03)
        out.write(0x01)
        writeShort(out, 0) // loop forever
        out.write(0x00)
    }

    private fun writeGraphicsControl(out: OutputStream, delayCs: Int) {
        out.write(0x21)
        out.write(0xF9)
        out.write(0x04)
        out.write(0x04) // disposal = do not dispose, no transparent color
        writeShort(out, delayCs)
        out.write(0x00) // transparent color index
        out.write(0x00)
    }

    private fun writeImageDescriptor(out: OutputStream, width: Int, height: Int) {
        out.write(0x2C)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, width)
        writeShort(out, height)
        out.write(0x00) // no local color table
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun writeString(out: OutputStream, value: String) {
        for (ch in value) out.write(ch.code)
    }
}
