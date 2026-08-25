package com.therealsylva.roaches

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        compose.onNodeWithContentDescription("Home").assertIsDisplayed()
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

    @Test
    fun saveLinkLivesInsideDownloads() {
        compose.onNodeWithContentDescription("Downloads").performClick()
        compose.onNodeWithText("Save link").performClick()

        compose.onNodeWithText("Save a media link").assertIsDisplayed()
        compose.onNodeWithText("Media link").assertIsDisplayed()
        compose.onNodeWithText("Video quality").assertIsDisplayed()
    }

    @Test
    fun eggsGatePromptsForAKey() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Enable eggs").performScrollTo().performClick()
        compose.onNodeWithText("Key").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
    }
}
