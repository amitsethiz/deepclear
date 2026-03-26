package com.deepclear.app.data.repository

import com.deepclear.app.data.model.JunkCategory
import com.deepclear.app.data.model.ScanCategory
import com.deepclear.app.data.model.ScanResult
import com.deepclear.app.data.model.ScannedFile
import com.deepclear.app.data.scanner.DeviceScanner
import com.deepclear.app.data.scanner.ScanProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that coordinates scanning and groups results into categories.
 */
@Singleton
class ScanRepository @Inject constructor(
    private val deviceScanner: DeviceScanner
) {
    /**
     * Starts a scan and returns a Flow of ScanProgress.
     * The final emission (isComplete = true) contains all scanned files.
     */
    fun startScan(): Flow<ScanProgress> = deviceScanner.scan()

    /**
     * Groups a flat list of scanned files into categorized ScanResult.
     */
    fun groupByCategory(files: List<ScannedFile>): ScanResult {
        val grouped = files.groupBy { it.category }
        val categories = grouped.map { (category, categoryFiles) ->
            ScanCategory(
                category = category,
                files = categoryFiles.sortedByDescending { it.sizeBytes },
                isExpanded = false,
                isAllSelected = categoryFiles.all { it.isSelected }
            )
        }.sortedByDescending { it.totalSize }

        return ScanResult(categories = categories)
    }

    /**
     * Deletes the given files from the device.
     * Returns the count of successfully deleted files.
     */
    suspend fun deleteFiles(files: List<ScannedFile>): Int {
        var deletedCount = 0
        files.forEach { scannedFile ->
            try {
                val file = java.io.File(scannedFile.path)
                if (file.exists() && file.canWrite()) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            } catch (_: SecurityException) {
                // Skip files we can't delete
            }
        }
        return deletedCount
    }
}
