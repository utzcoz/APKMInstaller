package com.apkm.installer.presentation.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apkm.installer.R
import com.apkm.installer.presentation.theme.ApkMInstallerTheme

const val HOME_PICK_BUTTON_TAG = "home_pick_button"

@Composable
fun HomeScreen(
    onFilePicked: (Uri) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate as soon as a URI arrives
    LaunchedEffect(uiState.pendingUri) {
        uiState.pendingUri?.let { uri ->
            viewModel.onNavigatedToDetail()
            onFilePicked(uri)
        }
    }

    // Show error snackbar
    val errorMsg = uiState.error
    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearError()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) viewModel.onFilePicked(uri)
            else viewModel.onError("No file selected")
        },
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        HomeContent(
            modifier = Modifier.padding(innerPadding),
            onPickFile = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) },
        )
    }
}

@Composable
private fun HomeContent(
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(600, easing = EaseInOutCubic))
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Hero icon
        Box(
            modifier = Modifier
                .scale(scale.value)
                .size(120.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onPickFile,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .height(56.dp)
                .testTag(HOME_PICK_BUTTON_TAG),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(
                text = stringResource(R.string.home_cta),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.home_drop_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    ApkMInstallerTheme {
        HomeContent(onPickFile = {})
    }
}
