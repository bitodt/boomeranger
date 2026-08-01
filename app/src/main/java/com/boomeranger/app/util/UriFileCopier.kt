package com.boomeranger.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/**
 * Copies a SAF content URI into app cache so MediaExtractor / Media3 can open a stable file path.
 */
class UriFileCopier(private val context: Context) {

    fun copyToCache(uri: Uri, fileNameHint: String = "input.mp4"): File {
        val safeName = fileNameHint
            .substringAfterLast('/')
            .ifBlank { "input.mp4" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")

        val target = File(context.cacheDir, "imports/${System.currentTimeMillis()}_$safeName")
        target.parentFile?.mkdirs()

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open selected video: $uri")

        return target
    }

    fun queryDisplayName(uri: Uri): String? {
        val resolver = context.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }
}
