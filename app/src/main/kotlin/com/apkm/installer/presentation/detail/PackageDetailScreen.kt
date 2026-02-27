package com.apkm.installer.presentation.detail

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.apkm.installer.R
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.presentation.theme.ApkMInstallerTheme

const val DETAIL_APP_NAME_TAG = "detail_app_name"
const val DETAIL_INSTALL_BUTTON_TAG = "detail_install_button"
const val DETAIL_PERMISSIONS_TAG = "detail_permissions_list"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageDetailScreen(
    uri: Uri,
    onNavigateUp: () -> Unit,
    onInstall: (ApkmPackageInfo) -> Unit,
    viewModel: PackageDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(uri) { viewModel.loadPackage(uri) }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Holds the package info for a pending install; non-null while the permission dialog is shown.
    var pendingInstall by remember { mutableStateOf<ApkmPackageInfo?>(null) }

    // Launcher that returns from the "Install unknown apps" settings page.
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Re-check after the user returns from Settings; install if granted, dismiss if not.
        val info = pendingInstall ?: return@rememberLauncherForActivityResult
        if (context.packageManager.canRequestPackageInstalls()) {
            pendingInstall = null
            onInstall(info)
        } else {
            pendingInstall = null
        }
    }

    // Show explanation dialog when the install-unknown-apps permission is missing.
    if (pendingInstall != null) {
        AlertDialog(
            onDismissRequest = { pendingInstall = null },
            title = { Text(stringResource(R.string.detail_perm_dialog_title)) },
            text = { Text(stringResource(R.string.detail_perm_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        )
                        settingsLauncher.launch(intent)
                    },
                ) {
                    Text(stringResource(R.string.detail_perm_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingInstall = null }) {
                    Text(stringResource(R.string.detail_perm_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            val state = uiState
            if (state is DetailUiState.Success) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 0.dp,
                ) {
                    Button(
                        onClick = {
                            if (context.packageManager.canRequestPackageInstalls()) {
                                onInstall(state.info)
                            } else {
                                pendingInstall = state.info
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .height(56.dp)
                            .testTag(DETAIL_INSTALL_BUTTON_TAG),
                    ) {
                        Text(stringResource(R.string.detail_install), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState,
            label = "detail_content",
            modifier = Modifier.padding(innerPadding),
        ) { state ->
            when (state) {
                is DetailUiState.Loading -> LoadingState()
                is DetailUiState.Error -> ErrorState(state.message)
                is DetailUiState.Success -> SuccessState(
                    info = state.info,
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    }
}

/** Public entry point for Compose tests to render the success state directly. */
@Composable
fun SuccessStatePreviewContent(info: ApkmPackageInfo) =
    SuccessState(info = info)

@Composable
private fun SuccessState(
    info: ApkmPackageInfo,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            AppHeader(info)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
        item {
            MetaRow(label = stringResource(R.string.detail_package), value = info.packageName)
            MetaRow(label = stringResource(R.string.detail_version), value = "${info.versionName} (${info.versionCode})")
            MetaRow(label = stringResource(R.string.detail_size), value = formatBytes(info.totalSizeBytes))
            MetaRow(label = stringResource(R.string.detail_splits), value = info.apkFiles.size.toString())
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
        if (info.permissions.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.detail_permissions),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(
                items = info.permissions,
                key = { it },
            ) { permission ->
                ListItem(
                    modifier = Modifier.testTag(DETAIL_PERMISSIONS_TAG),
                    headlineContent = {
                        Text(permission.substringAfterLast('.'), style = MaterialTheme.typography.bodyMedium)
                    },
                    supportingContent = {
                        Text(permission, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    },
                    leadingContent = {
                        Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                )
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AppHeader(info: ApkmPackageInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = androidx.compose.runtime.remember(info.icon) { info.icon?.toBitmap(96, 96) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_app_icon),
                modifier = Modifier.size(64.dp),
            )
        } else {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    Icons.Outlined.Android,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(40.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Column {
            Text(
                text = info.appName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag(DETAIL_APP_NAME_TAG),
            )
            Text(
                text = info.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val BYTES_IN_KB = 1024L
private const val BYTES_IN_MB = 1024L * 1024L

private fun formatBytes(bytes: Long): String = when {
    bytes < BYTES_IN_KB -> "$bytes B"
    bytes < BYTES_IN_MB -> "${bytes / BYTES_IN_KB} KB"
    else -> "${"%.1f".format(bytes / BYTES_IN_MB.toDouble())} MB"
}

@Suppress("UnusedPrivateMember")
@Composable
private fun rememberBitmap(key: Any?, calculation: () -> Any?) =
    androidx.compose.runtime.remember(key) { calculation() }

@Preview(showBackground = true)
@Composable
private fun DetailPreview() {
    ApkMInstallerTheme {
        SuccessState(
            info = ApkmPackageInfo(
                appName = "Sample App",
                packageName = "com.example.sample",
                versionName = "3.14.2",
                versionCode = 314002,
                icon = null,
                permissions = listOf("android.permission.INTERNET", "android.permission.CAMERA"),
                apkFiles = listOf("/cache/base.apk"),
                totalSizeBytes = 45_600_000,
            ),
        )
    }
}
