package com.deepclear.app.data.scanner

import android.os.Environment
import com.deepclear.app.data.model.JunkCategory
import com.deepclear.app.data.model.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class LargeFileScanProgress(
    val filesScanned: Int = 0,
    val largeFilesFound: Int = 0,
    val isComplete: Boolean = false,
    val files: List<ScannedFile> = emptyList()
)

/**
 * Scans for large files (> threshold) across user-accessible storage.
 * Default threshold: 50MB.
 */
@Singleton
class LargeFileScanner @Inject constructor() {

    private val defaultThresholdBytes = 50L * 1024L * 1024L // 50MB

    fun scan(thresholdBytes: Long = defaultThresholdBytes): Flow<LargeFileScanProgress> = flow {
        val largeFiles = mutableListOf<ScannedFile>()
        var filesScanned = 0

        emit(LargeFileScanProgress())

        val searchDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStorageDirectory(),
        )

        // Track visited paths to avoid duplicates from overlapping dirs
        val visited = mutableSetOf<String>()

        searchDirs.forEach { dir ->
            if (dir.exists() && dir.canRead()) {
                try {
                    dir.walkTopDown().maxDepth(6).forEach { file ->
                        if (file.isFile && file.canRead() && file.absolutePath !in visited) {
                            visited.add(file.absolutePath)
                            filesScanned++

                            if (file.length() >= thresholdBytes) {
                                largeFiles.add(
                                    ScannedFile(
                                        path = file.absolutePath,
                                        name = file.name,
                                        sizeBytes = file.length(),
                                        category = JunkCategory.LARGE_FILES,
                                        lastModified = file.lastModified(),
                                        isSelected = false // Default unselected for safety
                                    )
                                )

                                emit(
                                    LargeFileScanProgress(
                                        filesScanned = filesScanned,
                                        largeFilesFound = largeFiles.size
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Skip on error
                }
            }
        }

        emit(
            LargeFileScanProgress(
                filesScanned = filesScanned,
                largeFilesFound = largeFiles.size,
                isComplete = true,
                files = largeFiles.sortedByDescending { it.sizeBytes }
            )
        )
    }.flowOn(Dispatchers.IO)
}
