package com.boomeranger.app.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.boomeranger.app.util.AppLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Shared Media3 Transformer helpers for trim/scale/mute and concatenation export.
 */
@UnstableApi
class Media3TransformHelper(private val context: Context) {

    suspend fun exportEditedItem(
        editedMediaItem: EditedMediaItem,
        outputFile: File,
        onProgress: (Float) -> Unit = {},
    ) {
        val composition = Composition.Builder(
            EditedMediaItemSequence.Builder(editedMediaItem).build()
        )
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
        exportComposition(composition, outputFile, onProgress)
    }

    suspend fun exportComposition(
        composition: Composition,
        outputFile: File,
        onProgress: (Float) -> Unit = {},
    ) = suspendCancellableCoroutine { cont ->
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val completed = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()

        lateinit var transformer: Transformer
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (!completed.compareAndSet(false, true)) return
                mainHandler.removeCallbacksAndMessages(null)
                AppLogger.i(
                    "Media3 export completed: ${outputFile.name}, " +
                        "size=${outputFile.length()} bytes"
                )
                onProgress(1f)
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                if (!completed.compareAndSet(false, true)) return
                mainHandler.removeCallbacksAndMessages(null)
                AppLogger.e("Media3 export failed", exportException)
                if (cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException(
                            usefulExportMessage(exportException),
                            exportException
                        )
                    )
                }
            }
        }

        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(listener)
            .build()

        val progressRunnable = object : Runnable {
            override fun run() {
                if (!cont.isActive || completed.get()) return
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                }
                mainHandler.postDelayed(this, 250)
            }
        }

        cont.invokeOnCancellation {
            completed.set(true)
            mainHandler.removeCallbacksAndMessages(null)
            runCatching { transformer.cancel() }
        }

        mainHandler.post {
            try {
                transformer.start(composition, outputFile.absolutePath)
                mainHandler.post(progressRunnable)
            } catch (t: Throwable) {
                if (completed.compareAndSet(false, true) && cont.isActive) {
                    cont.resumeWithException(t)
                }
            }
        }
    }

    fun buildTrimmedScaledItem(
        inputUri: String,
        startPositionMs: Long,
        endPositionMs: Long,
        outputWidth: Int,
        outputHeight: Int,
        removeAudio: Boolean,
    ): EditedMediaItem {
        require(endPositionMs > startPositionMs) {
            "Invalid clip window: $startPositionMs → $endPositionMs"
        }
        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startPositionMs.coerceAtLeast(0L))
                    .setEndPositionMs(endPositionMs)
                    .build()
            )
            .build()

        val videoEffects = buildList<Effect> {
            add(
                Presentation.createForWidthAndHeight(
                    outputWidth,
                    outputHeight,
                    Presentation.LAYOUT_SCALE_TO_FIT
                )
            )
        }

        return EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(removeAudio)
            .setEffects(Effects(/* audioProcessors = */ emptyList(), videoEffects))
            .build()
    }

    fun buildItem(
        file: File,
        removeAudio: Boolean = true,
    ): EditedMediaItem {
        return EditedMediaItem.Builder(MediaItem.fromUri(file.absolutePath))
            .setRemoveAudio(removeAudio)
            .build()
    }

    fun buildBoomerangComposition(
        forwardFile: File,
        reverseFile: File,
        repeatCount: Int,
        removeAudio: Boolean,
    ): Composition {
        val forward = buildItem(forwardFile, removeAudio)
        val reverse = buildItem(reverseFile, removeAudio = true)
        val items = buildList {
            repeat(repeatCount.coerceIn(2, 4)) {
                add(forward)
                add(reverse)
            }
        }
        val sequence = EditedMediaItemSequence.Builder(items).build()
        // Reverse path is SDR; tone-map HDR sources so forward/reverse color spaces match.
        // If audio is kept, reverse segments are silent — force an audio track so concat stays valid.
        val builder = Composition.Builder(sequence)
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        if (!removeAudio) {
            builder.experimentalSetForceAudioTrack(true)
        }
        return builder.build()
    }

    private fun usefulExportMessage(error: ExportException): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("HDR", ignoreCase = true) ->
                "HDR export is not supported on this device/path. " +
                    "Tried tone-mapping to SDR but export still failed: $message"
            message.contains("codec", ignoreCase = true) ->
                "A required media codec failed during export: $message"
            else -> "Export failed: ${message.ifBlank { error.toString() }}"
        }
    }
}
