package com.boomeranger.app.domain

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.media3.common.util.UnstableApi
import com.boomeranger.app.data.ExportRepository
import com.boomeranger.app.media.ExportProgressListener
import com.boomeranger.app.media.ForwardClipPreparer
import com.boomeranger.app.media.Media3TransformHelper
import com.boomeranger.app.media.ReverseVideoBuilder
import com.boomeranger.app.media.VideoConcatenationService
import com.boomeranger.app.model.ExportResult
import com.boomeranger.app.model.ExportSettings
import com.boomeranger.app.model.ExportStage
import com.boomeranger.app.model.VideoMetadata
import com.boomeranger.app.util.AppLogger
import com.boomeranger.app.util.OutputSizeResolver
import com.boomeranger.app.util.UriFileCopier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Orchestrates the full boomerang export pipeline on a background dispatcher.
 */
@UnstableApi
class BoomerangExportUseCase(
    context: Context,
    private val uriFileCopier: UriFileCopier = UriFileCopier(context),
    private val transformHelper: Media3TransformHelper = Media3TransformHelper(context),
    private val forwardClipPreparer: ForwardClipPreparer = ForwardClipPreparer(transformHelper),
    private val reverseVideoBuilder: ReverseVideoBuilder = ReverseVideoBuilder(),
    private val concatenationService: VideoConcatenationService =
        VideoConcatenationService(transformHelper),
    private val exportRepository: ExportRepository = ExportRepository(context),
) {
    private val appContext = context.applicationContext

    suspend fun export(
        metadata: VideoMetadata,
        settings: ExportSettings,
        progressListener: ExportProgressListener,
    ): ExportResult = withContext(Dispatchers.Default) {
        val workRoot = File(
            appContext.cacheDir,
            "export_${System.currentTimeMillis()}"
        ).also { it.mkdirs() }

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
            )

            AppLogger.i(
                "Export start: file=${metadata.displayName}, " +
                    "src=${metadata.orientedWidth}x${metadata.orientedHeight}, " +
                    "out=${outputSize.width}x${outputSize.height}, " +
                    "repeats=${settings.repeatCount.value}, mute=${settings.muteAudio}"
            )

            progressListener.onProgress(ExportStage.PREPARING_FORWARD, 0.08f)
            ensureActive()
            val forwardFile = File(workRoot, "forward.mp4")
            forwardClipPreparer.prepare(
                inputFile = localInput,
                metadata = metadata,
                outputSize = outputSize,
                removeAudio = settings.muteAudio,
                outputFile = forwardFile,
                onProgress = { p ->
                    progressListener.onProgress(
                        ExportStage.PREPARING_FORWARD,
                        0.08f + p * 0.22f
                    )
                },
            )

            progressListener.onProgress(ExportStage.GENERATING_REVERSE, 0.32f)
            ensureActive()
            val reverseResult = reverseVideoBuilder.buildReversedSegment(
                preparedForwardFile = forwardFile,
                workDir = File(workRoot, "reverse_work"),
                sourceMetadata = metadata,
                onProgress = { p ->
                    progressListener.onProgress(
                        ExportStage.GENERATING_REVERSE,
                        0.32f + p * 0.35f
                    )
                },
            )

            progressListener.onProgress(ExportStage.CONCATENATING, 0.68f)
            ensureActive()
            val finalFile = File(
                appContext.getExternalFilesDir(null) ?: appContext.filesDir,
                "boomerangs/${outputFileName()}"
            )
            concatenationService.concatenateBoomerang(
                forwardFile = forwardFile,
                reverseFile = reverseResult.reverseFile,
                repeatCount = settings.repeatCount.value,
                outputFile = finalFile,
                removeAudio = settings.muteAudio,
                onProgress = { p ->
                    progressListener.onProgress(
                        ExportStage.EXPORTING,
                        0.68f + p * 0.22f
                    )
                },
            )

            progressListener.onProgress(ExportStage.SAVING, 0.92f)
            ensureActive()
            val durationMs = readDurationMs(finalFile)
            val provisional = ExportResult(
                outputFile = finalFile,
                mediaStoreUri = null,
                width = reverseResult.width,
                height = reverseResult.height,
                durationMs = durationMs,
            )
            val galleryUri = runCatching { exportRepository.saveToGallery(provisional) }
                .onFailure { AppLogger.w("Gallery save failed; file still available locally", it) }
                .getOrNull()

            val result = provisional.copy(mediaStoreUri = galleryUri)
            progressListener.onProgress(ExportStage.COMPLETED, 1f)
            result
        } catch (t: Throwable) {
            AppLogger.e("Boomerang export failed", t)
            progressListener.onProgress(ExportStage.FAILED, 0f)
            throw t
        } finally {
            // Keep final output; clean intermediate cache work dir.
            workRoot.deleteRecursivelySafely()
        }
    }

    private fun outputFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "boomerang_$stamp.mp4"
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
