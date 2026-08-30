package com.boomeranger.app.ui.components

import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.boomeranger.app.util.AppLogger
import java.io.File

/**
 * Plays an exported GIF in a [WebView].
 *
 * Android [android.graphics.ImageDecoder] materializes every frame at once and
 * paints a gray placeholder when a 720p loop OOMs — both in-app and in many
 * gallery apps. WebView streams frames, so the same file still animates.
 */
@Composable
fun GifPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
    file: File? = null,
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.apply {
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    javaScriptEnabled = false
                    allowFileAccess = true
                    allowContentAccess = true
                    displayZoomControls = false
                    builtInZoomControls = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    mediaPlaybackRequiresUserGesture = false
                    blockNetworkImage = true
                    blockNetworkLoads = true
                }
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            val src = resolveGifSrc(uri, file)
            if (src == null) {
                AppLogger.e("GIF preview has no readable source for $uri")
                return@AndroidView
            }
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"/>
                  <style>
                    html,body{margin:0;padding:0;width:100%;height:100%;background:transparent;
                      display:flex;align-items:center;justify-content:center;overflow:hidden;}
                    img{max-width:100%;max-height:100%;object-fit:contain;display:block;}
                  </style>
                </head>
                <body><img src="$src" alt="gif"/></body>
                </html>
            """.trimIndent()
            val base = src.substringBeforeLast('/', src)
            webView.loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceAtLeast(0.5f)),
    )
}

private fun resolveGifSrc(uri: Uri, file: File?): String? {
    if (file != null && file.exists()) {
        return file.toURI().toString()
    }
    return when (uri.scheme) {
        "file", "content" -> uri.toString()
        else -> uri.toString().takeIf { it.isNotBlank() }
    }
}
