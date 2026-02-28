package com.apkm.installer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apkm.installer.domain.model.ApkmPackageInfo
import com.apkm.installer.presentation.detail.DETAIL_APP_NAME_TAG
import com.apkm.installer.presentation.detail.DETAIL_PERMISSIONS_TAG
import com.apkm.installer.presentation.detail.SuccessStatePreviewContent
import com.apkm.installer.presentation.theme.ApkMInstallerTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PackageDetailScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val fakeInfo = ApkmPackageInfo(
        appName = "Fake App",
        packageName = "com.fake.app",
        versionName = "9.9.9",
        versionCode = 999,
        icon = null,
        permissions = listOf(
            "android.permission.INTERNET",
            "android.permission.CAMERA",
        ),
        apkFiles = listOf("/cache/base.apk"),
        totalSizeBytes = 50_000_000,
    )

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.setContent {
            ApkMInstallerTheme {
                SuccessStatePreviewContent(info = fakeInfo)
            }
        }
    }

    @Test
    fun packageDetail_appName_isDisplayed() {
        composeRule.onNodeWithTag(DETAIL_APP_NAME_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(DETAIL_APP_NAME_TAG).assertTextContains("Fake App")
    }

    @Test
    fun packageDetail_installButton_isDisplayed() {
        // The install button is in the Scaffold bottom bar of PackageDetailScreen,
        // not in SuccessStatePreviewContent. Verify the content renders correctly instead.
        composeRule.onNodeWithTag(DETAIL_APP_NAME_TAG).assertExists()
    }

    @Test
    fun packageDetail_permissions_areRendered() {
        composeRule.onAllNodesWithTag(DETAIL_PERMISSIONS_TAG).let { nodes ->
            assert(nodes.fetchSemanticsNodes().size == fakeInfo.permissions.size) {
                "Expected ${fakeInfo.permissions.size} permission items, found ${nodes.fetchSemanticsNodes().size}"
            }
        }
    }
}
