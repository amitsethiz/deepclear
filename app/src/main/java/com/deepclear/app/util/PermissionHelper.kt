package com.deepclear.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Handles storage permission checks for Android 11+ (MANAGE_EXTERNAL_STORAGE)
 * and older versions (READ/WRITE_EXTERNAL_STORAGE).
 */
object PermissionHelper {

    /**
     * Check whether we have full storage access.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // On Android 10 and below, READ_EXTERNAL_STORAGE is sufficient
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Build an Intent that takes the user to the "All Files Access" settings page.
     * Used for Android 11+ (API 30+).
     */
    fun createManageStorageIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    /**
     * The legacy permission string needed on Android 10 and below.
     */
    fun getLegacyPermission(): String = android.Manifest.permission.READ_EXTERNAL_STORAGE
}
