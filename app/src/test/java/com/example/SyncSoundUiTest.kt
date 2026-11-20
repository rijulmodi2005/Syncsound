package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.model.ConnectionMode
import com.example.model.SessionConnectionState
import com.example.ui.screens.CreateRoomScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.SyncSoundTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncSoundUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `home screen displays create and join room cards and navigates`() {
        var createClicked = false
        var joinClicked = false

        composeRule.setContent {
            SyncSoundTheme {
                HomeScreen(
                    connectionState = SessionConnectionState.IDLE,
                    activeRole = null,
                    activeRoom = null,
                    discoveredRooms = emptyList(),
                    recentRooms = emptyList(),
                    onStartDiscovery = {},
                    onStopDiscovery = {},
                    onCreateRoomClick = { createClicked = true },
                    onJoinRoomClick = { joinClicked = true },
                    onQuickJoinDiscovered = {},
                    onResumeRecent = {},
                    onRemoveRecent = {},
                    onOpenActiveSession = {},
                    onSettingsClick = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("SyncSound").assertIsDisplayed()
        composeRule.onNodeWithText("Every Device. One Sound.").assertIsDisplayed()
        composeRule.onNodeWithText("Turn Every Device Into a Speaker.").assertIsDisplayed()
        composeRule.onNodeWithTag("home_create_room_btn").performClick()
        composeRule.onNodeWithTag("home_join_room_btn").performClick()
        assertTrue(createClicked)
        assertTrue(joinClicked)
    }

    @Test
    fun `create room screen displays 5-digit code and launches room`() {
        var launchedName = ""
        var launchedCode = ""
        var launchedMode: ConnectionMode? = null

        composeRule.setContent {
            SyncSoundTheme {
                CreateRoomScreen(
                    initialCode = "58321",
                    defaultRelayUrl = "",
                    onGenerateNewCode = { "99412" },
                    onShareInvite = {},
                    onLaunchRoom = { name, code, mode, _ ->
                        launchedName = name
                        launchedCode = code
                        launchedMode = mode
                    },
                    onBack = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("generated_room_code_text").assertIsDisplayed()
        composeRule.onNodeWithTag("regenerate_code_btn").performScrollTo().performClick()
        composeRule.onNodeWithTag("launch_room_btn").performScrollTo().performClick()

        assertEquals("Living Room Music", launchedName)
        assertEquals("99412", launchedCode)
        assertEquals(ConnectionMode.LAN_HOST, launchedMode)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncSoundMainActivityNavigationTest {

    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `main activity home screen buttons are clickable and navigate properly`() {
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("home_create_room_btn").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("launch_room_btn").performScrollTo().assertIsDisplayed()

        activityRule.onNodeWithTag("create_back_btn").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("home_join_room_btn").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("join_room_submit_btn").assertIsDisplayed()

        activityRule.onNodeWithTag("join_back_btn").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("home_settings_button").performClick()
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("theme_switch").assertIsDisplayed()
    }
}

