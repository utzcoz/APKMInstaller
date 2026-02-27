package com.apkm.installer.domain.usecase

import com.apkm.installer.data.SplitApkInstaller
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.domain.model.InstallState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

private const val INSTALL_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

/**
 * Drives the full installation pipeline: Extracting → Verifying → Installing → result.
 */
class InstallPackageUseCase @Inject constructor(
    private val installer: SplitApkInstaller,
) {
    operator fun invoke(info: ApkmPackageInfo): Flow<InstallState> = flow {
        emit(InstallState.Extracting)

        // Verify all APK files are still present
        emit(InstallState.Verifying)
        val missing = info.apkFiles.filter { !java.io.File(it).exists() }
        if (missing.isNotEmpty()) {
            emit(InstallState.Failure("APK files missing: ${missing.joinToString()}"))
            return@flow
        }

        emit(InstallState.Installing)
        installer.install(info.packageName, info.apkFiles)

        // Wait for the result broadcast forwarded by InstallResultReceiver
        val result = withTimeout(INSTALL_TIMEOUT_MS) {
            installer.resultChannel.receive()
        }
        emit(result)
    }.flowOn(Dispatchers.IO)
}
