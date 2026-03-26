package com.deepclear.app.ui.optimizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepclear.app.data.model.MemoryInfo
import com.deepclear.app.data.model.OptimizeResult
import com.deepclear.app.data.model.RunningApp
import com.deepclear.app.data.repository.PerformanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OptimizerUiState(
    val memoryInfo: MemoryInfo = MemoryInfo(),
    val runningApps: List<RunningApp> = emptyList(),
    val isLoading: Boolean = true,
    val isOptimizing: Boolean = false,
    val optimizeResult: OptimizeResult? = null,
    val showResult: Boolean = false
)

@HiltViewModel
class OptimizerViewModel @Inject constructor(
    private val performanceRepository: PerformanceRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OptimizerUiState())
    val uiState: StateFlow<OptimizerUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val memInfo = performanceRepository.getMemoryInfo()
            val apps = performanceRepository.getRunningApps()

            _uiState.value = _uiState.value.copy(
                memoryInfo = memInfo,
                runningApps = apps,
                isLoading = false
            )
        }
    }

    fun optimize() {
        if (_uiState.value.isOptimizing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOptimizing = true, showResult = false)

            val result = performanceRepository.optimize()
            val updatedMemInfo = performanceRepository.getMemoryInfo()
            val updatedApps = performanceRepository.getRunningApps()

            _uiState.value = _uiState.value.copy(
                isOptimizing = false,
                optimizeResult = result,
                showResult = true,
                memoryInfo = updatedMemInfo,
                runningApps = updatedApps
            )
        }
    }

    fun dismissResult() {
        _uiState.value = _uiState.value.copy(showResult = false)
    }
}
