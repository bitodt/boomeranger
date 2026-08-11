package com.boomeranger.app.media

/**
 * Where extracted frames live between extract and encode.
 *
 * [MEMORY] keeps ARGB bitmaps in RAM (faster; no JPEG round-trip).
 * [DISK] writes JPEG files (slower; safer on low-RAM / high-res exports).
 */
enum class FrameStorageMode {
    MEMORY,
    DISK,
}
