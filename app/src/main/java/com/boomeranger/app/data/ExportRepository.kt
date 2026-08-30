package com.boomeranger.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.boomeranger.app.R
import com.boomeranger.app.model.ExportFormat
import com.boomeranger.app.model.ExportResult
import com.boomeranger.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Persists exported MP4 / GIF files into MediaStore and builds share / view intents.
 */
class ExportRepository(private val context: Context) {

    suspend fun saveToGallery(result: ExportResult): Uri = withContext(Dispatchers.IO) {
        result.mediaStoreUri?.let { return@withContext it }

        when (result.format) {
            ExportFormat.MP4 -> saveVideo(result)
            ExportFormat.GIF -> saveImage(result)
        }
    }

    private fun saveVideo(result: ExportResult): Uri {
        val file = result.outputFile
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, result.mimeType)
            put(MediaStore.Video.Media.WIDTH, result.width)
            put(MediaStore.Video.Media.HEIGHT, result.height)
            put(MediaStore.Video.Media.DURATION, result.durationMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/" + galleryAlbum()
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        return insertAndWrite(collection, values, file, isVideo = true)
    }

    private fun saveImage(result: ExportResult): Uri {
        val file = result.outputFile
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, result.mimeType)
            put(MediaStore.Images.Media.WIDTH, result.width)
            put(MediaStore.Images.Media.HEIGHT, result.height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + galleryAlbum()
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        return insertAndWrite(collection, values, file, isVideo = false)
    }

    private fun insertAndWrite(
        collection: Uri,
        values: ContentValues,
        file: File,
        isVideo: Boolean,
    ): Uri {
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: error("Unable to create MediaStore entry for exported file.")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(file).use { input -> input.copyTo(output) }
            } ?: error("Unable to open MediaStore output stream.")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                if (isVideo) {
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                } else {
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, values, null, null)
            }
            AppLogger.i("Saved export to gallery: $uri")
            return uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    private fun galleryAlbum(): String = context.getString(R.string.gallery_album)

    fun buildShareIntent(file: File, mimeType: String = "video/mp4"): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildViewIntent(uri: Uri, mimeType: String = "video/mp4"): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
