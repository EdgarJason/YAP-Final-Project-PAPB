package com.example.yap_finalprojectpapb

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class StudioInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun mascotRespondsToTapAndLongPress() {
        compose.onNodeWithContentDescription("YAP mascot").performClick()
        compose.onNodeWithText("Hey, nice to meet you!").assertIsDisplayed()
        compose.onNodeWithContentDescription("YAP mascot").performTouchInput { longClick() }
        compose.onNodeWithText("A little kindness goes a long way.").assertIsDisplayed()
    }

    @Test fun ideaIsEditableAndSurvivesSettingsNavigation() {
        compose.onNodeWithText("A pocket calculator").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Build a simple calculator", substring = true)
        compose.onNodeWithContentDescription("API settings").performClick()
        compose.onNodeWithText("API Settings").assertIsDisplayed()
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(hasSetTextAction()).assertTextContains("Build a simple calculator", substring = true)
        compose.onNodeWithContentDescription("Send idea").performClick()
        compose.onNodeWithText("Your idea is here.").assertIsDisplayed()
        compose.onNodeWithContentDescription("New chat").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Your idea is here.").assertIsDisplayed()
    }

    @Test fun drawerDoesNotNavigateUntilAnItemIsChosen() {
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithText("Your workspace").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close menu").performClick()
        compose.onNodeWithText("Small idea.\nLet's build on it.").assertIsDisplayed()
        compose.onNodeWithText("Preview").performClick()
        compose.onNodeWithText("Room for your app.").assertIsDisplayed()
        compose.onNodeWithText("Back to chat").performClick()
        compose.onNodeWithContentDescription("Send idea").assertIsNotEnabled()
    }
}

