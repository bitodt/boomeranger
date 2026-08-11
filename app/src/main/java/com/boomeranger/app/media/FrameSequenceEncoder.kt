package com.boomeranger.app.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.boomeranger.app.util.AppLogger
import com.boomeranger.app.util.BitrateCalculator
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes an ordered list of frames (in-memory bitmaps or JPEG files) into an H.264/MP4
 * video track (no audio).
 */
class FrameSequenceEncoder {

    fun encode(
        frames: List<FrameHandle>,
        outputFile: File,
        width: Int,
        height: Int,
        frameRate: Float,
        sourceBitrate: Int?,
        sourceWidth: Int,
        sourceHeight: Int,
        speedMultiplier: Int = 1,
        onProgress: (Float) -> Unit = {},
    ) {
        require(frames.size >= 2) { "At least 2 frames required." }
        require(width > 0 && height > 0) { "Invalid encode size." }

        val evenWidth = width and -2
        val evenHeight = height and -2

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val fps = frameRate.coerceIn(12f, 60f)
        val speed = speedMultiplier.coerceIn(1, 4)
        // Faster export = same frames with shorter duration / higher playback fps.
        val playbackFps = (fps * speed).coerceIn(12f, 240f)
        val bitrate = BitrateCalculator.calculateBitsPerSecond(
            width = evenWidth,
            height = evenHeight,
            frameRate = playbackFps.coerceAtMost(60f),
            sourceBitrate = sourceBitrate,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
        )

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            evenWidth,
            evenHeight
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setFloat(MediaFormat.KEY_FRAME_RATE, playbackFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var muxerStarted = false

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            var inputIndex = 0
            var outputDone = false
            var inputDone = false
            val frameDurationUs = (1_000_000.0 / playbackFps).toLong().coerceAtLeast(1L)

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = encoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        if (inputIndex >= frames.size) {
                            encoder.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                frameDurationUs * frames.size,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val opened = frames[inputIndex].openBitmap(evenWidth, evenHeight)
                            try {
                                val image = encoder.getInputImage(inIndex)
                                    ?: error("Encoder did not provide an input Image")
                                YuvConverter.fillImageFromBitmap(image, opened.bitmap)
                                // Standard I420/NV12 payload size; plane capacities may include padding.
                                val payloadSize = evenWidth * evenHeight * 3 / 2
                                val pts = frameDurationUs * inputIndex
                                encoder.queueInputBuffer(inIndex, 0, payloadSize, pts, 0)
                                inputIndex++
                                onProgress(inputIndex.toFloat() / frames.size * 0.7f)
                            } finally {
                                opened.recycleIfOwned()
                            }
                        }
                    }
                }

                when (val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) error("Format changed twice.")
                        trackIndex = muxer!!.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    else -> if (outIndex >= 0) {
                        val encoded: ByteBuffer = encoder.getOutputBuffer(outIndex)
                            ?: error("Null encoder output buffer")
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size != 0) {
                            if (!muxerStarted) error("Muxer has not started.")
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer!!.writeSampleData(trackIndex, encoded, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                        onProgress(
                            0.7f + (inputIndex.toFloat() / frames.size).coerceAtMost(1f) * 0.3f
                        )
                    }
                }
            }

            AppLogger.i(
                "Encoded ${frames.size} frames to ${outputFile.name} " +
                    "@ ${playbackFps}fps (${speed}x), ${bitrate}bps"
            )
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
        }
    }
}
