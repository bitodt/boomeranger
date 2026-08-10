package com.boomeranger.app.model

import android.net.Uri
import java.io.File

data class ExportResult(
    val outputFile: File,
    val mediaStoreUri: Uri?,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val format: ExportFormat = ExportFormat.MP4,
) {
    val mimeType: String get() = format.mimeType
}
