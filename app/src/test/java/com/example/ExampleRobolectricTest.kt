package com.example

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.game.model.GameUiState
import com.example.game.model.ScreenState
import com.example.game.ui.*
import com.example.ui.theme.WardZeroTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w800dp-h480dp-land")
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun appNameMatchesWardZero() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Ward Zero", appName)
  }

  @Test
  fun titleAndSettingsUiRenderAndRespond() {
    var started = false
    var openedSettings = false
    composeTestRule.setContent {
      WardZeroTheme {
        TitleScreen(
          state = GameUiState(screen = ScreenState.TITLE, hasCheckpoint = true),
          onStartNewShift = { started = true },
          onContinueCheckpoint = {},
          onOpenSettings = { openedSettings = true },
          onOpenNotes = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("start_new_shift_btn").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("continue_checkpoint_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_btn").assertIsDisplayed().performClick()
    assertTrue(started)
    assertTrue(openedSettings)
  }
}
