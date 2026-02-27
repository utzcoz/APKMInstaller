package com.apkm.installer.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
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
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SplitApkInstaller"
internal const val INSTALL_ACTION = "com.apkm.installer.INSTALL_RESULT"

/**
 * Holds state for one active PackageInstaller session.
 *
 * A fresh [ActiveSession] is created per [SplitApkInstaller.install] call.
 * Using a per-session channel eliminates the stale-state bug where a terminal
 * result from a previous install was consumed by the next install's receive loop.
 */
internal data class ActiveSession(
    val sessionId: Int,
    val packageName: String,
    val channel: Channel<InstallState> = Channel(Channel.UNLIMITED),
)

/**
 * Installs one or more APKs (split-APK set) using the system [PackageInstaller] API.
 *
 * Results are delivered via a per-session [Channel] returned by [install].
 * Call [cancelCurrentSession] to abandon the active session (user cancel / timeout).
 */
@Singleton
class SplitApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** The currently active install session. Null when no install is in progress. */
    internal val activeSession = AtomicReference<ActiveSession?>(null)

    /**
     * Starts a PackageInstaller session for [apkPaths] and returns a [Channel] that
     * receives [InstallState] updates until a terminal [InstallState.Success] or
     * [InstallState.Failure] is delivered.
     */
    suspend fun install(packageName: String, apkPaths: List<String>): Channel<InstallState> {
        val pkgInstaller = context.packageManager.packageInstaller

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)
        params.setInstallReason(PackageManager.INSTALL_REASON_USER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        val sessionId = pkgInstaller.createSession(params)
        val session = ActiveSession(sessionId, packageName)
        activeSession.set(session)

        val wallStart = SystemClock.elapsedRealtime()
        fun elapsed() = SystemClock.elapsedRealtime() - wallStart
        Log.i(TAG, "▶ install START  pkg=$packageName  apks=${apkPaths.size}  session=$sessionId")

        val sessionCallback = object : PackageInstaller.SessionCallback() {
            override fun onCreated(sId: Int) {}
            override fun onBadgingChanged(sId: Int) {}
            override fun onActiveChanged(sId: Int, active: Boolean) {
                if (sId == sessionId) Log.d(TAG, "  [CB#$sessionId] active=$active  t=${elapsed()}ms")
            }
            override fun onProgressChanged(sId: Int, progress: Float) {
                if (sId == sessionId) Log.i(TAG, "  [CB#$sessionId] progress=${"%.0f".format(progress * 100)}%  t=${elapsed()}ms")
            }
            override fun onFinished(sId: Int, success: Boolean) {
                if (sId != sessionId) {
                    Log.w(TAG, "  [CB#$sId] onFinished for WRONG session (active=$sessionId) — ignoring")
                    return
                }
                pkgInstaller.unregisterSessionCallback(this)
                val t = elapsed()
                Log.i(TAG, "  [CB#$sessionId] onFinished  success=$success  t=${t}ms")
                val current = activeSession.get()
                if (current == null || current.sessionId != sessionId) {
                    Log.w(TAG, "  [CB#$sessionId] session already cleared (cancelled?) — ignoring")
                    return
                }
                val pkg = pkgInstaller.getSessionInfo(sId)?.appPackageName ?: packageName
                val state = if (success) InstallState.Success(pkg)
                else InstallState.Failure("Installation failed (t=${t}ms)")
                Log.i(TAG, "  [CB#$sessionId] → channel.trySend($state)  isEmpty=${current.channel.isEmpty}")
                current.channel.trySend(state)
            }
        }
        pkgInstaller.registerSessionCallback(sessionCallback, Handler(Looper.getMainLooper()))
        Log.d(TAG, "  [#$sessionId] SessionCallback registered")

        try {
            pkgInstaller.openSession(sessionId).use { pkgSession ->
                apkPaths.forEachIndexed { index, path ->
                    val file = File(path)
                    Log.d(TAG, "  [#$sessionId] writing split_$index  size=${"%.2f".format(file.length() / 1_048_576.0)}MB  t=${elapsed()}ms")
                    FileInputStream(file).use { input ->
                        pkgSession.openWrite("split_$index.apk", 0, file.length()).use { out ->
                            input.copyTo(out, bufferSize = 1024 * 1024)
                            pkgSession.fsync(out)
                        }
                    }
                    Log.d(TAG, "  [#$sessionId] split_$index written  t=${elapsed()}ms")
                }

                val piIntent = Intent(INSTALL_ACTION).apply { setPackage(context.packageName) }
                val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                else PendingIntent.FLAG_UPDATE_CURRENT
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, piIntent, piFlags)

                Log.i(TAG, "  [#$sessionId] committing  t=${elapsed()}ms")
                pkgSession.commit(pendingIntent.intentSender)
                Log.i(TAG, "  [#$sessionId] committed — awaiting callbacks  t=${elapsed()}ms")
                session.channel.trySend(InstallState.Finalizing)
            }
        } catch (e: Exception) {
            Log.e(TAG, "✖ [#$sessionId] exception t=${elapsed()}ms: ${e.message}", e)
            pkgInstaller.unregisterSessionCallback(sessionCallback)
            safeAbandon(pkgInstaller, sessionId)
            session.channel.trySend(InstallState.Failure(e.message ?: "Unknown error"))
            activeSession.compareAndSet(session, null)
        }

        return session.channel
    }

    /**
     * Abandons the active PackageInstaller session, forcing an immediate failure result.
     * Called when the user presses Cancel or the install times out.
     */
    fun cancelCurrentSession() {
        val session = activeSession.getAndSet(null) ?: run {
            Log.d(TAG, "cancelCurrentSession: no active session")
            return
        }
        Log.w(TAG, "✖ cancelCurrentSession  session=${session.sessionId}")
        safeAbandon(context.packageManager.packageInstaller, session.sessionId)
        session.channel.trySend(InstallState.Failure("Installation cancelled"))
    }

    private fun safeAbandon(pkgInstaller: PackageInstaller, sessionId: Int) {
        try {
            pkgInstaller.abandonSession(sessionId)
            Log.i(TAG, "  abandonSession($sessionId) OK")
        } catch (e: Exception) {
            Log.w(TAG, "  abandonSession($sessionId) skipped: ${e.message}")
        }
    }
}

