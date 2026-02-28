package com.apkm.installer.domain.model

/** Represents the lifecycle of a split-APK installation session. */
sealed class InstallState {
    data object Idle : InstallState()

    data object Extracting : InstallState()

    data object Verifying : InstallState()

    data object Installing : InstallState()

    data class Success(
        val packageName: String,
    ) : InstallState()

    data class Failure(
        val message: String,
    ) : InstallState()
}
