package com.apkm.installer.domain.usecase

import com.apkm.installer.data.SplitApkInstaller
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.domain.model.InstallState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import android.os.SystemClock
import android.util.Log
import javax.inject.Inject

private const val TAG = "InstallUseCase"
// 3 minutes: Play Protect cloud scan on slow GMS emulators can take 90–120 s.
// SessionCallback.onFinished() is the primary completion signal; this timeout is a
// last resort so the UI never hangs indefinitely.
private const val INSTALL_TIMEOUT_MS = 3 * 60 * 1000L

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

        // Receive states until a terminal one arrives.
        // IMPORTANT: withTimeout throws TimeoutCancellationException which is a CancellationException.
        // Coroutines treat CancellationException as silent cancellation — the ViewModel's launch
        // block swallows it and the UI freezes on whatever state it last showed (typically Finalizing).
        // We must catch it explicitly and emit a Failure so the UI always reaches a terminal state.
        try {
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
        } catch (e: TimeoutCancellationException) {
            val msg = "Installation timed out after ${elapsed() / 1000}s. " +
                "Try disabling Play Protect: Google Play → Profile → Play Protect → turn off."
            Log.e(TAG, "✖ $msg")
            emit(InstallState.Failure(msg))
        }
    }.flowOn(Dispatchers.IO)
}
