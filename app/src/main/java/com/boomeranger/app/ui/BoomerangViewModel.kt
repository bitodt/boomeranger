package com.boomeranger.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.boomeranger.app.data.ExportRepository
import com.boomeranger.app.data.VideoMetadataReader
import com.boomeranger.app.domain.BoomerangExportUseCase
import com.boomeranger.app.model.BoomerangUiState
import com.boomeranger.app.model.ExportFormat
import com.boomeranger.app.model.ExportSettings
import com.boomeranger.app.model.ExportStage
import com.boomeranger.app.model.FrameRateOption
import com.boomeranger.app.model.RepeatCount
import com.boomeranger.app.model.ResolutionOption
import com.boomeranger.app.model.VideoMetadata
import com.boomeranger.app.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@UnstableApi
class BoomerangViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val metadataReader = VideoMetadataReader(application)
    private val exportUseCase = BoomerangExportUseCase(application)
    private val exportRepository = ExportRepository(application)

    private val _uiState = MutableStateFlow(BoomerangUiState())
    val uiState: StateFlow<BoomerangUiState> = _uiState.asStateFlow()

    private var exportJob: Job? = null

    fun onVideoPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    errorMessage = null,
                    infoMessage = null,
                    result = null,
                    stage = ExportStage.READING_METADATA,
                    progress = 0f,
                )
            }
            runCatching { metadataReader.read(uri) }
                .onSuccess { metadata -> handleMetadata(metadata) }
                .onFailure { error ->
                    AppLogger.e("Video pick/metadata failed", error)
                    _uiState.update {
                        it.copy(
                            selectedVideo = null,
                            stage = ExportStage.IDLE,
                            errorMessage = error.message
                                ?: "Could not read the selected video.",
                        )
                    }
                }
        }
    }

    private fun handleMetadata(metadata: VideoMetadata) {
        val info = when {
            metadata.exceedsMaxDuration ->
                "Video is longer than 3 seconds. Export will use the first 3 seconds."
            else -> null
        }
        _uiState.update {
            it.copy(
                selectedVideo = metadata,
                stage = ExportStage.IDLE,
                progress = 0f,
                infoMessage = info,
                errorMessage = null,
                result = null,
            )
        }
    }

    fun setRepeatCount(count: RepeatCount) {
        _uiState.update { it.copy(settings = it.settings.copy(repeatCount = count)) }
    }

    fun setResolution(option: ResolutionOption) {
        _uiState.update { it.copy(settings = it.settings.copy(resolution = option)) }
    }

    fun setFrameRate(option: FrameRateOption) {
        _uiState.update { it.copy(settings = it.settings.copy(frameRate = option)) }
    }

    fun setFormat(format: ExportFormat) {
        _uiState.update { it.copy(settings = it.settings.copy(format = format)) }
    }

    fun setMuteAudio(mute: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(muteAudio = mute)) }
    }

    fun export() {
        val metadata = _uiState.value.selectedVideo ?: return
        if (_uiState.value.isExporting) return

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExporting = true,
                    errorMessage = null,
                    result = null,
                    stage = ExportStage.READING_METADATA,
                    progress = 0f,
                )
            }
            runCatching {
                exportUseCase.export(
                    metadata = metadata,
                    settings = _uiState.value.settings,
                    progressListener = { stage, progress ->
                        _uiState.update {
                            it.copy(stage = stage, progress = progress.coerceIn(0f, 1f))
                        }
                    },
                )
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        result = result,
                        stage = ExportStage.COMPLETED,
                        progress = 1f,
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                AppLogger.e("Export UI failure", error)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        stage = ExportStage.FAILED,
                        errorMessage = error.message ?: "Export failed.",
                    )
                }
            }
        }
    }

    fun retryExport() = export()

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Returns to the starting screen, clearing the selected clip and export result.
     * Export settings are preserved.
     */
    fun goHome() {
        exportJob?.cancel()
        exportJob = null
        _uiState.update { current ->
            BoomerangUiState(settings = current.settings)
        }
    }

    fun buildShareIntent(): Intent? {
        val result = _uiState.value.result ?: return null
        return exportRepository.buildShareIntent(result.outputFile, result.mimeType)
    }

    fun buildOpenIntent(): Intent? {
        val result = _uiState.value.result ?: return null
        val uri = result.mediaStoreUri ?: androidx.core.content.FileProvider.getUriForFile(
            getApplication(),
            "${getApplication<Application>().packageName}.fileprovider",
            result.outputFile
        )
        return exportRepository.buildViewIntent(uri, result.mimeType)
    }

    fun saveAgain() {
        val result = _uiState.value.result ?: return
        viewModelScope.launch {
            runCatching { exportRepository.saveToGallery(result) }
                .onSuccess { uri ->
                    _uiState.update {
                        it.copy(
                            result = it.result?.copy(mediaStoreUri = uri),
                            infoMessage = "Saved to gallery.",
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not save to gallery.")
                    }
                }
        }
    }

    fun currentSettings(): ExportSettings = _uiState.value.settings
}
