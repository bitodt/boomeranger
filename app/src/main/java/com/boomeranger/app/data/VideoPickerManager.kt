package com.boomeranger.app.data

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Thin helper around the Storage Access Framework video picker contract.
 * UI owns the Compose rememberLauncherForActivityResult; this documents the MIME filter.
 */
object VideoPickerManager {
    const val VIDEO_MIME = "video/*"
    const val PREFERRED_MIME = "video/mp4"

    fun createPickVisualMediaRequest(): String = VIDEO_MIME

    fun launch(launcher: ActivityResultLauncher<String>) {
        launcher.launch(VIDEO_MIME)
    }

    fun launchCompose(launcher: ManagedActivityResultLauncher<String, Uri?>) {
        launcher.launch(VIDEO_MIME)
    }

    val openDocumentContract: ActivityResultContracts.GetContent
        get() = ActivityResultContracts.GetContent()
}
