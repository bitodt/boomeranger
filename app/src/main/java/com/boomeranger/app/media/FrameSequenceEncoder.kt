package com.boomeranger.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.boomeranger.app.util.AppLogger
import com.boomeranger.app.util.BitrateCalculator
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes an ordered list of JPEG frame files into an H.264/MP4 video track (no audio).
 */
class FrameSequenceEncoder {

    fun encode(
        frameFiles: List<File>,
        outputFile: File,
        width: Int,
        height: Int,
        frameRate: Float,
        sourceBitrate: Int?,
        sourceWidth: Int,
        sourceHeight: Int,
        onProgress: (Float) -> Unit = {},
    ) {
        require(frameFiles.size >= 2) { "At least 2 frames required." }
        require(width > 0 && height > 0) { "Invalid encode size." }

        val evenWidth = width and -2
        val evenHeight = height and -2

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val fps = frameRate.coerceIn(12f, 60f)
        val bitrate = BitrateCalculator.calculateBitsPerSecond(
            width = evenWidth,
            height = evenHeight,
            frameRate = fps,
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
            setFloat(MediaFormat.KEY_FRAME_RATE, fps)
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
            val frameDurationUs = (1_000_000.0 / fps).toLong().coerceAtLeast(1L)

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = encoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        if (inputIndex >= frameFiles.size) {
                            encoder.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                frameDurationUs * frameFiles.size,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val bitmap = decodeScaledFrame(
                                frameFiles[inputIndex],
                                evenWidth,
                                evenHeight
                            )
                            try {
                                val image = encoder.getInputImage(inIndex)
                                    ?: error("Encoder did not provide an input Image")
                                YuvConverter.fillImageFromBitmap(image, bitmap)
                                // Standard I420/NV12 payload size; plane capacities may include padding.
                                val payloadSize = evenWidth * evenHeight * 3 / 2
                                val pts = frameDurationUs * inputIndex
                                encoder.queueInputBuffer(inIndex, 0, payloadSize, pts, 0)
                                inputIndex++
                                onProgress(inputIndex.toFloat() / frameFiles.size * 0.7f)
                            } finally {
                                bitmap.recycle()
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
                            0.7f + (inputIndex.toFloat() / frameFiles.size).coerceAtMost(1f) * 0.3f
                        )
                    }
                }
            }

            AppLogger.i("Encoded ${frameFiles.size} frames to ${outputFile.name} @ ${bitrate}bps")
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
        }
    }

    private fun decodeScaledFrame(file: File, width: Int, height: Int): Bitmap {
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
            ?: error("Failed to decode ${file.name}")
        if (decoded.width == width && decoded.height == height) {
            return decoded
        }
        val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}
