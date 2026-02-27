package com.apkm.installer.presentation.install

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.domain.model.InstallState
import com.apkm.installer.domain.usecase.InstallPackageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstallViewModel @Inject constructor(
    private val installPackageUseCase: InstallPackageUseCase,
) : ViewModel() {

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState

    fun install(info: ApkmPackageInfo) {
        if (_installState.value != InstallState.Idle) return
        viewModelScope.launch {
            installPackageUseCase(info).collect { state ->
                _installState.value = state
            }
        }
    }

    fun reset() {
        _installState.value = InstallState.Idle
    }
}
