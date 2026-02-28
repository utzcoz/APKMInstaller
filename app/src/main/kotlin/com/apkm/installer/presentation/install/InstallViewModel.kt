package com.apkm.installer.presentation.install

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkm.installer.data.SplitApkInstaller
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.domain.model.InstallState
import com.apkm.installer.domain.usecase.InstallPackageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstallViewModel @Inject constructor(
    private val installPackageUseCase: InstallPackageUseCase,
    private val installer: SplitApkInstaller,
) : ViewModel() {

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState

    private var installJob: Job? = null

    fun install(info: ApkmPackageInfo) {
        if (_installState.value != InstallState.Idle) return
        installJob = viewModelScope.launch {
            installPackageUseCase(info).collect { state ->
                _installState.value = state
            }
        }
    }

    fun cancel() {
        installJob?.cancel()
        installer.cancelCurrentSession()
        _installState.value = InstallState.Idle
    }

    fun reset() {
        _installState.value = InstallState.Idle
    }
}
