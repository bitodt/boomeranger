package com.boomeranger.app.model

import android.graphics.Bitmap
import android.net.Uri

data class VideoMetadata(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val frameRate: Float?,
    val bitrate: Int?,
    val mimeType: String?,
    val thumbnail: Bitmap?,
    val wasTrimmedToMax: Boolean = false,
) {
    val orientedWidth: Int
        get() = if (rotationDegrees % 180 == 0) width else height

    val orientedHeight: Int
        get() = if (rotationDegrees % 180 == 0) height else width

    val durationSeconds: Double
        get() = durationMs / 1000.0

    val exceedsMaxDuration: Boolean
        get() = durationMs > MAX_INPUT_DURATION_MS

    companion object {
        const val MAX_INPUT_DURATION_MS = 3_000L
    }
}
