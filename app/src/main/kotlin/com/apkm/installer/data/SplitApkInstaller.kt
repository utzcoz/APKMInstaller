package com.apkm.installer.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.apkm.installer.domain.model.InstallState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SplitApkInstaller"
internal const val INSTALL_ACTION = "com.apkm.installer.INSTALL_RESULT"
internal const val EXTRA_PACKAGE_NAME = "package_name"
internal const val EXTRA_STATUS = "status"
internal const val EXTRA_MESSAGE = "message"

/**
 * Installs one or more APKs (split-APK set) using the system [PackageInstaller] API.
 *
 * Results are delivered through [results] channel which the use-case layer subscribes to.
 */
@Singleton
class SplitApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Receives install status from [InstallResultReceiver] via [resultChannel]. */
    internal val resultChannel = Channel<InstallState>(capacity = Channel.BUFFERED)

    /**
     * Installs the APKs at [apkPaths] as a single split-APK session.
     * Emits [InstallState.Installing] and then one terminal state via [resultChannel].
     */
    suspend fun install(packageName: String, apkPaths: List<String>): Unit {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)

        val sessionId = installer.createSession(params)
        Log.i(TAG, "▶ install START  pkg=$packageName  apks=${apkPaths.size}  session=$sessionId")
        val wallStart = SystemClock.elapsedRealtime()

        // SessionCallback fires on the main thread with live progress updates and, crucially,
        // onFinished() — a reliable fallback when the PendingIntent broadcast is delayed by
        // Play Protect scanning (which can hold the broadcast for 30+ seconds on GMS emulators).
        val sessionCallback = object : PackageInstaller.SessionCallback() {
            override fun onCreated(sId: Int) {}
            override fun onBadgingChanged(sId: Int) {}
            override fun onActiveChanged(sId: Int, active: Boolean) {
                if (sId == sessionId) Log.d(TAG, "  [SessionCallback] active=$active  elapsed=${SystemClock.elapsedRealtime() - wallStart}ms")
            }
            override fun onProgressChanged(sId: Int, progress: Float) {
                if (sId == sessionId) Log.i(TAG, "  [SessionCallback] progress=${"%.0f".format(progress * 100)}%  elapsed=${SystemClock.elapsedRealtime() - wallStart}ms")
            }
            override fun onFinished(sId: Int, success: Boolean) {
                if (sId != sessionId) return
                val elapsed = SystemClock.elapsedRealtime() - wallStart
                Log.i(TAG, "  [SessionCallback] onFinished  success=$success  elapsed=${elapsed}ms")
                // Unregister immediately to avoid leaking the callback.
                installer.unregisterSessionCallback(this)
                // Always push a result — the use-case loop breaks on the first *terminal* state
                // (Success/Failure), so a duplicate terminal from onFinished() is harmless.
                // Do NOT gate on resultChannel.isEmpty: the channel may still hold the non-terminal
                // Finalizing state, causing isEmpty=false and this fallback to be silently skipped
                // when the PendingIntent broadcast was actually dropped (rare but real on GMS emulators).
                val pkg = installer.getSessionInfo(sId)?.appPackageName ?: packageName
                val state = if (success) InstallState.Success(pkg)
                else InstallState.Failure("Installation failed (onFinished success=false, elapsed=${elapsed}ms)")
                Log.w(TAG, "  [SessionCallback] pushing result from SessionCallback  state=$state")
                resultChannel.trySend(state)
            }
        }
        installer.registerSessionCallback(sessionCallback, Handler(Looper.getMainLooper()))

        try {
            installer.openSession(sessionId).use { session ->
                apkPaths.forEachIndexed { index, path ->
                    val file = File(path)
                    val sizeMb = "%.2f".format(file.length() / 1_048_576.0)
                    Log.d(TAG, "  writing split_$index.apk  path=$path  size=${sizeMb}MB")
                    val writeStart = SystemClock.elapsedRealtime()
                    FileInputStream(file).use { input ->
                        session.openWrite("split_$index.apk", 0, file.length()).use { out ->
                            input.copyTo(out, bufferSize = 1024 * 1024)
                            session.fsync(out)
                        }
                    }
                    Log.d(TAG, "  split_$index.apk written in ${SystemClock.elapsedRealtime() - writeStart}ms")
                }

                val intent = Intent(INSTALL_ACTION).apply {
                    setPackage(context.packageName)
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                Log.i(TAG, "  committing session $sessionId  elapsed=${SystemClock.elapsedRealtime() - wallStart}ms")
                session.commit(pendingIntent.intentSender)
                Log.i(TAG, "  session committed  elapsed=${SystemClock.elapsedRealtime() - wallStart}ms  (Play Protect scan starts now)")
                // Signal the UI that streaming is done and the system is now verifying
                // (Play Protect cloud scan on GMS devices can take 30+ seconds here).
                resultChannel.trySend(InstallState.Finalizing)
            }
        } catch (e: Exception) {
            Log.e(TAG, "✖ session $sessionId failed after ${SystemClock.elapsedRealtime() - wallStart}ms: ${e.message}", e)
            installer.unregisterSessionCallback(sessionCallback)
            installer.abandonSession(sessionId)
            resultChannel.send(InstallState.Failure(e.message ?: "Unknown error"))
        }
    }
}

/**
 * BroadcastReceiver wired in AndroidManifest that receives callbacks from the system
 * PackageInstaller and forwards them to [SplitApkInstaller.resultChannel].
 *
 * Hilt cannot inject into static receivers registered via manifest, so we obtain the
 * installer through the application-level Hilt component.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != INSTALL_ACTION) return

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: ""
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        val statusName = when (status) {
            PackageInstaller.STATUS_SUCCESS -> "SUCCESS"
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "PENDING_USER_ACTION"
            PackageInstaller.STATUS_FAILURE -> "FAILURE"
            PackageInstaller.STATUS_FAILURE_ABORTED -> "FAILURE_ABORTED"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "FAILURE_BLOCKED"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "FAILURE_CONFLICT"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "FAILURE_INCOMPATIBLE"
            PackageInstaller.STATUS_FAILURE_INVALID -> "FAILURE_INVALID"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "FAILURE_STORAGE"
            else -> "UNKNOWN($status)"
        }
        Log.i(TAG, "◀ receiver  status=$statusName  pkg=$packageName  msg=${message.ifBlank { "(none)" }}")

        val installer = (context.applicationContext as? com.apkm.installer.ApkMInstallerApp)
            ?.splitApkInstaller ?: return

        val state = when (status) {
            PackageInstaller.STATUS_SUCCESS -> InstallState.Success(packageName)
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // System needs the user to confirm (e.g. Play Protect verification on GMS emulators).
                // Update the UI to PendingUserAction, then launch the confirmation activity.
                // The receiver will be called again with STATUS_SUCCESS / STATUS_FAILURE after
                // the user responds.
                installer.resultChannel.trySend(InstallState.PendingUserAction)
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirmIntent?.let { context.startActivity(it) }
                return
            }
            else -> InstallState.Failure(message.ifBlank { "Installation failed (status=$status)" })
        }

        installer.resultChannel.trySend(state)
    }
}
