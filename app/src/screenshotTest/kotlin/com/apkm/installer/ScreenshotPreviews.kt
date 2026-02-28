package com.apkm.installer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.domain.model.InstallState
import com.apkm.installer.presentation.detail.SuccessStatePreviewContent
import com.apkm.installer.presentation.home.HomeContent
import com.apkm.installer.presentation.install.InstallContent
import com.apkm.installer.presentation.theme.ApkMInstallerTheme

// ── Home Screen ──

@PreviewTest
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    ApkMInstallerTheme {
        HomeContent(onPickFile = {})
    }
}

// ── Package Detail Screen ──

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PackageDetailPreview() {
    ApkMInstallerTheme {
        SuccessStatePreviewContent(
            info = ApkmPackageInfo(
                appName = "Sample App",
                packageName = "com.example.sample",
                versionName = "3.14.2",
                versionCode = 314002,
                icon = null,
                permissions = listOf(
                    "android.permission.INTERNET",
                    "android.permission.CAMERA",
                    "android.permission.ACCESS_FINE_LOCATION",
                ),
                apkFiles = listOf("/cache/base.apk", "/cache/split_config.arm64_v8a.apk"),
                totalSizeBytes = 45_600_000,
            ),
        )
    }
}

// ── Install Progress Screen: Installing ──

@PreviewTest
@Preview(showBackground = true)
@Composable
fun InstallProgressInstallingPreview() {
    ApkMInstallerTheme {
        Box(Modifier.fillMaxSize()) {
            InstallContent(
                state = InstallState.Installing,
                appName = "Sample App",
                packageName = "com.example.app",
                onDone = {},
                onRetry = {},
                onCancel = {},
            )
        }
    }
}

// ── Install Progress Screen: Extracting ──

@PreviewTest
@Preview(showBackground = true)
@Composable
fun InstallProgressExtractingPreview() {
    ApkMInstallerTheme {
        Box(Modifier.fillMaxSize()) {
            InstallContent(
                state = InstallState.Extracting,
                appName = "Sample App",
                packageName = "com.example.app",
                onDone = {},
                onRetry = {},
                onCancel = {},
            )
        }
    }
}

// ── Install Progress Screen: Success ──

@PreviewTest
@Preview(showBackground = true)
@Composable
fun InstallProgressSuccessPreview() {
    ApkMInstallerTheme {
        Box(Modifier.fillMaxSize()) {
            InstallContent(
                state = InstallState.Success("com.example.app"),
                appName = "Sample App",
                packageName = "com.example.app",
                onDone = {},
                onRetry = {},
                onCancel = {},
            )
        }
    }
}

// ── Install Progress Screen: Failure ──

@PreviewTest
@Preview(showBackground = true)
@Composable
fun InstallProgressFailurePreview() {
    ApkMInstallerTheme {
        Box(Modifier.fillMaxSize()) {
            InstallContent(
                state = InstallState.Failure("INSTALL_FAILED_UPDATE_INCOMPATIBLE"),
                appName = "Sample App",
                packageName = "com.example.app",
                onDone = {},
                onRetry = {},
                onCancel = {},
            )
        }
    }
}
