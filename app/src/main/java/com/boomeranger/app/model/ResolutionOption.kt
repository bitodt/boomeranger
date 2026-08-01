package com.boomeranger.app.model

/**
 * Export resolution policy. Never upscales; only downscales when source exceeds the cap.
 */
enum class ResolutionOption(val label: String) {
    ORIGINAL("Original"),
    FHD("FHD max"),
    HD("HD max");
}
