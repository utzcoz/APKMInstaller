package com.apkm.installer.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.apkm.installer.domain.model.InstallState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SplitApkInstaller"
private const val ACTION_PREFIX = "com.apkm.installer.INSTALL_RESULT_"
private const val TIMEOUT_MS = 5L * 60 * 1_000 // 5 minutes

/**
 * Installs split APKs using the [PackageInstaller] session API.
 *
 * Flow:
 * 1. Create a session, write all split APKs
 * 2. Commit with a [PendingIntent] targeting a dynamically-registered [BroadcastReceiver]
 * 3. The receiver handles [PackageInstaller.STATUS_PENDING_USER_ACTION] by launching the
 *    system confirmation Activity, then waits for the final callback
 * 4. A [CompletableDeferred] bridges the callback to the calling coroutine
 * 5. [withTimeoutOrNull] prevents indefinite hangs (returns null → Failure)
 */
@Singleton
class SplitApkInstaller
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        @Volatile
        private var currentSessionId: Int = -1

        /**
         * Installs the APKs via the PackageInstaller session API.
         * Returns the terminal [InstallState] (Success or Failure).
         */
        suspend fun install(
            packageName: String,
            apkPaths: List<String>,
        ): InstallState =
            withContext(Dispatchers.IO) {
                val wallStart = SystemClock.elapsedRealtime()

                fun elapsed() = SystemClock.elapsedRealtime() - wallStart

                val installer = context.packageManager.packageInstaller
                Log.i(TAG, "▶ install START  pkg=$packageName  splits=${apkPaths.size}")

                // --- 1. Create session ---
                val params =
                    SessionParams(SessionParams.MODE_FULL_INSTALL).apply {
                        setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setRequireUserAction(SessionParams.USER_ACTION_NOT_REQUIRED)
                        }
                    }
                val sessionId =
                    try {
                        installer.createSession(params)
                    } catch (e: Exception) {
                        Log.e(TAG, "  ✖ createSession failed: ${e.message}")
                        return@withContext InstallState.Failure("Could not create install session: ${e.message}")
                    }
                currentSessionId = sessionId
                Log.i(TAG, "  [${elapsed()}ms] sessionId=$sessionId")

                // --- 2. Write each split APK ---
                try {
                    installer.openSession(sessionId).use { session ->
                        apkPaths.forEachIndexed { index, path ->
                            val file = File(path)
                            val name = "split_$index.apk"
                            Log.d(TAG, "  [${elapsed()}ms] writing $name (${file.length()} bytes)")
                            session.openWrite(name, 0, file.length()).use { out ->
                                file.inputStream().use { input -> input.copyTo(out) }
                                session.fsync(out)
                            }
                        }

                        // --- 3. Commit and wait for result ---
                        val result = CompletableDeferred<InstallState>()
                        val action = "$ACTION_PREFIX$sessionId"
                        val receiver = buildResultReceiver(result, packageName) { elapsed() }

                        // Register receiver before commit to avoid missing the callback.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.registerReceiver(
                                receiver,
                                IntentFilter(action),
                                Context.RECEIVER_NOT_EXPORTED,
                            )
                        } else {
                            @Suppress("UnspecifiedRegisterReceiverFlag")
                            context.registerReceiver(receiver, IntentFilter(action))
                        }

                        val pendingIntent =
                            PendingIntent.getBroadcast(
                                context,
                                sessionId,
                                Intent(action).setPackage(context.packageName),
                                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                            )

                        Log.i(TAG, "  [${elapsed()}ms] committing session $sessionId")
                        session.commit(pendingIntent.intentSender)

                        // Wait for result with timeout — withTimeoutOrNull returns null on
                        // timeout instead of throwing TimeoutCancellationException, so the
                        // coroutine stays alive and we can emit a proper Failure state.
                        val installResult =
                            try {
                                withTimeoutOrNull(TIMEOUT_MS) { result.await() }
                                    ?: InstallState.Failure(
                                        "Installation timed out. You may need to check your device screen " +
                                            "for a system confirmation dialog.",
                                    )
                            } finally {
                                try {
                                    context.unregisterReceiver(receiver)
                                } catch (_: Exception) {
                                    // already unregistered
                                }
                                currentSessionId = -1
                            }

                        installResult
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "  ✖ session write/commit failed: ${e.message}", e)
                    try {
                        installer.abandonSession(sessionId)
                    } catch (_: Exception) {
                    }
                    currentSessionId = -1
                    InstallState.Failure("Installation failed: ${e.message}")
                }
            }

        /** Abandon the current session if one is active. */
        fun cancelCurrentSession() {
            val sid = currentSessionId
            if (sid >= 0) {
                Log.i(TAG, "cancelCurrentSession  sessionId=$sid")
                try {
                    context.packageManager.packageInstaller.abandonSession(sid)
                } catch (e: Exception) {
                    Log.w(TAG, "abandonSession($sid) failed: ${e.message}")
                }
                currentSessionId = -1
            }
        }

        private fun buildResultReceiver(
            result: CompletableDeferred<InstallState>,
            packageName: String,
            elapsed: () -> Long,
        ): BroadcastReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent,
                ) {
                    val status =
                        intent.getIntExtra(
                            PackageInstaller.EXTRA_STATUS,
                            PackageInstaller.STATUS_FAILURE,
                        )
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    Log.i(TAG, "  callback status=$status  msg=$msg  t=${elapsed()}ms")

                    when (status) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            @Suppress("DEPRECATION")
                            val confirmIntent =
                                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                            confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                Log.i(TAG, "  → launching user confirmation activity")
                                ctx.startActivity(confirmIntent)
                            } catch (e: Exception) {
                                Log.e(TAG, "  ✖ startActivity failed: ${e.message}")
                                result.complete(
                                    InstallState.Failure(
                                        "Could not show install confirmation: ${e.message}",
                                    ),
                                )
                            }
                        }

                        PackageInstaller.STATUS_SUCCESS -> {
                            Log.i(TAG, "▶ install SUCCESS  t=${elapsed()}ms")
                            result.complete(InstallState.Success(packageName))
                        }

                        else -> {
                            val error = msg ?: "Installation failed (status=$status)"
                            Log.e(TAG, "▶ install FAILED  t=${elapsed()}ms  $error")
                            result.complete(InstallState.Failure(error))
                        }
                    }
                }
            }
    }
