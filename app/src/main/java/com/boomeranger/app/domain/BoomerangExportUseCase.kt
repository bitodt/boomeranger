package com.boomeranger.app.domain

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.media3.common.util.UnstableApi
import com.boomeranger.app.data.ExportRepository
import com.boomeranger.app.media.ExportProgressListener
import com.boomeranger.app.media.ForwardClipPreparer
import com.boomeranger.app.media.GifSequenceEncoder
import com.boomeranger.app.media.Media3TransformHelper
import com.boomeranger.app.media.ReverseVideoBuilder
import com.boomeranger.app.media.VideoConcatenationService
import com.boomeranger.app.model.ExportFormat
import com.boomeranger.app.model.ExportResult
import com.boomeranger.app.model.ExportSettings
import com.boomeranger.app.model.ExportStage
import com.boomeranger.app.model.VideoMetadata
import com.boomeranger.app.util.AppLogger
import com.boomeranger.app.util.AvailableMemoryReader
import com.boomeranger.app.util.ClipWindowResolver
import com.boomeranger.app.util.GifPlaybackTiming
import com.boomeranger.app.util.OutputSizeResolver
import com.boomeranger.app.util.UriFileCopier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Orchestrates the full boomerang export pipeline on a background dispatcher.
 * Supports MP4 (Media3 concat) and GIF (frame-sequence encoder) at 30 or 60 fps.
 */
