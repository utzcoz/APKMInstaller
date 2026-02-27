package com.apkm.installer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apkm.installer.domain.model.InstallState
import com.apkm.installer.presentation.install.INSTALL_DONE_BUTTON_TAG
import com.apkm.installer.presentation.install.INSTALL_RETRY_BUTTON_TAG
import com.apkm.installer.presentation.install.INSTALL_STATUS_TAG
import com.apkm.installer.presentation.install.InstallContent
import com.apkm.installer.presentation.theme.ApkMInstallerTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InstallProgressScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun installScreen_extractingState_showsStatusText() {
        composeRule.setContent {
            ApkMInstallerTheme {
                InstallContent(
                    state = InstallState.Extracting,
                    appName = "My App",
                    packageName = "com.my.app",
                    onDone = {},
                    onRetry = {},
                    onCancel = {},
                )
            }
        }
        composeRule.onNodeWithTag(INSTALL_STATUS_TAG).assertIsDisplayed()
    }

    @Test
    fun installScreen_successState_showsDoneButton() {
        composeRule.setContent {
            ApkMInstallerTheme {
                InstallContent(
                    state = InstallState.Success("com.my.app"),
                    appName = "My App",
                    packageName = "com.my.app",
                    onDone = {},
                    onRetry = {},
                    onCancel = {},
                )
            }
        }
        composeRule.onNodeWithTag(INSTALL_DONE_BUTTON_TAG).assertIsDisplayed()
    }

    @Test
    fun installScreen_failureState_showsRetryButton() {
        composeRule.setContent {
            ApkMInstallerTheme {
                InstallContent(
                    state = InstallState.Failure("Something went wrong"),
                    appName = "My App",
                    packageName = "com.my.app",
                    onDone = {},
                    onRetry = {},
                    onCancel = {},
                )
            }
        }
        composeRule.onNodeWithTag(INSTALL_RETRY_BUTTON_TAG).assertIsDisplayed()
    }

    @Test
    fun installScreen_failureState_showsStatusText() {
        composeRule.setContent {
            ApkMInstallerTheme {
                InstallContent(
                    state = InstallState.Failure("Something went wrong"),
                    appName = "My App",
                    packageName = "com.my.app",
                    onDone = {},
                    onRetry = {},
                    onCancel = {},
                )
            }
        }
        composeRule.onNodeWithTag(INSTALL_STATUS_TAG).assertIsDisplayed()
    }
}
