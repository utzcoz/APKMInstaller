package com.apkm.installer.presentation.install

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apkm.installer.R
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.domain.model.InstallState
import com.apkm.installer.presentation.theme.ApkMInstallerTheme

const val INSTALL_STATUS_TAG = "install_status_text"
const val INSTALL_DONE_BUTTON_TAG = "install_done_button"
const val INSTALL_RETRY_BUTTON_TAG = "install_retry_button"

@Composable
fun InstallProgressScreen(
    packageInfo: ApkmPackageInfo,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    viewModel: InstallViewModel = hiltViewModel(),
) {
    LaunchedEffect(packageInfo) { viewModel.install(packageInfo) }

    val state by viewModel.installState.collectAsState()

    Scaffold { innerPadding ->
        InstallContent(
            state = state,
            appName = packageInfo.appName,
            packageName = packageInfo.packageName,
            modifier = Modifier.padding(innerPadding),
            onDone = onDone,
            onRetry = {
                viewModel.reset()
                onRetry()
            },
        )
    }
}

@Composable
fun InstallContent(
    state: InstallState,
    appName: String,
    packageName: String,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            is InstallState.Success -> SuccessContent(appName, packageName, onDone)
            is InstallState.Failure -> FailureContent(state.message, onRetry)
            else -> ProgressContent(state, appName)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProgressContent(state: InstallState, appName: String) {
    CircularWavyProgressIndicator(
        modifier = Modifier.size(96.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )

    Spacer(Modifier.height(40.dp))

    Text(
        text = appName,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(12.dp))

    val statusText = when (state) {
        InstallState.Extracting -> stringResource(R.string.install_step_extracting)
        InstallState.Verifying -> stringResource(R.string.install_step_verifying)
        InstallState.Installing -> stringResource(R.string.install_step_installing)
        InstallState.Finalizing -> stringResource(R.string.install_step_finalizing)
        InstallState.PendingUserAction -> stringResource(R.string.install_step_pending_user_action)
        else -> stringResource(R.string.install_title)
    }

    Text(
        text = statusText,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(INSTALL_STATUS_TAG),
    )
}

@Composable
private fun SuccessContent(appName: String, packageName: String, onDone: () -> Unit) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(400)) }

    Icon(
        Icons.Outlined.CheckCircle,
        contentDescription = null,
        modifier = Modifier
            .scale(scale.value)
            .size(80.dp),
        tint = MaterialTheme.colorScheme.tertiary,
    )

    Spacer(Modifier.height(24.dp))

    Text(
        stringResource(R.string.install_success),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(INSTALL_STATUS_TAG),
    )

    Spacer(Modifier.height(8.dp))

    Text(
        appName,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(32.dp))

    val context = LocalContext.current
    val launchIntent = remember(packageName) {
        context.packageManager.getLaunchIntentForPackage(packageName)
    }

    if (launchIntent != null) {
        Button(
            onClick = { context.startActivity(launchIntent) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.install_open_app))
        }
        Spacer(Modifier.height(8.dp))
    }

    OutlinedButton(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(INSTALL_DONE_BUTTON_TAG),
    ) {
        Text(stringResource(R.string.install_done))
    }
}

@Composable
private fun FailureContent(message: String, onRetry: () -> Unit) {
    Icon(
        Icons.Outlined.ErrorOutline,
        contentDescription = null,
        modifier = Modifier.size(80.dp),
        tint = MaterialTheme.colorScheme.error,
    )

    Spacer(Modifier.height(24.dp))

    Text(
        stringResource(R.string.install_failure),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(INSTALL_STATUS_TAG),
    )

    Spacer(Modifier.height(8.dp))

    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(32.dp))

    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(INSTALL_RETRY_BUTTON_TAG),
    ) {
        Text(stringResource(R.string.install_retry))
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressPreview() {
    ApkMInstallerTheme {
        Box(Modifier.fillMaxSize()) {
            InstallContent(
                state = InstallState.Installing,
                appName = "Sample App",
                packageName = "com.example.app",
                onDone = {},
                onRetry = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SuccessPreview() {
    ApkMInstallerTheme {
        Box(Modifier.fillMaxSize()) {
            InstallContent(
                state = InstallState.Success("com.example.app"),
                appName = "Sample App",
                packageName = "com.example.app",
                onDone = {},
                onRetry = {},
            )
        }
    }
}
