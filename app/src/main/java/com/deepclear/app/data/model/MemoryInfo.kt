package com.deepclear.app.data.model

/**
 * Holds memory/RAM usage information.
 */
data class MemoryInfo(
    val totalRam: Long = 0L,
    val availableRam: Long = 0L,
    val usedRam: Long = 0L,
    val threshold: Long = 0L,
    val isLowMemory: Boolean = false
) {
    val usedPercentage: Float
        get() = if (totalRam > 0) usedRam.toFloat() / totalRam.toFloat() else 0f
}

/**
 * Represents a running app process.
 */
data class RunningApp(
    val packageName: String,
    val appName: String,
    val memoryUsageBytes: Long,
    val isSystemApp: Boolean = false
)

/**
 * Result from the performance optimizer.
 */
data class OptimizeResult(
    val ramFreedBytes: Long = 0L,
    val cachesClearedBytes: Long = 0L,
    val appsOptimized: Int = 0
)
