package com.apkm.installer.presentation.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class HomeUiState(
    val pendingUri: Uri? = null,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState

        fun onFilePicked(uri: Uri) {
            _uiState.value = HomeUiState(pendingUri = uri)
        }

        fun onNavigatedToDetail() {
            _uiState.value = HomeUiState()
        }

        fun onError(message: String) {
            _uiState.value = HomeUiState(error = message)
        }

        fun clearError() {
            _uiState.value = _uiState.value.copy(error = null)
        }
    }
