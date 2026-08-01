package com.boomeranger.app.model

enum class ExportStage(val label: String) {
    IDLE("Idle"),
    READING_METADATA("Reading metadata"),
    PREPARING_FORWARD("Preparing forward clip"),
    GENERATING_REVERSE("Generating reverse segment"),
    CONCATENATING("Building boomerang cycles"),
    EXPORTING("Exporting final video"),
    SAVING("Saving to storage"),
    COMPLETED("Completed"),
    FAILED("Failed"),
}
