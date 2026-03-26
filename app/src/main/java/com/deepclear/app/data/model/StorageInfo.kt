package com.deepclear.app.data.model

/**
 * Holds device storage information.
 */
data class StorageInfo(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L
) {
    val usedPercentage: Float
        get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f

    val freePercentage: Float
        get() = if (totalBytes > 0) freeBytes.toFloat() / totalBytes.toFloat() else 0f
}
