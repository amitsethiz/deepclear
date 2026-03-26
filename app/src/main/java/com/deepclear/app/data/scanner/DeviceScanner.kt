package com.deepclear.app.data.scanner

import android.content.Context
import android.os.Environment
import com.deepclear.app.data.model.JunkCategory
import com.deepclear.app.data.model.ScannedFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress update emitted during scanning.
 */
data class ScanProgress(
    val currentCategory: String = "",
    val filesFound: Int = 0,
    val totalSizeBytes: Long = 0L,
    val isComplete: Boolean = false,
    val scannedFiles: List<ScannedFile> = emptyList()
)

/**
 * Core scanner that identifies junk files on the device.
 * Scans: App Cache, Temp Files, Residual APKs, and user media.
 *
 * Safety: Only scans user-accessible directories. Never touches
 * system partitions or critical app data.
 */
@Singleton
class DeviceScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // File extensions for media categorization
    private val photoExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")
    private val videoExtensions = setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp", "webm")
    private val audioExtensions = setOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "opus")
    private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv")
    private val apkExtensions = setOf("apk", "xapk", "apks")
    private val tempExtensions = setOf("tmp", "temp", "bak", "log", "old", "cache")

    /**
     * Performs a full device scan, emitting progress updates as a Flow.
     */
    fun scan(): Flow<ScanProgress> = flow {
        val allFiles = mutableListOf<ScannedFile>()
        var filesFound = 0
        var totalSize = 0L

        // 1. Scan App Cache
        emit(ScanProgress(currentCategory = "App Cache", filesFound = filesFound, totalSizeBytes = totalSize))
        val cacheFiles = scanAppCache()
        allFiles.addAll(cacheFiles)
        filesFound += cacheFiles.size
        totalSize += cacheFiles.sumOf { it.sizeBytes }
        emit(ScanProgress(currentCategory = "App Cache", filesFound = filesFound, totalSizeBytes = totalSize))

        // 2. Scan Temporary Files
        emit(ScanProgress(currentCategory = "Temporary Files", filesFound = filesFound, totalSizeBytes = totalSize))
        val tempFiles = scanTempFiles()
        allFiles.addAll(tempFiles)
        filesFound += tempFiles.size
        totalSize += tempFiles.sumOf { it.sizeBytes }
        emit(ScanProgress(currentCategory = "Temporary Files", filesFound = filesFound, totalSizeBytes = totalSize))

        // 3. Scan Residual APKs
        emit(ScanProgress(currentCategory = "Residual APKs", filesFound = filesFound, totalSizeBytes = totalSize))
        val apkFiles = scanResidualApks()
        allFiles.addAll(apkFiles)
        filesFound += apkFiles.size
        totalSize += apkFiles.sumOf { it.sizeBytes }
        emit(ScanProgress(currentCategory = "Residual APKs", filesFound = filesFound, totalSizeBytes = totalSize))

        // 4. Scan Media Files (large or potentially unwanted)
        emit(ScanProgress(currentCategory = "Media Files", filesFound = filesFound, totalSizeBytes = totalSize))
        val mediaFiles = scanMediaFiles()
        allFiles.addAll(mediaFiles)
        filesFound += mediaFiles.size
        totalSize += mediaFiles.sumOf { it.sizeBytes }

        // 5. Complete
        emit(
            ScanProgress(
                currentCategory = "Complete",
                filesFound = filesFound,
                totalSizeBytes = totalSize,
                isComplete = true,
                scannedFiles = allFiles
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Scans the app's own cache directory and external cache.
     */
    private fun scanAppCache(): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()

        // Internal cache
        context.cacheDir?.let { cacheDir ->
            scanDirectory(cacheDir, JunkCategory.APP_CACHE, results)
        }

        // External cache
        context.externalCacheDir?.let { extCacheDir ->
            scanDirectory(extCacheDir, JunkCategory.APP_CACHE, results)
        }

        // Other apps' cache dirs (accessible on older Android or with MANAGE_EXTERNAL_STORAGE)
        val externalStorage = Environment.getExternalStorageDirectory()
        val androidDataDir = File(externalStorage, "Android/data")
        if (androidDataDir.exists() && androidDataDir.canRead()) {
            androidDataDir.listFiles()?.forEach { appDir ->
                val appCacheDir = File(appDir, "cache")
                if (appCacheDir.exists() && appCacheDir.canRead()) {
                    scanDirectory(appCacheDir, JunkCategory.APP_CACHE, results)
                }
            }
        }

        return results
    }

    /**
     * Scans for temporary files across accessible storage.
     */
    private fun scanTempFiles(): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()
        val externalStorage = Environment.getExternalStorageDirectory()

        if (externalStorage.exists() && externalStorage.canRead()) {
            scanDirectoryByExtension(externalStorage, tempExtensions, JunkCategory.TEMP_FILES, results, maxDepth = 5)
        }

        // System tmp directories
        val tmpDir = File(externalStorage, "tmp")
        if (tmpDir.exists()) scanDirectory(tmpDir, JunkCategory.TEMP_FILES, results)

        val tempDir = File(externalStorage, "temp")
        if (tempDir.exists()) scanDirectory(tempDir, JunkCategory.TEMP_FILES, results)

        return results
    }

    /**
     * Finds residual APK files in common download/storage locations.
     */
    private fun scanResidualApks(): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()
        val searchDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStorageDirectory(),
        )

        searchDirs.forEach { dir ->
            if (dir.exists() && dir.canRead()) {
                scanDirectoryByExtension(dir, apkExtensions, JunkCategory.RESIDUAL_APKS, results, maxDepth = 3)
            }
        }

        return results
    }

    /**
     * Scans for media files, categorizing by type.
     */
    private fun scanMediaFiles(): List<ScannedFile> {
        val results = mutableListOf<ScannedFile>()

        val mediaDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        )

        mediaDirs.forEach { dir ->
            if (dir.exists() && dir.canRead()) {
                dir.walkTopDown().maxDepth(5).forEach { file ->
                    if (file.isFile && file.canRead() && file.length() > 0) {
                        val ext = file.extension.lowercase()
                        val category = when {
                            ext in photoExtensions -> JunkCategory.PHOTOS
                            ext in videoExtensions -> JunkCategory.VIDEOS
                            ext in audioExtensions -> JunkCategory.AUDIO
                            ext in documentExtensions -> JunkCategory.DOCUMENTS
                            else -> null
                        }
                        if (category != null) {
                            results.add(
                                ScannedFile(
                                    path = file.absolutePath,
                                    name = file.name,
                                    sizeBytes = file.length(),
                                    category = category,
                                    lastModified = file.lastModified(),
                                    isSelected = false // Media files default to unselected for safety
                                )
                            )
                        }
                    }
                }
            }
        }

        return results
    }

    /**
     * Recursively scan a directory, adding all files to results.
     */
    private fun scanDirectory(
        dir: File,
        category: JunkCategory,
        results: MutableList<ScannedFile>,
        maxDepth: Int = 10
    ) {
        try {
            dir.walkTopDown().maxDepth(maxDepth).forEach { file ->
                if (file.isFile && file.canRead() && file.length() > 0) {
                    results.add(
                        ScannedFile(
                            path = file.absolutePath,
                            name = file.name,
                            sizeBytes = file.length(),
                            category = category,
                            lastModified = file.lastModified()
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Skip directories we can't access
        } catch (_: Exception) {
            // Skip on any other error
        }
    }

    /**
     * Scan a directory for files matching specific extensions.
     */
    private fun scanDirectoryByExtension(
        dir: File,
        extensions: Set<String>,
        category: JunkCategory,
        results: MutableList<ScannedFile>,
        maxDepth: Int = 5
    ) {
        try {
            dir.walkTopDown().maxDepth(maxDepth).forEach { file ->
                if (file.isFile && file.canRead() && file.length() > 0) {
                    if (file.extension.lowercase() in extensions) {
                        results.add(
                            ScannedFile(
                                path = file.absolutePath,
                                name = file.name,
                                sizeBytes = file.length(),
                                category = category,
                                lastModified = file.lastModified()
                            )
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            // Skip
        } catch (_: Exception) {
            // Skip
        }
    }
}
