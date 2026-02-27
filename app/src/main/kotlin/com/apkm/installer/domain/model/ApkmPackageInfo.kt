package com.apkm.installer.domain.model

import android.graphics.drawable.Drawable

/**
 * Metadata parsed from a .apkm file before installation.
 */
data class ApkmPackageInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    /** App icon decoded from base.apk, may be null if unavailable. */
    val icon: Drawable?,
    /** Declared permissions (e.g. "android.permission.CAMERA"). */
    val permissions: List<String>,
    /** Paths to the extracted APK files in the device cache directory. */
    val apkFiles: List<String>,
    /** Total uncompressed size of all APKs in bytes. */
    val totalSizeBytes: Long,
)
