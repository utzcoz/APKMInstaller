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
// On timeout we abandon the PackageInstaller session to force a system callback.
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

        emit(InstallState.Verifying)
        val missing = info.apkFiles.filter { !java.io.File(it).exists() }
        if (missing.isNotEmpty()) {
            Log.e(TAG, "  [${elapsed()}ms] Missing APK files: $missing")
            emit(InstallState.Failure("APK files missing: ${missing.joinToString()}"))
            return@flow
        }
        Log.d(TAG, "  [${elapsed()}ms] All APK files present, starting session")

        emit(InstallState.Installing)

        // install() creates a fresh per-session channel and returns it.
        // Using per-session channels eliminates the stale-state bug where a terminal
        // result from a previous install was consumed by this install's receive loop.
        val channel = installer.install(info.packageName, info.apkFiles)
        Log.i(TAG, "  [${elapsed()}ms] install() returned — draining session channel")

        // IMPORTANT: withTimeout throws TimeoutCancellationException (a CancellationException).
        // Kotlin silently swallows CancellationException in launch{} — we must catch it here
        // and emit a Failure so the UI always reaches a terminal state.
        // On timeout we also abandon the PackageInstaller session so the system stops scanning.
        try {
            withTimeout(INSTALL_TIMEOUT_MS) {
                for (state in channel) {
                    Log.i(TAG, "  [${elapsed()}ms] channel → $state")
                    emit(state)
                    if (state is InstallState.Success || state is InstallState.Failure) {
                        Log.i(TAG, "▶ pipeline END  result=$state  t=${elapsed()}ms")
                        break
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            val msg = "Installation timed out after ${elapsed() / 1000}s — " +
                "disable Play Protect in Google Play → Profile → Play Protect and retry."
            Log.e(TAG, "✖ $msg")
            installer.cancelCurrentSession()
            emit(InstallState.Failure(msg))
        }
    }.flowOn(Dispatchers.IO)
}
