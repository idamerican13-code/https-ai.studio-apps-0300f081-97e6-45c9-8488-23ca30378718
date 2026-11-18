package com.example

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.model.ScreenState
import com.example.game.ui.*
import com.example.ui.theme.WardZeroTheme

class MainActivity : ComponentActivity() {

  private val viewModel: WardZeroViewModel by viewModels()
  private var glSurfaceView: GLSurfaceView? = null
  private var lastMouseX = 0f
  private var lastMouseY = 0f
  private var mouseDragging = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    hideSystemBars()

    setContent {
      WardZeroTheme {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        var showSettingsModal by remember { mutableStateOf(false) }
        var showCodexModal by remember { mutableStateOf(false) }

        BackHandler(enabled = state.screen != ScreenState.TITLE || showSettingsModal || showCodexModal) {
          when {
            showSettingsModal -> showSettingsModal = false
            showCodexModal -> showCodexModal = false
            state.screen == ScreenState.NOTE_READER -> viewModel.resumeGame()
            state.screen == ScreenState.PLAYING -> viewModel.pauseGame()
            state.screen == ScreenState.PAUSED -> viewModel.resumeGame()
            else -> viewModel.returnToTitle()
          }
        }

        Box(modifier = Modifier.fillMaxSize()) {
          // Real-time 3D OpenGL ES 3.0 Changi Hospital Viewport
          AndroidView(
            factory = { ctx ->
              GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(3)
                preserveEGLContextOnPause = true
                setRenderer(viewModel.glRenderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                setOnGenericMotionListener { _, ev ->
                  handleMouseOrStickMotion(ev)
                }
                glSurfaceView = this
              }
            },
            modifier = Modifier.fillMaxSize()
          )

          when (state.screen) {
            ScreenState.TITLE -> {
              TitleScreen(
                state = state,
                onStartNewShift = { viewModel.startNewShift(showIntro = true) },
                onContinueCheckpoint = { viewModel.continueFromCheckpoint() },
                onOpenSettings = { showSettingsModal = true },
                onOpenNotes = { showCodexModal = true }
              )
            }
            ScreenState.INTRO -> {
              IntroBriefingScreen(
                onBeginWithTutorial = { viewModel.enterWingFromIntro(skipTutorial = false) },
                onSkipTutorial = { viewModel.enterWingFromIntro(skipTutorial = true) }
              )
            }
            ScreenState.PLAYING -> {
              HudOverlay(
                state = state,
                inputHandler = viewModel.inputHandler,
                onInteract = { viewModel.interact() },
                onCycleTorch = { viewModel.cycleTorch() },
                onCrankDynamo = { viewModel.crankDynamo() },
                onToggleCrouch = { viewModel.toggleCrouch() },
                onThrowBottle = { viewModel.throwBottle() },
                onPause = { viewModel.pauseGame() },
                onSkipTutorial = { viewModel.skipTutorialInGame() }
              )
            }
            ScreenState.PAUSED -> {
              PauseModal(
                onResume = { viewModel.resumeGame() },
                onOpenSettings = { showSettingsModal = true },
                onOpenCodex = { showCodexModal = true },
                onRestartCheckpoint = { viewModel.continueFromCheckpoint() },
                hasCheckpoint = state.hasCheckpoint,
                onExitToTitle = { viewModel.returnToTitle() }
              )
            }
            ScreenState.NOTE_READER -> {
              state.activeNote?.let { note ->
                NoteReaderModal(
                  note = note,
                  onClose = { viewModel.resumeGame() }
                )
              }
            }
            ScreenState.CAUGHT -> {
              CaughtScreen(
                state = state,
                onRetryCheckpoint = { viewModel.continueFromCheckpoint() },
                onRestartShift = { viewModel.startNewShift(showIntro = false) },
                onReturnTitle = { viewModel.returnToTitle() }
              )
            }
            ScreenState.ENDING -> {
              EndingScreen(
                state = state,
                onPlayAgain = { viewModel.startNewShift(showIntro = false) },
                onReturnTitle = { viewModel.returnToTitle() }
              )
            }
          }

          if (showSettingsModal) {
            SettingsModal(
              settings = state.settings,
              onUpdate = { viewModel.updateSettings(it) },
              onClose = { showSettingsModal = false }
            )
          }

          if (showCodexModal) {
            CodexModal(
              notes = state.collectedNotes,
              totalCount = state.totalNotesCount,
              onClose = { showCodexModal = false }
            )
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    glSurfaceView?.onResume()
  }

  override fun onPause() {
    super.onPause()
    glSurfaceView?.onPause()
    viewModel.onAppPause()
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val handled = when (event.action) {
      KeyEvent.ACTION_DOWN -> viewModel.inputHandler.handleKeyDown(event.keyCode, event)
      KeyEvent.ACTION_UP -> viewModel.inputHandler.handleKeyUp(event.keyCode)
      else -> false
    }
    return if (handled) true else super.dispatchKeyEvent(event)
  }

  override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
    if (handleMouseOrStickMotion(ev)) return true
    return super.dispatchGenericMotionEvent(ev)
  }

  override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    // Support desktop mouse click-drag look or right-click throw bottle when playing
    if (ev.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE && viewModel.uiState.value.screen == ScreenState.PLAYING) {
      when (ev.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          if (ev.buttonState == MotionEvent.BUTTON_SECONDARY) {
            viewModel.throwBottle()
            return true
          }
          lastMouseX = ev.x
          lastMouseY = ev.y
          mouseDragging = true
        }
        MotionEvent.ACTION_MOVE -> if (mouseDragging) {
          val dx = ev.x - lastMouseX
          val dy = ev.y - lastMouseY
          lastMouseX = ev.x
          lastMouseY = ev.y
          viewModel.inputHandler.addLookDelta(dx * 1.4f, dy * 1.2f)
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mouseDragging = false
      }
    }
    return super.dispatchTouchEvent(ev)
  }

  private fun handleMouseOrStickMotion(ev: MotionEvent): Boolean {
    return viewModel.inputHandler.handleGenericMotion(ev)
  }

  private fun hideSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.hide(WindowInsetsCompat.Type.systemBars())
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
  }
}
