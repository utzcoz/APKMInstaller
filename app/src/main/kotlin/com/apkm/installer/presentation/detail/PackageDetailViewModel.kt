package com.apkm.installer.presentation.detail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.domain.usecase.ParseApkmUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Success(val info: ApkmPackageInfo) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

@HiltViewModel
class PackageDetailViewModel @Inject constructor(
    private val parseApkmUseCase: ParseApkmUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState

    fun loadPackage(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            parseApkmUseCase(uri).fold(
                onSuccess = { info -> _uiState.value = DetailUiState.Success(info) },
                onFailure = { e -> _uiState.value = DetailUiState.Error(e.message ?: "Parse error") },
            )
        }
    }
}
