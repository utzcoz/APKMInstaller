package com.apkm.installer.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.apkm.installer.domain.model.ApkmPackageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ApkmParser"
private const val EXTRACT_DIR = "apkm_extract"
private const val MAX_EXTRACT_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB safety cap

/**
 * Parses a `.apkm` file (a ZIP of split APKs) into [ApkmPackageInfo].
 *
 * The file is extracted to [Context.getCacheDir]/apkm_extract/<sessionId>/
 * so that [androidx.core.content.FileProvider] can share individual APKs with
 * the system PackageInstaller.
 */
@Singleton
class ApkmParser @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Extracts the .apkm at [uri] and returns [ApkmPackageInfo].
     *
     * @throws IllegalArgumentException if the file contains no APKs.
     * @throws java.io.IOException on I/O failure.
     */
    @Suppress("DEPRECATION")
    fun parse(uri: Uri): ApkmPackageInfo {
        val sessionDir = prepareSessionDir()
        val apkFiles = mutableListOf<File>()
        var totalBytes = 0L

        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                        val outFile = File(sessionDir, entry.name.substringAfterLast('/'))
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            val written = zip.copyTo(out, bufferSize = 65_536)
                            totalBytes += written
                            require(totalBytes <= MAX_EXTRACT_BYTES) { "Package exceeds size limit" }
                        }
                        apkFiles += outFile
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalArgumentException("Cannot open URI: $uri")

        if (apkFiles.isEmpty()) {
            sessionDir.deleteRecursively()
            require(false) { "No APK files found inside the .apkm archive" }
        }

        // Sort so base.apk is always first
        apkFiles.sortWith(compareBy { if (it.name == "base.apk") 0 else 1 })
        val baseApk = apkFiles.first()

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.content.pm.PackageManager.GET_PERMISSIONS or
                android.content.pm.PackageManager.GET_META_DATA
        } else {
            @Suppress("DEPRECATION")
            android.content.pm.PackageManager.GET_PERMISSIONS
        }

        val packageInfo = context.packageManager
            .getPackageArchiveInfo(baseApk.absolutePath, flags)
            ?: throw IllegalArgumentException("Cannot parse base.apk – not a valid APK file")

        packageInfo.applicationInfo?.let {
            it.sourceDir = baseApk.absolutePath
            it.publicSourceDir = baseApk.absolutePath
        }

        val icon = runCatching {
            packageInfo.applicationInfo?.loadIcon(context.packageManager)
        }.getOrNull()

        val appName = runCatching {
            packageInfo.applicationInfo?.loadLabel(context.packageManager)?.toString()
        }.getOrNull() ?: packageInfo.packageName

        Log.d(TAG, "Parsed ${packageInfo.packageName} with ${apkFiles.size} APK(s)")

        return ApkmPackageInfo(
            appName = appName,
            packageName = packageInfo.packageName,
            versionName = packageInfo.versionName ?: "unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            },
            icon = icon,
            permissions = packageInfo.requestedPermissions?.toList() ?: emptyList(),
            apkFiles = apkFiles.map { it.absolutePath },
            totalSizeBytes = totalBytes,
        )
    }

    /** Deletes extracted files for a given package from the cache. */
    fun clearCache() {
        File(context.cacheDir, EXTRACT_DIR).deleteRecursively()
    }

    private fun prepareSessionDir(): File {
        val dir = File(context.cacheDir, "$EXTRACT_DIR/${System.currentTimeMillis()}")
        dir.mkdirs()
        return dir
    }
}