@UnstableApi
class BoomerangExportUseCase(
    context: Context,
    private val uriFileCopier: UriFileCopier = UriFileCopier(context),
    private val transformHelper: Media3TransformHelper = Media3TransformHelper(context),
    private val forwardClipPreparer: ForwardClipPreparer = ForwardClipPreparer(transformHelper),
    private val reverseVideoBuilder: ReverseVideoBuilder = ReverseVideoBuilder(
        memoryReader = AvailableMemoryReader(context),
    ),
    private val concatenationService: VideoConcatenationService =
        VideoConcatenationService(transformHelper),
    private val gifEncoder: GifSequenceEncoder = GifSequenceEncoder(),
    private val exportRepository: ExportRepository = ExportRepository(context),
) {
    private val appContext = context.applicationContext

    suspend fun export(
        metadata: VideoMetadata,
        settings: ExportSettings,
        trimStartMs: Long = 0L,
        progressListener: ExportProgressListener,
    ): ExportResult = withContext(Dispatchers.Default) {
        val workRoot = File(
            appContext.cacheDir,
            "export_${System.currentTimeMillis()}"
        ).also { it.mkdirs() }
        val exportJob = coroutineContext.job
        var outputFile: File? = null

        fun checkActive() = exportJob.ensureActive()

        try {
            progressListener.onProgress(ExportStage.READING_METADATA, 0.02f)
            ensureActive()

            if (metadata.durationMs <= 0L) {
                error("Selected video has an invalid duration.")
            }

            val localInput = uriFileCopier.copyToCache(metadata.uri, metadata.displayName)
            val outputSize = OutputSizeResolver.resolve(
                sourceWidth = metadata.orientedWidth,
                sourceHeight = metadata.orientedHeight,
                option = settings.resolution,
                format = settings.format,
            )
            val targetFps = when (settings.format) {
                ExportFormat.GIF -> 30f
                ExportFormat.MP4 -> settings.frameRate.fps.toFloat()
            }
            val speedMultiplier = settings.speed.multiplier
            // GIF always silent; MP4 honors mute toggle.
            val removeAudio = settings.format == ExportFormat.GIF || settings.muteAudio

            AppLogger.i(
                "Export start: file=${metadata.displayName}, " +
                    "format=${settings.format}, fps=$targetFps, speed=${speedMultiplier}x, " +
                    "trimStartMs=$trimStartMs, " +
                    "src=${metadata.orientedWidth}x${metadata.orientedHeight}, " +
                    "out=${outputSize.width}x${outputSize.height}, " +
                    "repeats=${settings.repeatCount.value}, mute=$removeAudio"
            )

            progressListener.onProgress(ExportStage.PREPARING_FORWARD, 0.08f)
            ensureActive()
            val preparedForward = File(workRoot, "forward_prepared.mp4")
            forwardClipPreparer.prepare(
                inputFile = localInput,
                metadata = metadata,
                outputSize = outputSize,
                removeAudio = removeAudio,
                outputFile = preparedForward,
                trimStartMs = trimStartMs,
                onProgress = { p ->
                    progressListener.onProgress(
                        ExportStage.PREPARING_FORWARD,
                        0.08f + p * 0.18f
                    )
                },
            )

            progressListener.onProgress(ExportStage.GENERATING_REVERSE, 0.28f)
            ensureActive()

            val finalFile = File(
                appContext.getExternalFilesDir(null) ?: appContext.filesDir,
                "boomerangs/${outputFileName(settings.format)}"
            )
            outputFile = finalFile

            val durationMs: Long
            val outWidth: Int
            val outHeight: Int

            val clipWindow = ClipWindowResolver.resolve(
                sourceDurationMs = metadata.durationMs,
                requestedStartMs = trimStartMs,
            )
            val clipDurationMs = clipWindow.durationMs

            when (settings.format) {
                ExportFormat.MP4 -> {
                    val segments = reverseVideoBuilder.buildSegments(
                        preparedForwardFile = preparedForward,
                        workDir = File(workRoot, "segments"),
                        sourceMetadata = metadata,
                        targetFrameRate = targetFps,
                        speedMultiplier = speedMultiplier,
                        encodeForwardFromFrames = true,
                        outputWidth = outputSize.width,
                        outputHeight = outputSize.height,
                        clipDurationMs = clipDurationMs,
                        onProgress = { p ->
                            checkActive()
                            progressListener.onProgress(
                                ExportStage.GENERATING_REVERSE,
                                0.28f + p * 0.32f
                            )
                        },
                    )
                    outWidth = segments.width
                    outHeight = segments.height

                    try {
                        progressListener.onProgress(ExportStage.CONCATENATING, 0.62f)
                        ensureActive()
                        val forwardForConcat = segments.forwardFile
                            ?: error("Forward re-encode missing for MP4 export.")
                        concatenationService.concatenateBoomerang(
                            forwardFile = forwardForConcat,
                            reverseFile = segments.reverseFile,
                            repeatCount = settings.repeatCount.value,
                            outputFile = finalFile,
                            removeAudio = removeAudio,
                            onProgress = { p ->
                                progressListener.onProgress(
                                    ExportStage.EXPORTING,
                                    0.62f + p * 0.28f
                                )
                            },
                        )
                        durationMs = readDurationMs(finalFile)
                    } finally {
                        // Frames are no longer needed after F/R encodes finish.
                        segments.frameBundle.release()
                    }
                }

                ExportFormat.GIF -> {
                    val framesDir = File(workRoot, "gif_frames").also { it.mkdirs() }
                    val bundle = reverseVideoBuilder.extractFrames(
                        preparedForwardFile = preparedForward,
                        framesDir = framesDir,
                        targetFrameRate = targetFps,
                        outputWidth = outputSize.width,
                        outputHeight = outputSize.height,
                        clipDurationMs = clipDurationMs,
                        onProgress = { p ->
                            checkActive()
                            progressListener.onProgress(
                                ExportStage.GENERATING_REVERSE,
                                0.28f + p * 0.30f
                            )
                        },
                    )
                    outWidth = bundle.width
                    outHeight = bundle.height

                    try {
                        progressListener.onProgress(ExportStage.ENCODING_GIF, 0.60f)
                        ensureActive()
                        val cycleFrames = reverseVideoBuilder.buildBoomerangFrameCycle(
                            forwardFrames = bundle.frames,
                            repeatCount = settings.repeatCount.value,
                        )
                        gifEncoder.encode(
                            frames = cycleFrames,
                            outputFile = finalFile,
                            frameRate = bundle.frameRate,
                            width = bundle.width,
                            height = bundle.height,
                            speedMultiplier = speedMultiplier,
                            onProgress = { p ->
                                checkActive()
                                progressListener.onProgress(
                                    ExportStage.ENCODING_GIF,
                                    0.60f + p * 0.30f
                                )
                            },
                        )
                        durationMs = GifPlaybackTiming.plan(
                            sourceFrameCount = cycleFrames.size,
                            sourceFps = bundle.frameRate,
                            speedMultiplier = speedMultiplier,
                        ).durationMs()
                    } finally {
                        bundle.release()
                    }
                }
            }

            progressListener.onProgress(ExportStage.SAVING, 0.92f)
            ensureActive()
            val provisional = ExportResult(
                outputFile = finalFile,
                mediaStoreUri = null,
                width = outWidth,
                height = outHeight,
                durationMs = durationMs,
                format = settings.format,
            )
            val galleryUri = runCatching { exportRepository.saveToGallery(provisional) }
                .onFailure { AppLogger.w("Gallery save failed; file still available locally", it) }
                .getOrNull()

            val result = provisional.copy(mediaStoreUri = galleryUri)
            progressListener.onProgress(ExportStage.COMPLETED, 1f)
            result
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            AppLogger.i("Boomerang export cancelled")
            outputFile?.delete()
            throw cancelled
        } catch (t: Throwable) {
            AppLogger.e("Boomerang export failed", t)
            progressListener.onProgress(ExportStage.FAILED, 0f)
            throw t
        } finally {
            workRoot.deleteRecursivelySafely()
        }
    }

    private fun outputFileName(format: ExportFormat): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "boomerang_$stamp.${format.fileExtension}"
    }

    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } catch (t: Throwable) {
            AppLogger.w("Could not read exported duration", t)
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun File.deleteRecursivelySafely() {
        runCatching { deleteRecursively() }
            .onFailure { AppLogger.w("Failed to clean work dir $absolutePath", it) }
    }
}
