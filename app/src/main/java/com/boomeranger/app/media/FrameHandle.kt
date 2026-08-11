package com.boomeranger.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * One extracted frame, either an in-memory bitmap or a JPEG on disk.
 */
sealed class FrameHandle {
    data class Memory(val bitmap: Bitmap) : FrameHandle()
    data class Disk(val file: File) : FrameHandle()
}

/**
 * Bitmap borrowed or decoded from a [FrameHandle].
 *
 * When [ownsBitmap] is true the caller must [recycleIfOwned] after use.
 * In-memory frames return the shared bitmap with [ownsBitmap] = false unless scaled.
 */
class OpenedFrame(
    val bitmap: Bitmap,
    val ownsBitmap: Boolean,
) {
    fun recycleIfOwned() {
        if (ownsBitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

fun FrameHandle.openBitmap(targetWidth: Int, targetHeight: Int): OpenedFrame {
    return when (this) {
        is FrameHandle.Memory -> {
            val source = bitmap
            if (source.isRecycled) {
                error("In-memory frame bitmap was already recycled.")
            }
            if (source.width == targetWidth && source.height == targetHeight) {
                OpenedFrame(source, ownsBitmap = false)
            } else {
                OpenedFrame(
                    Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true),
                    ownsBitmap = true,
                )
            }
        }
        is FrameHandle.Disk -> {
            val decoded = BitmapFactory.decodeFile(file.absolutePath)
                ?: error("Failed to decode ${file.name}")
            if (decoded.width == targetWidth && decoded.height == targetHeight) {
                OpenedFrame(decoded, ownsBitmap = true)
            } else {
                val scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
                if (scaled !== decoded) decoded.recycle()
                OpenedFrame(scaled, ownsBitmap = true)
            }
        }
    }
}
