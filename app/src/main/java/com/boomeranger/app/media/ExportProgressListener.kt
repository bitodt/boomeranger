package com.boomeranger.app.media

import com.boomeranger.app.model.ExportStage

fun interface ExportProgressListener {
    fun onProgress(stage: ExportStage, progress: Float)
}
