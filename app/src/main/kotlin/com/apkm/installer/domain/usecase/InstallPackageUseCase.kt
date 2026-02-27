package com.apkm.installer.domain.usecase

import com.apkm.installer.data.SplitApkInstaller
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.domain.model.InstallState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import android.os.SystemClock
import android.util.Log
import javax.inject.Inject

private const val TAG = "InstallUseCase"
private const val INSTALL_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

/**
 * Drives the full installation pipeline: Extracting → Verifying → Installing → result.
 */
class InstallPackageUseCase @Inject constructor(
    private val installer: SplitApkInstaller,
) {
    operator fun invoke(info: ApkmPackageInfo): Flow<InstallState> = flow {
        val t0 = SystemClock.elapsedRealtime()
        fun elapsed() = SystemClock.elapsedRealtime() - t0

        Log.i(TAG, "▶ pipeline START  pkg=${info.packageName}  splits=${info.apkFiles.size}")
        emit(InstallState.Extracting)

        Log.d(TAG, "  [${elapsed()}ms] Verifying APK files on disk")
        emit(InstallState.Verifying)
        val missing = info.apkFiles.filter { !java.io.File(it).exists() }
        if (missing.isNotEmpty()) {
            Log.e(TAG, "  [${elapsed()}ms] Missing APK files: $missing")
            emit(InstallState.Failure("APK files missing: ${missing.joinToString()}"))
            return@flow
        }
        Log.d(TAG, "  [${elapsed()}ms] All APK files present, starting PackageInstaller session")

        emit(InstallState.Installing)
        installer.install(info.packageName, info.apkFiles)
        Log.i(TAG, "  [${elapsed()}ms] install() returned — waiting for system callbacks")

        // Receive states until a terminal one arrives.  PendingUserAction may appear first when
        // the system (e.g. Play Protect on GMS emulators) requires user confirmation before the
        // install completes; we surface it in the UI instead of keeping an indefinite spinner.
        withTimeout(INSTALL_TIMEOUT_MS) {
            while (true) {
                val state = installer.resultChannel.receive()
                Log.i(TAG, "  [${elapsed()}ms] state → $state")
                emit(state)
                if (state is InstallState.Success || state is InstallState.Failure) {
                    Log.i(TAG, "▶ pipeline END  result=$state  totalElapsed=${elapsed()}ms")
                    break
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
