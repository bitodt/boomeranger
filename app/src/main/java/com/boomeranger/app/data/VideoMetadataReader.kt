package com.boomeranger.app.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.boomeranger.app.model.VideoMetadata
import com.boomeranger.app.util.AppLogger
import com.boomeranger.app.util.UriFileCopier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads duration, dimensions, rotation, frame rate, and a thumbnail via platform APIs.
 */
class VideoMetadataReader(
    private val context: Context,
    private val uriFileCopier: UriFileCopier = UriFileCopier(context),
) {

    suspend fun read(uri: Uri): VideoMetadata = withContext(Dispatchers.IO) {
        val displayName = uriFileCopier.queryDisplayName(uri) ?: "video.mp4"
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: error("Could not read video duration.")

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: error("Could not read video width.")

            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: error("Could not read video height.")

            val rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0

            val frameRate = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
            )?.toFloatOrNull()

            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()

            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            val thumbnail = extractThumbnail(retriever)

            val wasTrimmed = durationMs > VideoMetadata.MAX_INPUT_DURATION_MS

            VideoMetadata(
                uri = uri,
                displayName = displayName,
                durationMs = durationMs,
                width = width,
                height = height,
                rotationDegrees = rotation,
                frameRate = frameRate,
                bitrate = bitrate,
                mimeType = mimeType,
                thumbnail = thumbnail,
                wasTrimmedToMax = wasTrimmed,
            )
        } catch (t: Throwable) {
            AppLogger.e("Failed to read video metadata for $uri", t)
            throw IllegalArgumentException(
                "Unable to read this video. Prefer a common local MP4 file.",
                t
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractThumbnail(retriever: MediaMetadataRetriever): Bitmap? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    512,
                    512
                )
            } else {
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (t: Throwable) {
            AppLogger.w("Thumbnail extraction failed", t)
            null
        }
    }
}
