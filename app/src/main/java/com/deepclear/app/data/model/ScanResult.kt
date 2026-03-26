package com.deepclear.app.data.model

/**
 * Represents a single scannable file item.
 */
data class ScannedFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val category: JunkCategory,
    val lastModified: Long = 0L,
    val isSelected: Boolean = true
)

/**
 * Represents a group of scanned files under a category.
 */
data class ScanCategory(
    val category: JunkCategory,
    val files: List<ScannedFile>,
    val isExpanded: Boolean = false,
    val isAllSelected: Boolean = true
) {
    val totalSize: Long
        get() = files.sumOf { it.sizeBytes }

    val selectedSize: Long
        get() = files.filter { it.isSelected }.sumOf { it.sizeBytes }

    val selectedCount: Int
        get() = files.count { it.isSelected }

    val fileCount: Int
        get() = files.size
}

/**
 * Overall scan result.
 */
data class ScanResult(
    val categories: List<ScanCategory> = emptyList(),
    val scanDurationMs: Long = 0L
) {
    val totalJunkSize: Long
        get() = categories.sumOf { it.totalSize }

    val selectedJunkSize: Long
        get() = categories.sumOf { it.selectedSize }

    val totalFileCount: Int
        get() = categories.sumOf { it.fileCount }
}
