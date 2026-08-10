package com.boomeranger.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boomeranger.app.data.VideoPickerManager
import com.boomeranger.app.model.BoomerangUiState
import com.boomeranger.app.model.ExportStage
import com.boomeranger.app.model.RepeatCount
import com.boomeranger.app.model.ResolutionOption
import com.boomeranger.app.model.VideoMetadata
import com.boomeranger.app.ui.components.SegmentedSelector
import com.boomeranger.app.ui.components.VideoPlayer
import com.boomeranger.app.ui.theme.Danger
import com.boomeranger.app.ui.theme.Ink
import com.boomeranger.app.ui.theme.Leaf
import com.boomeranger.app.ui.theme.Mist
import com.boomeranger.app.ui.theme.Moss
import com.boomeranger.app.ui.theme.Sand
import java.util.Locale

@Composable
fun BoomerangAppScreen(viewModel: BoomerangViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> viewModel.onVideoPicked(uri) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Ink, Moss.copy(alpha = 0.85f), Ink)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HomeHeader(
                onPickVideo = { VideoPickerManager.launchCompose(picker) },
                enabled = !state.isExporting,
            )

            state.selectedVideo?.let { video ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(400)) + slideInVertically(
                        animationSpec = tween(450, easing = FastOutSlowInEasing)
                    ) { it / 4 },
                ) {
                    SelectedVideoPanel(video)
                }

                ExportSettingsPanel(
                    state = state,
                    onRepeat = viewModel::setRepeatCount,
                    onResolution = viewModel::setResolution,
                    onMute = viewModel::setMuteAudio,
                    onExport = viewModel::export,
                )
            }

            if (state.isExporting || state.stage == ExportStage.FAILED) {
                ExportProgressPanel(state = state, onRetry = viewModel::retryExport)
            }

            state.result?.let { result ->
                ResultPanel(
                    resultUri = result.mediaStoreUri
                        ?: Uri.fromFile(result.outputFile),
                    aspectRatio = if (result.height > 0) {
                        result.width.toFloat() / result.height.toFloat()
                    } else {
                        16f / 9f
                    },
                    onShare = {
                        viewModel.buildShareIntent()?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, "Share boomerang"))
                        }
                    },
                    onSave = viewModel::saveAgain,
                    onOpen = {
                        viewModel.buildOpenIntent()?.let { intent ->
                            runCatching { context.startActivity(intent) }
                        }
                    },
                )
            }

            state.infoMessage?.let { InfoBanner(it) }
            state.errorMessage?.let { ErrorBanner(it, onRetry = viewModel::retryExport) }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HomeHeader(onPickVideo: () -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Boomeranger",
            style = MaterialTheme.typography.displayLarge,
            color = Mist,
        )
        Text(
            text = "Choose a video up to 3 seconds.",
            style = MaterialTheme.typography.bodyLarge,
            color = Mist.copy(alpha = 0.78f),
        )
        Button(
            onClick = onPickVideo,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Leaf,
                contentColor = Ink,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Choose video")
        }
    }
}

@Composable
private fun SelectedVideoPanel(video: VideoMetadata) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Selected clip",
            style = MaterialTheme.typography.headlineMedium,
            color = Mist,
        )
        video.thumbnail?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Video thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        } ?: Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Moss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Movie, contentDescription = null, tint = Sand, modifier = Modifier.size(48.dp))
        }

        MetaRow("File", video.displayName)
        MetaRow("Duration", String.format(Locale.US, "%.2fs", video.durationSeconds))
        MetaRow("Resolution", "${video.orientedWidth}×${video.orientedHeight}")
        MetaRow(
            "Frame rate",
            video.frameRate?.let { String.format(Locale.US, "%.2f fps", it) } ?: "Unavailable"
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Mist.copy(alpha = 0.65f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Mist)
    }
}

@Composable
private fun ExportSettingsPanel(
    state: BoomerangUiState,
    onRepeat: (RepeatCount) -> Unit,
    onResolution: (ResolutionOption) -> Unit,
    onMute: (Boolean) -> Unit,
    onExport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Export settings",
            style = MaterialTheme.typography.headlineMedium,
            color = Mist,
        )
        Text("Repeat count", style = MaterialTheme.typography.titleMedium, color = Sand)
        SegmentedSelector(
            options = RepeatCount.entries,
            selected = state.settings.repeatCount,
            labelOf = { it.label },
            onSelected = onRepeat,
        )
        Text("Resolution", style = MaterialTheme.typography.titleMedium, color = Sand)
        SegmentedSelector(
            options = ResolutionOption.entries,
            selected = state.settings.resolution,
            labelOf = { it.label },
            onSelected = onResolution,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Mute exported audio", style = MaterialTheme.typography.titleMedium, color = Mist)
                Text(
                    "Recommended: reverse audio is not synthesized.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist.copy(alpha = 0.65f),
                )
            }
            Switch(
                checked = state.settings.muteAudio,
                onCheckedChange = onMute,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Leaf,
                    checkedThumbColor = Ink,
                ),
            )
        }
        Button(
            onClick = onExport,
            enabled = !state.isExporting && state.selectedVideo != null,
            colors = ButtonDefaults.buttonColors(containerColor = Leaf, contentColor = Ink),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Export boomerang")
        }
    }
}

@Composable
private fun ExportProgressPanel(state: BoomerangUiState, onRetry: () -> Unit) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(300),
        label = "exportProgress",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (state.stage == ExportStage.FAILED) "Export failed" else "Exporting",
            style = MaterialTheme.typography.headlineMedium,
            color = Mist,
        )
        Text(
            text = state.stage.label,
            style = MaterialTheme.typography.bodyLarge,
            color = Sand,
        )
        if (state.isExporting) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = Leaf,
                trackColor = Mist.copy(alpha = 0.15f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Leaf,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = Mist,
                )
            }
        } else if (state.stage == ExportStage.FAILED) {
            TextButton(onClick = onRetry) { Text("Retry", color = Leaf) }
        }
    }
}

@Composable
private fun ResultPanel(
    resultUri: Uri,
    aspectRatio: Float,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Result",
            style = MaterialTheme.typography.headlineMedium,
            color = Mist,
        )
        VideoPlayer(uri = resultUri, aspectRatio = aspectRatio)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Leaf, contentColor = Ink),
                modifier = Modifier.weight(1f),
            ) { Text("Save to gallery") }
            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(containerColor = Sand, contentColor = Ink),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text("Share")
            }
        }
        TextButton(onClick = onOpen) {
            Text("Open in gallery", color = Leaf)
        }
    }
}

@Composable
private fun InfoBanner(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = Sand,
        modifier = Modifier
            .fillMaxWidth()
            .background(Moss.copy(alpha = 0.7f))
            .padding(12.dp),
    )
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Danger.copy(alpha = 0.18f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Mist)
        TextButton(onClick = onRetry) { Text("Retry", color = Leaf) }
    }
}
