package com.boomeranger.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.boomeranger.app.model.ExportFormat
import com.boomeranger.app.model.ExportStage
import com.boomeranger.app.model.FrameRateOption
import com.boomeranger.app.model.RepeatCount
import com.boomeranger.app.model.ResolutionOption
import com.boomeranger.app.model.SpeedOption
import com.boomeranger.app.model.VideoMetadata
import com.boomeranger.app.ui.components.BoomerangDemoHero
import com.boomeranger.app.ui.components.ClipWindowPicker
import com.boomeranger.app.ui.components.GifPlayer
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
    val showingResult = state.result != null
    val canNavigateHome =
        !state.isExporting && (showingResult || state.selectedVideo != null)

    BackHandler(enabled = canNavigateHome) {
        viewModel.goHome()
    }

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
                // Keep content clear of status bar, cutouts, and gesture/nav bars.
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (showingResult) {
                state.result?.let { result ->
                    ResultPanel(
                        resultUri = result.mediaStoreUri
                            ?: Uri.fromFile(result.outputFile),
                        format = result.format,
                        aspectRatio = if (result.height > 0) {
                            result.width.toFloat() / result.height.toFloat()
                        } else {
                            16f / 9f
                        },
                        onBack = viewModel::goHome,
                        onShare = {
                            viewModel.buildShareIntent()?.let { intent ->
                                context.startActivity(
                                    Intent.createChooser(intent, "Share boomerang")
                                )
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
            } else {
                HomeHeader(
                    onPickVideo = { VideoPickerManager.launchCompose(picker) },
                    enabled = !state.isExporting,
                    showBack = state.selectedVideo != null,
                    onBack = viewModel::goHome,
                    showDemo = state.selectedVideo == null && !state.isExporting,
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

                    ClipWindowPicker(
                        uri = video.uri,
                        durationMs = video.durationMs,
                        trimStartMs = state.trimStartMs,
                        onTrimStartChanged = viewModel::setTrimStartMs,
                        aspectRatio = if (video.orientedHeight > 0) {
                            video.orientedWidth.toFloat() / video.orientedHeight.toFloat()
                        } else {
                            16f / 9f
                        },
                    )

                    ExportSettingsPanel(
                        state = state,
                        onRepeat = viewModel::setRepeatCount,
                        onResolution = viewModel::setResolution,
                        onFrameRate = viewModel::setFrameRate,
                        onSpeed = viewModel::setSpeed,
                        onFormat = viewModel::setFormat,
                        onMute = viewModel::setMuteAudio,
                        onExport = viewModel::export,
                    )
                }

                if (state.isExporting || state.stage == ExportStage.FAILED) {
                    ExportProgressPanel(state = state, onRetry = viewModel::retryExport)
                }
            }

            state.infoMessage?.let { InfoBanner(it) }
            state.errorMessage?.let { ErrorBanner(it, onRetry = viewModel::retryExport) }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HomeHeader(
    onPickVideo: () -> Unit,
    enabled: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    showDemo: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showBack) {
            TextButton(
                onClick = onBack,
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Leaf,
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Back", color = Leaf)
            }
        }
        Text(
            text = "Boomeranger",
            style = MaterialTheme.typography.displayLarge,
            color = Mist,
        )
        if (showDemo) {
            BoomerangDemoHero()
        } else {
            Text(
                text = "Choose a video up to 3 seconds.",
                style = MaterialTheme.typography.bodyLarge,
                color = Mist.copy(alpha = 0.78f),
            )
        }
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
            Icon(
                Icons.Default.Movie,
                contentDescription = null,
                tint = Sand,
                modifier = Modifier.size(48.dp),
            )
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
    onFrameRate: (FrameRateOption) -> Unit,
    onSpeed: (SpeedOption) -> Unit,
    onFormat: (ExportFormat) -> Unit,
    onMute: (Boolean) -> Unit,
    onExport: () -> Unit,
) {
    val isGif = state.settings.format == ExportFormat.GIF
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Export settings",
            style = MaterialTheme.typography.headlineMedium,
            color = Mist,
        )
        Text("Format", style = MaterialTheme.typography.titleMedium, color = Sand)
        SegmentedSelector(
            options = ExportFormat.entries,
            selected = state.settings.format,
            labelOf = { it.label },
            onSelected = onFormat,
        )
        Text("Repeat count", style = MaterialTheme.typography.titleMedium, color = Sand)
        SegmentedSelector(
            options = RepeatCount.entries,
            selected = state.settings.repeatCount,
            labelOf = { it.label },
            onSelected = onRepeat,
        )
        Text("Speed", style = MaterialTheme.typography.titleMedium, color = Sand)
        SegmentedSelector(
            options = SpeedOption.entries,
            selected = state.settings.speed,
            labelOf = { it.label },
            onSelected = onSpeed,
        )
        if (!isGif) {
            Text("Frame rate", style = MaterialTheme.typography.titleMedium, color = Sand)
            SegmentedSelector(
                options = FrameRateOption.entries,
                selected = state.settings.frameRate,
                labelOf = { "${it.label} fps" },
                onSelected = onFrameRate,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Mute exported audio",
                        style = MaterialTheme.typography.titleMedium,
                        color = Mist,
                    )
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
        } else {
            Text(
                "GIF exports are silent, locked to 30 fps, and capped at 1080p so they encode faster.",
                style = MaterialTheme.typography.bodyMedium,
                color = Mist.copy(alpha = 0.65f),
            )
        }
        Button(
            onClick = onExport,
            enabled = !state.isExporting && state.selectedVideo != null,
            colors = ButtonDefaults.buttonColors(containerColor = Leaf, contentColor = Ink),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isGif) "Export GIF" else "Export boomerang")
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
    format: ExportFormat,
    aspectRatio: Float,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to start",
                    tint = Leaf,
                )
            }
            Text(
                text = if (format == ExportFormat.GIF) "GIF result" else "Result",
                style = MaterialTheme.typography.headlineMedium,
                color = Mist,
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(onClick = onBack) {
            Text("Create another boomerang", color = Leaf)
        }
        when (format) {
            ExportFormat.GIF -> GifPlayer(uri = resultUri, aspectRatio = aspectRatio)
            ExportFormat.MP4 -> VideoPlayer(uri = resultUri, aspectRatio = aspectRatio)
        }
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