/**
 * BroadcastReceiver (registered in AndroidManifest) that forwards system
 * PackageInstaller callbacks to the active session's [Channel].
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != INSTALL_ACTION) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
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
        Log.i(TAG, "◀ BroadcastReceiver  status=$statusName  pkg=$packageName  msg=${message.ifBlank { "(none)" }}")

        val installer = (context.applicationContext as? com.apkm.installer.ApkMInstallerApp)
            ?.splitApkInstaller ?: run {
            Log.e(TAG, "◀ could not get SplitApkInstaller from Application")
            return
        }

        val session = installer.activeSession.get()
        if (session == null) {
            Log.w(TAG, "◀ no active session — broadcast arrived after cancel/timeout, ignoring")
            return
        }
        Log.i(TAG, "◀ routing to session=${session.sessionId}")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.i(TAG, "  → PendingUserAction")
                session.channel.trySend(InstallState.PendingUserAction)
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmIntent != null) {
                    Log.i(TAG, "  → starting confirmation: ${confirmIntent.component}")
                    context.startActivity(confirmIntent)
                } else {
                    Log.e(TAG, "  → EXTRA_INTENT null — cannot show confirmation!")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val state = InstallState.Success(packageName)
                Log.i(TAG, "  → $state")
                session.channel.trySend(state)
                installer.activeSession.compareAndSet(session, null)
            }
            else -> {
                val state = InstallState.Failure(message.ifBlank { "Installation failed ($statusName)" })
                Log.i(TAG, "  → $state")
                session.channel.trySend(state)
                installer.activeSession.compareAndSet(session, null)
            }
        }
    }
}
