package com.boomeranger.app.ui.components

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * Plays an animated GIF via [android.graphics.drawable.AnimatedImageDrawable] (API 28+)
 * or a static decode fallback on older devices.
 */
@Composable
fun GifPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
) {
    val context = LocalContext.current
    val imageView = remember(uri) {
        ImageView(context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }

    DisposableEffect(uri) {
        val drawable = runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                val source = when (uri.scheme) {
                    "file" -> ImageDecoder.createSource(File(uri.path!!))
                    else -> ImageDecoder.createSource(context.contentResolver, uri)
                }
                ImageDecoder.decodeDrawable(source)
            } else {
                null
            }
        }.getOrNull()

        if (drawable != null) {
            imageView.setImageDrawable(drawable)
            if (Build.VERSION.SDK_INT >= 28 &&
                drawable is android.graphics.drawable.AnimatedImageDrawable
            ) {
                drawable.repeatCount = android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                drawable.start()
            }
        } else {
            imageView.setImageURI(uri)
        }

        onDispose {
            if (Build.VERSION.SDK_INT >= 28 &&
                drawable is android.graphics.drawable.AnimatedImageDrawable
            ) {
                drawable.stop()
            }
            imageView.setImageDrawable(null)
        }
    }

    AndroidView(
        factory = { imageView },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceAtLeast(0.5f)),
    )
}
