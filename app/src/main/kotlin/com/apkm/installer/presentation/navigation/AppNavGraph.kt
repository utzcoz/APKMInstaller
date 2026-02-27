package com.apkm.installer.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.presentation.detail.PackageDetailScreen
import com.apkm.installer.presentation.home.HomeScreen
import com.apkm.installer.presentation.install.InstallProgressScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_DETAIL = "detail"
private const val ROUTE_INSTALL = "install"

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    // These are passed in-memory between destinations to avoid Parcelable complexity
    // with Drawable (icon) which is not easily serialisable.
    // Note: ApkmPackageInfo holds a Drawable and file paths not suitable for process-death save.
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPackageInfo by remember { mutableStateOf<ApkmPackageInfo?>(null) }

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME,
        modifier = modifier,
    ) {
        composable(ROUTE_HOME) {
            HomeScreen(
                onFilePicked = { uri ->
                    pendingUri = uri
                    navController.navigate(ROUTE_DETAIL)
                },
            )
        }

        composable(ROUTE_DETAIL) {
            val uri = pendingUri
            if (uri == null) {
                navController.popBackStack()
                return@composable
            }
            PackageDetailScreen(
                uri = uri,
                onNavigateUp = { navController.popBackStack() },
                onInstall = { info ->
                    pendingPackageInfo = info
                    navController.navigate(ROUTE_INSTALL)
                },
            )
        }

        composable(ROUTE_INSTALL) {
            val info = pendingPackageInfo
            if (info == null) {
                navController.popBackStack()
                return@composable
            }
            InstallProgressScreen(
                packageInfo = info,
                onDone = {
                    pendingUri = null
                    pendingPackageInfo = null
                    navController.popBackStack(ROUTE_HOME, inclusive = false)
                },
                onRetry = {
                    navController.popBackStack(ROUTE_DETAIL, inclusive = false)
                },
            )
        }
    }
}
