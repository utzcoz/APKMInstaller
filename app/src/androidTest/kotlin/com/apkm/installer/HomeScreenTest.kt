package com.apkm.installer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apkm.installer.presentation.home.HOME_PICK_BUTTON_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun homeScreen_pickButton_isDisplayed() {
        composeRule.onNodeWithTag(HOME_PICK_BUTTON_TAG).assertIsDisplayed()
    }

    @Test
    fun homeScreen_pickButton_isClickable() {
        // Clicking will open the system file picker (we just verify the click doesn't crash)
        composeRule.onNodeWithTag(HOME_PICK_BUTTON_TAG).performClick()
        composeRule.waitForIdle()
        // Activity is still alive after the click (system picker opened)
        composeRule.onNodeWithTag(HOME_PICK_BUTTON_TAG).assertExists()
    }
}
