package com.apkm.installer

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        // Verify the button exists and has click action semantics.
        // We cannot actually click it because it launches the system file picker,
        // which takes the activity out of the foreground and destroys the compose hierarchy.
        composeRule.onNodeWithTag(HOME_PICK_BUTTON_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(HOME_PICK_BUTTON_TAG).assertHasClickAction()
    }
}
