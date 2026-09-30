package com.apkm.installer

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apkm.installer.data.ApkmParser
import com.apkm.installer.data.SplitApkInstaller
import com.apkm.installer.di.AppModule
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.domain.model.InstallState
import com.apkm.installer.presentation.detail.DETAIL_INSTALL_BUTTON_TAG
import com.apkm.installer.presentation.home.HOME_PICK_BUTTON_TAG
import com.apkm.installer.presentation.install.INSTALL_DONE_BUTTON_TAG
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the real navigation graph through Home → Detail → Install → Done, with the file picker,
 * parser and PackageInstaller faked out.
 */
@HiltAndroidTest
@UninstallModules(AppModule::class)
@RunWith(AndroidJUnit4::class)
class InstallFlowNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fakeUri = Uri.parse("content://com.apkm.installer.test/fake.apkm")
    private val fakeApk =
        File(instrumentation.targetContext.cacheDir, "fake-base.apk").apply { writeText("apk") }

    private val fakeInfo =
        ApkmPackageInfo(
            appName = "Fake App",
            packageName = "com.fake.app",
            versionName = "1.0",
            versionCode = 1,
            icon = null,
            permissions = emptyList(),
            // InstallPackageUseCase verifies that these files exist.
            apkFiles = listOf(fakeApk.absolutePath),
            totalSizeBytes = fakeApk.length(),
        )

    @BindValue
    @JvmField
    val parser: ApkmParser = mockk { every { parse(fakeUri) } returns fakeInfo }

    @BindValue
    @JvmField
    val installer: SplitApkInstaller =
        mockk(relaxed = true) {
            coEvery { install(any(), any()) } returns InstallState.Success(fakeInfo.packageName)
        }

    @Before
    fun setUp() {
        hiltRule.inject()
        // Skip the "Install unknown apps" permission dialog on the detail screen.
        instrumentation.uiAutomation
            .executeShellCommand(
                "appops set ${instrumentation.targetContext.packageName} REQUEST_INSTALL_PACKAGES allow",
            ).close()
        Intents.init()
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(fakeUri)))
    }

    @After
    fun tearDown() {
        Intents.release()
        fakeApk.delete()
    }

    // Regression test for https://github.com/utzcoz/APKMInstaller/issues/1: tapping Done used to
    // pop the home destination too, leaving an empty NavHost (blank white screen).
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun installFlow_done_returnsToHome() {
        composeRule.onNodeWithTag(HOME_PICK_BUTTON_TAG).performClick()

        composeRule.waitUntilExactlyOneExists(hasTestTag(DETAIL_INSTALL_BUTTON_TAG), TIMEOUT_MS)
        composeRule.onNodeWithTag(DETAIL_INSTALL_BUTTON_TAG).performClick()

        composeRule.waitUntilExactlyOneExists(hasTestTag(INSTALL_DONE_BUTTON_TAG), TIMEOUT_MS)
        composeRule.onNodeWithTag(INSTALL_DONE_BUTTON_TAG).performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(HOME_PICK_BUTTON_TAG).assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
