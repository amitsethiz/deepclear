package com.deepclear.app.data.scanner

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Info about a browser's cache on the device.
 */
data class BrowserCacheInfo(
    val packageName: String,
    val appName: String,
    val cacheSize: Long,
    val dataDirPath: String?,
    val isSelected: Boolean = true
)

/**
 * Progress during browser cache clearing.
 */
data class BrowserCleanProgress(
    val currentBrowser: String = "",
    val browsersProcessed: Int = 0,
    val totalBrowsers: Int = 0,
    val totalClearedBytes: Long = 0L,
    val isComplete: Boolean = false,
    val browsers: List<BrowserCacheInfo> = emptyList()
)

/**
 * Detects installed browsers, reads their cache sizes, and clears cache
 * using MANAGE_EXTERNAL_STORAGE for direct file access on Android 11+.
 *
 * Supported browsers: Chrome, Firefox, Edge, Brave, Opera, Samsung Internet,
 * DuckDuckGo, Vivaldi, and more.
 */
@Singleton
class BrowserCacheCleaner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Known browser package names
    private val knownBrowserPackages = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",            // Firefox Nightly
        "com.microsoft.emmx",           // Edge
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.gx",
        "com.sec.android.app.sbrowser", // Samsung Internet
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "org.chromium.chrome",
        "com.UCMobile.intl",            // UC Browser
        "com.yandex.browser",
        "com.cloudflare.onedotonedotonedotone", // 1.1.1.1
    )

    /**
     * Scan for installed browsers and estimate their cache sizes.
     */
    fun scanBrowserCaches(): Flow<BrowserCleanProgress> = flow {
        emit(BrowserCleanProgress())

        val pm = context.packageManager
        val browsers = mutableListOf<BrowserCacheInfo>()

        knownBrowserPackages.forEach { packageName ->
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val cacheSize = estimateCacheSize(packageName, appInfo)
                val dataDir = appInfo.dataDir

                if (cacheSize > 0) {
                    browsers.add(
                        BrowserCacheInfo(
                            packageName = packageName,
                            appName = appName,
                            cacheSize = cacheSize,
                            dataDirPath = dataDir
                        )
                    )
                }
            } catch (_: PackageManager.NameNotFoundException) {
                // Browser not installed — skip
            } catch (_: Exception) {
                // Skip on any error
            }
        }

        emit(
            BrowserCleanProgress(
                totalBrowsers = browsers.size,
                isComplete = true,
                browsers = browsers.sortedByDescending { it.cacheSize }
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Estimate cache size for an app using StorageStatsManager (API 26+)
     * and direct file access fallback.
     */
    private fun estimateCacheSize(packageName: String, appInfo: ApplicationInfo): Long {
        var totalCache = 0L

        // Method 1: StorageStatsManager (accurate, API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val uuid = storageManager.getUuidForPath(Environment.getDataDirectory())
                val stats = storageStatsManager.queryStatsForPackage(
                    uuid,
                    packageName,
                    android.os.Process.myUserHandle()
                )
                totalCache += stats.cacheBytes
            } catch (_: Exception) {
                // Fallback to file scanning
            }
        }

        // Method 2: Direct file scanning (requires MANAGE_EXTERNAL_STORAGE)
        if (totalCache == 0L) {
            totalCache = scanExternalCacheDir(packageName)
        }

        return totalCache
    }

    /**
     * Scan the external Android/data/{package}/cache directory.
     */
    private fun scanExternalCacheDir(packageName: String): Long {
        var size = 0L
        val externalStorage = Environment.getExternalStorageDirectory()

        // Android/data/{package}/cache
        val cacheDir = File(externalStorage, "Android/data/$packageName/cache")
        if (cacheDir.exists() && cacheDir.canRead()) {
            size += calculateDirSize(cacheDir)
        }

        // Android/data/{package}/code_cache
        val codeCacheDir = File(externalStorage, "Android/data/$packageName/code_cache")
        if (codeCacheDir.exists() && codeCacheDir.canRead()) {
            size += calculateDirSize(codeCacheDir)
        }

        // Some browsers store in Android/data/{package}/files/cache
        val filesCacheDir = File(externalStorage, "Android/data/$packageName/files/cache")
        if (filesCacheDir.exists() && filesCacheDir.canRead()) {
            size += calculateDirSize(filesCacheDir)
        }

        return size
    }

    /**
     * Clear cache for selected browsers.
     */
    fun clearBrowserCaches(browsers: List<BrowserCacheInfo>): Flow<BrowserCleanProgress> = flow {
        val selectedBrowsers = browsers.filter { it.isSelected }
        val totalBrowsers = selectedBrowsers.size
        var processedCount = 0
        var totalCleared = 0L

        selectedBrowsers.forEach { browser ->
            emit(
                BrowserCleanProgress(
                    currentBrowser = browser.appName,
                    browsersProcessed = processedCount,
                    totalBrowsers = totalBrowsers,
                    totalClearedBytes = totalCleared
                )
            )

            val cleared = clearCacheForPackage(browser.packageName)
            totalCleared += cleared
            processedCount++
        }

        emit(
            BrowserCleanProgress(
                browsersProcessed = totalBrowsers,
                totalBrowsers = totalBrowsers,
                totalClearedBytes = totalCleared,
                isComplete = true
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Clear cache for a specific browser package.
     * Uses direct file deletion via MANAGE_EXTERNAL_STORAGE.
     */
    private fun clearCacheForPackage(packageName: String): Long {
        var totalCleared = 0L
        val externalStorage = Environment.getExternalStorageDirectory()

        // Clear Android/data/{package}/cache
        val cacheDirs = listOf(
            File(externalStorage, "Android/data/$packageName/cache"),
            File(externalStorage, "Android/data/$packageName/code_cache"),
            File(externalStorage, "Android/data/$packageName/files/cache"),
            File(externalStorage, "Android/data/$packageName/app_webview/Cache"),
            File(externalStorage, "Android/data/$packageName/app_webview/Code Cache"),
            File(externalStorage, "Android/data/$packageName/app_chrome/Default/Cache"),
            File(externalStorage, "Android/data/$packageName/app_chrome/Default/Code Cache"),
        )

        cacheDirs.forEach { dir ->
            if (dir.exists() && dir.canWrite()) {
                totalCleared += deleteDirectoryContents(dir)
            }
        }

        return totalCleared
    }

    /**
     * Recursively delete all contents of a directory.
     */
    private fun deleteDirectoryContents(dir: File): Long {
        var cleared = 0L
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    cleared += deleteDirectoryContents(file)
                    file.delete()
                } else {
                    cleared += file.length()
                    file.delete()
                }
            }
        } catch (_: SecurityException) {
            // Skip if no access
        } catch (_: Exception) {
            // Skip
        }
        return cleared
    }

    /**
     * Calculate total size of a directory recursively.
     */
    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        try {
            dir.walkTopDown().forEach { file ->
                if (file.isFile) size += file.length()
            }
        } catch (_: Exception) {
            // Skip
        }
        return size
    }
}
