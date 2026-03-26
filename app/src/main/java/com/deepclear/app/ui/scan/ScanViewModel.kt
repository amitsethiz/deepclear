package com.deepclear.app.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepclear.app.data.model.ScanCategory
import com.deepclear.app.data.model.ScanResult
import com.deepclear.app.data.model.ScannedFile
import com.deepclear.app.data.repository.ScanRepository
import com.deepclear.app.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val hasPermission: Boolean = false,
    val isScanning: Boolean = false,
    val scanProgress: String = "",
    val filesFound: Int = 0,
    val totalSizeFound: Long = 0L,
    val scanResult: ScanResult = ScanResult(),
    val isDeleting: Boolean = false,
    val deletedCount: Int = 0,
    val showDeleteConfirmation: Boolean = false,
    val scanComplete: Boolean = false
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        checkPermission()
    }

    fun checkPermission() {
        _uiState.value = _uiState.value.copy(
            hasPermission = PermissionHelper.hasStoragePermission(getApplication())
        )
    }

    fun startScan() {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                scanProgress = "Starting scan...",
                filesFound = 0,
                totalSizeFound = 0L,
                scanComplete = false,
                scanResult = ScanResult()
            )

            scanRepository.startScan().collect { progress ->
                if (progress.isComplete) {
                    val result = scanRepository.groupByCategory(progress.scannedFiles)
                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        scanProgress = "Scan complete!",
                        filesFound = progress.filesFound,
                        totalSizeFound = progress.totalSizeBytes,
                        scanResult = result,
                        scanComplete = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        scanProgress = "Scanning: ${progress.currentCategory}",
                        filesFound = progress.filesFound,
                        totalSizeFound = progress.totalSizeBytes
                    )
                }
            }
        }
    }

    fun toggleCategoryExpanded(categoryIndex: Int) {
        val current = _uiState.value.scanResult
        val updatedCategories = current.categories.toMutableList()
        if (categoryIndex in updatedCategories.indices) {
            val cat = updatedCategories[categoryIndex]
            updatedCategories[categoryIndex] = cat.copy(isExpanded = !cat.isExpanded)
            _uiState.value = _uiState.value.copy(
                scanResult = current.copy(categories = updatedCategories)
            )
        }
    }

    fun toggleCategorySelection(categoryIndex: Int) {
        val current = _uiState.value.scanResult
        val updatedCategories = current.categories.toMutableList()
        if (categoryIndex in updatedCategories.indices) {
            val cat = updatedCategories[categoryIndex]
            val newSelected = !cat.isAllSelected
            val updatedFiles = cat.files.map { it.copy(isSelected = newSelected) }
            updatedCategories[categoryIndex] = cat.copy(
                files = updatedFiles,
                isAllSelected = newSelected
            )
            _uiState.value = _uiState.value.copy(
                scanResult = current.copy(categories = updatedCategories)
            )
        }
    }

    fun toggleFileSelection(categoryIndex: Int, fileIndex: Int) {
        val current = _uiState.value.scanResult
        val updatedCategories = current.categories.toMutableList()
        if (categoryIndex in updatedCategories.indices) {
            val cat = updatedCategories[categoryIndex]
            val updatedFiles = cat.files.toMutableList()
            if (fileIndex in updatedFiles.indices) {
                val file = updatedFiles[fileIndex]
                updatedFiles[fileIndex] = file.copy(isSelected = !file.isSelected)
                updatedCategories[categoryIndex] = cat.copy(
                    files = updatedFiles,
                    isAllSelected = updatedFiles.all { it.isSelected }
                )
                _uiState.value = _uiState.value.copy(
                    scanResult = current.copy(categories = updatedCategories)
                )
            }
        }
    }

    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = true)
    }

    fun dismissDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = false)
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDeleting = true,
                showDeleteConfirmation = false
            )

            val selectedFiles = _uiState.value.scanResult.categories
                .flatMap { it.files }
                .filter { it.isSelected }

            val deletedCount = scanRepository.deleteFiles(selectedFiles)

            // Re-scan to update the results
            _uiState.value = _uiState.value.copy(
                isDeleting = false,
                deletedCount = deletedCount
            )

            // Start a fresh scan to show updated state
            startScan()
        }
    }
}
