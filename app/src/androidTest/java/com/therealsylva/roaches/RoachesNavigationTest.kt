package com.therealsylva.roaches

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoachesNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun primaryDestinationsAndSettingsAreReachable() {
        compose.onNodeWithContentDescription("Discover").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Home feed region").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("Title, actor or series").assertIsDisplayed()
        compose.onNodeWithContentDescription("Library").performClick()
        compose.onNodeWithText("Library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Downloads").performClick()
        compose.onNodeWithText("Downloads").assertIsDisplayed()
    }
}
