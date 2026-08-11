package com.boomeranger.app.model

enum class ExportFormat(val label: String, val mimeType: String, val fileExtension: String) {
    MP4("Video", "video/mp4", "mp4"),
    GIF("GIF", "image/gif", "gif");
}
