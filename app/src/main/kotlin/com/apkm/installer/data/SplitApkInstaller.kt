package com.apkm.installer.data

import android.os.SystemClock
import android.util.Log
import com.apkm.installer.domain.model.InstallState
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SplitApkInstaller"
private const val BYTES_PER_MB = 1_048_576.0

/**
 * Installs split APKs using the `pm install-create` / `pm install-write` /
 * `pm install-commit` shell commands, the same approach used by APKMirror Helper.
 *
 * This bypasses the Java [android.content.pm.PackageInstaller] API entirely.
 * The shell `pm` tool talks directly to the package manager service without
 * triggering Play Protect scan delays, broadcast-based callbacks, or
 * STATUS_PENDING_USER_ACTION prompts that plague the Java API on GMS emulators.
 *
 * Flow:
 * 1. `pm install-create -r` → creates a session, returns "Success: created install session [N]"
 * 2. `pm install-write -S <size> <sessionId> <name> <path>` per split
 * 3. `pm install-commit <sessionId>` → "Success" or error
 */
@Singleton
class SplitApkInstaller @Inject constructor(
    @Suppress("unused") @ApplicationContext private val context: Context,
) {
    /**
     * Installs the APKs synchronously via shell commands.
     * Returns the terminal [InstallState] (Success or Failure).
     *
     * Must be called from a background thread / coroutine (IO dispatcher).
     */
    suspend fun install(packageName: String, apkPaths: List<String>): InstallState =
        withContext(Dispatchers.IO) {
            val wallStart = SystemClock.elapsedRealtime()
            fun elapsed() = SystemClock.elapsedRealtime() - wallStart

            Log.i(TAG, "▶ shell install START  pkg=$packageName  splits=${apkPaths.size}")

            // --- 1. Create session ---
            val totalSize = apkPaths.sumOf { File(it).length() }
            val createCmd = "pm install-create -r -S $totalSize"
            Log.d(TAG, "  [${elapsed()}ms] > $createCmd")
            val createResult = exec(createCmd)
            Log.d(TAG, "  [${elapsed()}ms] < ${createResult.output}")
            if (!createResult.success) {
                val msg = "pm install-create failed: ${createResult.output}"
                Log.e(TAG, "  ✖ $msg")
                return@withContext InstallState.Failure(msg)
            }

            val sessionId = parseSessionId(createResult.output)
            if (sessionId == null) {
                val msg = "Could not parse session ID from: ${createResult.output}"
                Log.e(TAG, "  ✖ $msg")
                return@withContext InstallState.Failure(msg)
            }
            Log.i(TAG, "  [${elapsed()}ms] session=$sessionId")

            // --- 2. Write each split ---
            apkPaths.forEachIndexed { index, path ->
                val file = File(path)
                val size = file.length()
                val name = "split_$index.apk"
                val writeCmd = "pm install-write -S $size $sessionId $name $path"
                Log.d(TAG, "  [${elapsed()}ms] > pm install-write  $name  size=${"%.2f".format(size / BYTES_PER_MB)}MB")
                val writeResult = exec(writeCmd)
                Log.d(TAG, "  [${elapsed()}ms] < ${writeResult.output}")
                if (!writeResult.success) {
                    Log.e(TAG, "  ✖ write failed for $name: ${writeResult.output}")
                    exec("pm install-abandon $sessionId")
                    return@withContext InstallState.Failure(
                        "Failed to write $name: ${writeResult.output}"
                    )
                }
            }

            // --- 3. Commit ---
            val commitCmd = "pm install-commit $sessionId"
            Log.i(TAG, "  [${elapsed()}ms] > $commitCmd")
            val commitResult = exec(commitCmd)
            Log.i(TAG, "  [${elapsed()}ms] < ${commitResult.output}")

            if (commitResult.success && commitResult.output.contains("Success", ignoreCase = true)) {
                Log.i(TAG, "▶ shell install SUCCESS  t=${elapsed()}ms")
                InstallState.Success(packageName)
            } else {
                val msg = commitResult.output.ifBlank { "pm install-commit failed" }
                Log.e(TAG, "▶ shell install FAILED  t=${elapsed()}ms  msg=$msg")
                InstallState.Failure(msg)
            }
        }

    fun cancelCurrentSession() {
        // Shell-based install is synchronous — cancel happens via coroutine cancellation.
        // No session to abandon here; the coroutine checks for cancellation between steps.
        Log.d(TAG, "cancelCurrentSession (no-op for shell installer)")
    }

    /**
     * Parse session ID from output like:
     * "Success: created install session [1234567]"
     */
    private fun parseSessionId(output: String): Int? {
        val match = Regex("""\[(\d+)]""").find(output)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Execute a shell command via `Runtime.exec()` and return the combined output.
     */
    private fun exec(command: String): ShellResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            val exitCode = process.waitFor()
            val output = if (stderr.isNotEmpty() && stdout.isNotEmpty()) "$stdout\n$stderr"
            else stdout.ifEmpty { stderr }
            ShellResult(exitCode == 0, output, exitCode)
        } catch (e: Exception) {
            ShellResult(false, e.message ?: "exec failed", -1)
        }
    }

    private data class ShellResult(val success: Boolean, val output: String, val exitCode: Int)
}
