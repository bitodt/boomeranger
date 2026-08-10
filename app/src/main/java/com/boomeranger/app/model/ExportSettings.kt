package com.boomeranger.app.model

data class ExportSettings(
    val repeatCount: RepeatCount = RepeatCount.THREE,
    val resolution: ResolutionOption = ResolutionOption.ORIGINAL,
    val frameRate: FrameRateOption = FrameRateOption.FPS_30,
    val speed: SpeedOption = SpeedOption.ONE_X,
    val format: ExportFormat = ExportFormat.MP4,
    val muteAudio: Boolean = true,
)
