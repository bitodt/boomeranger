package com.boomeranger.app.model

data class ExportSettings(
    val repeatCount: RepeatCount = RepeatCount.THREE,
    val resolution: ResolutionOption = ResolutionOption.ORIGINAL,
    val muteAudio: Boolean = true,
)
