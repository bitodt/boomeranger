package com.boomeranger.app.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.boomeranger.app.model.VideoMetadata
import com.boomeranger.app.ui.theme.Ink
import com.boomeranger.app.ui.theme.Leaf
import com.boomeranger.app.ui.theme.Mist
import com.boomeranger.app.ui.theme.Moss
import com.boomeranger.app.ui.theme.Sand
import com.boomeranger.app.util.ClipWindowResolver
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Lets the user scrub to choose which ≤3-second window of a longer clip to export.
 * For clips already ≤3s, shows that the full video will be used.
 */
@Composable
fun ClipWindowPicker(
    uri: Uri,
    durationMs: Long,
    trimStartMs: Long,
    onTrimStartChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
) {
    val window = remember(durationMs, trimStartMs) {
        ClipWindowResolver.resolve(durationMs, trimStartMs)
    }
    val needsTrim = durationMs > VideoMetadata.MAX_INPUT_DURATION_MS
    val maxStartMs = (durationMs - VideoMetadata.MAX_INPUT_DURATION_MS).coerceAtLeast(0L)

    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            volume = 0f
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPreviewPlaying = false
                    player.seekTo(window.startMs)
                    player.pause()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPreviewPlaying = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Keep preview parked at the window start while scrubbing (unless actively previewing).
    LaunchedEffect(window.startMs, isPreviewPlaying) {
        if (!isPreviewPlaying) {
            player.seekTo(window.startMs)
            player.pause()
        }
    }

    // Stop preview if it runs past the selected window end.
    LaunchedEffect(isPreviewPlaying, window.endMs) {
        if (!isPreviewPlaying) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(50)
            if (player.currentPosition >= window.endMs) {
                player.pause()
                player.seekTo(window.startMs)
                isPreviewPlaying = false
                break
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Choose your 3 seconds",
            style = MaterialTheme.typography.headlineMedium,
            color = Mist,
        )
        Text(
            text = if (needsTrim) {
                "Scroll to pick where the ${formatSeconds(window.durationMs)} loop starts."
            } else {
                "This clip is already within 3 seconds — the full video will be used."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Mist.copy(alpha = 0.7f),
        )

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio.coerceAtLeast(0.5f))
                .clip(RoundedCornerShape(8.dp))
                .background(Moss),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
            },
            update = { it.player = player },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = {
                    if (isPreviewPlaying) {
                        player.pause()
                        player.seekTo(window.startMs)
                        isPreviewPlaying = false
                    } else {
                        player.seekTo(window.startMs)
                        player.play()
                        isPreviewPlaying = true
                    }
                },
            ) {
                Icon(
                    imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPreviewPlaying) "Pause preview" else "Preview window",
                    tint = Leaf,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = "${formatTimestamp(window.startMs)}  →  ${formatTimestamp(window.endMs)}",
                style = MaterialTheme.typography.titleMedium,
                color = Sand,
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        if (needsTrim) {
            Slider(
                value = trimStartMs.toFloat().coerceIn(0f, maxStartMs.toFloat()),
                onValueChange = { value ->
                    if (isPreviewPlaying) {
                        player.pause()
                        isPreviewPlaying = false
                    }
                    onTrimStartChanged(value.roundToLong().coerceIn(0L, maxStartMs))
                },
                valueRange = 0f..maxStartMs.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Leaf,
                    activeTrackColor = Leaf,
                    inactiveTrackColor = Mist.copy(alpha = 0.2f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Start",
                    style = MaterialTheme.typography.labelLarge,
                    color = Mist.copy(alpha = 0.55f),
                )
                Text(
                    "Window ${formatSeconds(window.durationMs)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Leaf,
                )
                Text(
                    formatTimestamp(durationMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = Mist.copy(alpha = 0.55f),
                )
            }
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val totalSeconds = ms / 1000.0
    val minutes = (totalSeconds / 60).toInt()
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%04.1f", minutes, seconds)
}

private fun formatSeconds(ms: Long): String =
    String.format(Locale.US, "%.1fs", ms / 1000.0)
