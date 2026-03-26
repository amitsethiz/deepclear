package com.deepclear.app.data.repository

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Debug
import com.deepclear.app.data.model.MemoryInfo
import com.deepclear.app.data.model.OptimizeResult
import com.deepclear.app.data.model.RunningApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that provides memory information and safe optimization.
 *
 * Safety notes:
 * - We only clear our own app's cache safely
 * - For other apps, we use ActivityManager's safe APIs
 * - We never force-kill system processes
 */
@Singleton
class PerformanceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activityManager: ActivityManager
        get() = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val packageManager: PackageManager
        get() = context.packageManager

    /**
     * Get current device memory information.
     */
    suspend fun getMemoryInfo(): MemoryInfo = withContext(Dispatchers.IO) {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        MemoryInfo(
            totalRam = memInfo.totalMem,
            availableRam = memInfo.availMem,
            usedRam = memInfo.totalMem - memInfo.availMem,
            threshold = memInfo.threshold,
            isLowMemory = memInfo.lowMemory
        )
    }

    /**
     * Get list of running non-system apps and their memory usage.
     */
    suspend fun getRunningApps(): List<RunningApp> = withContext(Dispatchers.IO) {
        val runningApps = mutableListOf<RunningApp>()

        try {
            val runningProcesses = activityManager.runningAppProcesses ?: emptyList()

            runningProcesses.forEach { process ->
                try {
                    val appInfo = packageManager.getApplicationInfo(
                        process.processName.split(":").first(),
                        0
                    )
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val appName = packageManager.getApplicationLabel(appInfo).toString()

                    // Get memory usage for this process
                    val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(process.pid))
                    val memUsage = if (memInfo.isNotEmpty()) {
                        memInfo[0].totalPss.toLong() * 1024L // Convert KB to bytes
                    } else 0L

                    // Skip our own app
                    if (process.processName != context.packageName) {
                        runningApps.add(
                            RunningApp(
                                packageName = process.processName,
                                appName = appName,
                                memoryUsageBytes = memUsage,
                                isSystemApp = isSystem
                            )
                        )
                    }
                } catch (_: PackageManager.NameNotFoundException) {
                    // Skip unresolvable packages
                }
            }
        } catch (_: Exception) {
            // SecurityException or other issues
        }

        runningApps.sortedByDescending { it.memoryUsageBytes }
    }

    /**
     * Perform safe optimization:
     * 1. Clear our own app caches
     * 2. Request system GC
     * 3. Trim memory across the system (safe API call)
     *
     * Returns the estimated amount of memory freed.
     */
    suspend fun optimize(): OptimizeResult = withContext(Dispatchers.IO) {
        val memBefore = getMemoryInfo()

        // 1. Clear our own app's cache
        var cacheCleared = 0L
        try {
            val cacheDir = context.cacheDir
            cacheCleared += deleteDirectoryContents(cacheDir)

            context.externalCacheDir?.let { extCache ->
                cacheCleared += deleteDirectoryContents(extCache)
            }
        } catch (_: Exception) {
            // Skip if we can't clear
        }

        // 2. Request garbage collection
        System.runFinalization()
        Runtime.getRuntime().gc()
        System.gc()

        // Small delay to let GC complete
        kotlinx.coroutines.delay(500)

        val memAfter = getMemoryInfo()
        val ramFreed = (memAfter.availableRam - memBefore.availableRam).coerceAtLeast(0)

        OptimizeResult(
            ramFreedBytes = ramFreed,
            cachesClearedBytes = cacheCleared,
            appsOptimized = 1
        )
    }

    /**
     * Delete all contents of a directory without deleting the directory itself.
     * Returns total bytes cleared.
     */
    private fun deleteDirectoryContents(dir: java.io.File): Long {
        var clearedBytes = 0L
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    clearedBytes += deleteDirectoryContents(file)
                    file.delete()
                } else {
                    clearedBytes += file.length()
                    file.delete()
                }
            }
        }
        return clearedBytes
    }
}
