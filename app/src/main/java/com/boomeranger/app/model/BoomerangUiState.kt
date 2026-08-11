package com.boomeranger.app.model

data class BoomerangUiState(
    val selectedVideo: VideoMetadata? = null,
    /** Start of the ≤3s window inside the source clip. Ignored when duration ≤ 3s. */
    val trimStartMs: Long = 0L,
    val settings: ExportSettings = ExportSettings(),
    val stage: ExportStage = ExportStage.IDLE,
    val progress: Float = 0f,
    val isExporting: Boolean = false,
    val result: ExportResult? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)
