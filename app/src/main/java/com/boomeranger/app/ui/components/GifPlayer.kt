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
import com.boomeranger.app.util.AppLogger
import java.io.File
import kotlin.math.max

/**
 * Plays an animated GIF via [android.graphics.drawable.AnimatedImageDrawable] (API 28+).
 * Large 1080p loops are downsampled on decode so ImageDecoder does not OOM
 * and leave a gray empty view.
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
                ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val srcW = info.size.width
                    val srcH = info.size.height
                    val longest = max(srcW, srcH)
                    if (longest > PREVIEW_MAX_EDGE) {
                        val scale = PREVIEW_MAX_EDGE.toFloat() / longest
                        decoder.setTargetSize(
                            (srcW * scale).toInt().coerceAtLeast(2),
                            (srcH * scale).toInt().coerceAtLeast(2),
                        )
                    }
                }
            } else {
                null
            }
        }.onFailure { AppLogger.e("GIF preview decode failed for $uri", it) }
            .getOrNull()

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

private const val PREVIEW_MAX_EDGE = 720
