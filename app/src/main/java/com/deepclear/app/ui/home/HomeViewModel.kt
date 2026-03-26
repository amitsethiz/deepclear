package com.deepclear.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepclear.app.data.model.StorageInfo
import com.deepclear.app.data.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val storageInfo: StorageInfo = StorageInfo(),
    val isLoading: Boolean = true,
    val isScanning: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadStorageInfo()
    }

    fun loadStorageInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val storageInfo = storageRepository.getStorageInfo()
            _uiState.value = _uiState.value.copy(
                storageInfo = storageInfo,
                isLoading = false
            )
        }
    }

    fun onScanClicked() {
        _uiState.value = _uiState.value.copy(isScanning = true)
    }

    fun onScanNavigated() {
        _uiState.value = _uiState.value.copy(isScanning = false)
    }
}
