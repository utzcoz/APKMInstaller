package com.apkm.installer.domain.usecase

import com.apkm.installer.data.SplitApkInstaller
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.domain.model.InstallState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import android.os.SystemClock
import android.util.Log
import javax.inject.Inject

private const val TAG = "InstallUseCase"

/**
 * Drives the full installation pipeline: Extracting → Verifying → Installing → result.
 *
 * Uses the [PackageInstaller] session API with a dynamically-registered BroadcastReceiver.
 * [STATUS_PENDING_USER_ACTION][PackageInstaller.STATUS_PENDING_USER_ACTION] is handled by
 * launching the system confirmation Activity; a [CompletableDeferred] bridges the async
 * callback to the calling coroutine with a 5-minute timeout.
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
        Log.d(TAG, "  [${elapsed()}ms] All APK files present, starting shell install")

        emit(InstallState.Installing)
        val result = installer.install(info.packageName, info.apkFiles)
        Log.i(TAG, "▶ pipeline END  result=$result  t=${elapsed()}ms")
        emit(result)
    }.flowOn(Dispatchers.IO)
}
