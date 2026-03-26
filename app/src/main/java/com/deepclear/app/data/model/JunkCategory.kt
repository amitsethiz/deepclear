package com.deepclear.app.data.model

/**
 * Base data models for scan results.
 * Will be expanded in feature/cache-scanner.
 */
enum class JunkCategory(val displayName: String) {
    APP_CACHE("App Cache"),
    TEMP_FILES("Temporary Files"),
    RESIDUAL_APKS("Residual APKs"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    TRASH("Trash Files"),
    DUPLICATES("Duplicates"),
    LARGE_FILES("Large Files"),
    EMPTY_FOLDERS("Empty Folders")
}
