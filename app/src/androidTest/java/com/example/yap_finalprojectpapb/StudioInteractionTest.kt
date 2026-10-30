package com.example.yap_finalprojectpapb

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StudioInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun openStudio() {
        compose.onNode(hasText("Log In") and hasClickAction()).performClick()
        compose.onNode(hasText("Log In") and hasClickAction()).performClick()
    }

    @Test fun draftSurvivesSettingsAndNewChatCanBeCancelled() {
        compose.onNodeWithText("Kalkulator Portabel").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Buatkan aplikasi kalkulator", substring = true)
        compose.onNodeWithContentDescription("Pengaturan AI").performClick()
        compose.onNodeWithText("Pengaturan AI").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(hasSetTextAction()).assertTextContains("Buatkan aplikasi kalkulator", substring = true)
        compose.onNodeWithContentDescription("Chat Baru").performClick()
        compose.onNodeWithText("Tetap Lanjut").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Buatkan aplikasi kalkulator", substring = true)
    }

    @Test fun drawerWaitsForAnItemAndNewChatClearsDraft() {
        compose.onNodeWithContentDescription("Buka menu").performClick()
        compose.onNodeWithText("Workspace Kamu").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tutup menu").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Draft")
        compose.onNodeWithContentDescription("Chat Baru").performClick()
        compose.onNodeWithText("Mulai Baru").performClick()
        compose.onNodeWithContentDescription("Kirim ide").assertIsNotEnabled()
    }
}
