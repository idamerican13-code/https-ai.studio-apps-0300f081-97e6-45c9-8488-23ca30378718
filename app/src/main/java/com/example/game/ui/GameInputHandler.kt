package com.example.game.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.game.model.InputState
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Aggregates Keyboard, Mouse, Gamepad, and Touch inputs cleanly into InputState
 * and fires discrete one-shot actions via callbacks.
 */
class GameInputHandler(
  private val onInteract: () -> Unit,
  private val onCycleTorch: () -> Unit,
  private val onCrankDynamo: () -> Unit,
  private val onToggleCrouch: () -> Unit,
  private val onThrowBottle: () -> Unit,
  private val onPauseToggle: () -> Unit,
  private val onDeviceModeChanged: (gamepad: Boolean, kbm: Boolean) -> Unit
) {
  private val pressedKeys = HashSet<Int>()

  // Touch channels
  @Volatile var touchMoveX = 0f
  @Volatile var touchMoveY = 0f
  @Volatile var touchSprint = false
  @Volatile var touchBreath = false
  @Volatile var touchAimBottle = false

  // Gamepad channels
  @Volatile private var padMoveX = 0f
  @Volatile private var padMoveY = 0f
  @Volatile private var padLookX = 0f
  @Volatile private var padLookY = 0f
  @Volatile private var padSprint = false
  @Volatile private var padBreath = false

  // Mouse / touch look delta accumulator (drained each frame)
  private var accumLookX = 0f
  private var accumLookY = 0f
  private val lookLock = Any()

  fun addLookDelta(dx: Float, dy: Float) {
    synchronized(lookLock) {
      accumLookX += dx
      accumLookY += dy
    }
  }

  fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    val isGamepad = (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
      (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    if (isGamepad) {
      onDeviceModeChanged(true, false)
    } else {
      onDeviceModeChanged(false, true)
    }

    if (event.repeatCount > 0) return false
    pressedKeys.add(keyCode)

    return when (keyCode) {
      KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_BUTTON_A -> {
        onInteract(); true
      }
      KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_BUTTON_X -> {
        onCycleTorch(); true
      }
      KeyEvent.KEYCODE_R -> {
        onCrankDynamo(); true
      }
      KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_BUTTON_B -> {
        onToggleCrouch(); true
      }
      KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_BUTTON_Y -> {
        onThrowBottle(); true
      }
      KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_BUTTON_START -> {
        onPauseToggle(); true
      }
      KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
      KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
      KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
      KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
      KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L1 -> true
      else -> false
    }
  }

  fun handleKeyUp(keyCode: Int): Boolean {
    pressedKeys.remove(keyCode)
    return when (keyCode) {
      KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
      KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
      KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
      KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
      KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L1 -> true
      else -> false
    }
  }

  fun handleGenericMotion(event: MotionEvent): Boolean {
    val src = event.source
    if ((src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
      onDeviceModeChanged(true, false)
      padMoveX = deadzone(event.getAxisValue(MotionEvent.AXIS_X), 0.16f)
      padMoveY = -deadzone(event.getAxisValue(MotionEvent.AXIS_Y), 0.16f)

      val rx = deadzone(event.getAxisValue(MotionEvent.AXIS_Z), 0.16f)
      val ry = deadzone(event.getAxisValue(MotionEvent.AXIS_RZ), 0.16f)
      padLookX = if (abs(rx) > 0.01f) rx else deadzone(event.getAxisValue(MotionEvent.AXIS_RX), 0.16f)
      padLookY = if (abs(ry) > 0.01f) ry else deadzone(event.getAxisValue(MotionEvent.AXIS_RY), 0.16f)

      val lTrig = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
      val rTrig = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
      padBreath = lTrig > 0.35f
      padSprint = rTrig > 0.35f
      return true
    }
    return false
  }

  fun consumeInputState(): InputState {
    var kx = 0f
    var ky = 0f
    if (pressedKeys.contains(KeyEvent.KEYCODE_W) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_UP)) ky += 1f
    if (pressedKeys.contains(KeyEvent.KEYCODE_S) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN)) ky -= 1f
    if (pressedKeys.contains(KeyEvent.KEYCODE_D) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) kx += 1f
    if (pressedKeys.contains(KeyEvent.KEYCODE_A) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) kx -= 1f

    val kmag = hypot(kx, ky)
    if (kmag > 1f) {
      kx /= kmag
      ky /= kmag
    }

    val mx = (kx + padMoveX + touchMoveX).coerceIn(-1f, 1f)
    val my = (ky + padMoveY + touchMoveY).coerceIn(-1f, 1f)

    val ldx: Float
    val ldy: Float
    synchronized(lookLock) {
      ldx = accumLookX
      ldy = accumLookY
      accumLookX = 0f
      accumLookY = 0f
    }

    val sprint = touchSprint || padSprint ||
      pressedKeys.contains(KeyEvent.KEYCODE_SHIFT_LEFT) ||
      pressedKeys.contains(KeyEvent.KEYCODE_SHIFT_RIGHT) ||
      pressedKeys.contains(KeyEvent.KEYCODE_BUTTON_R1)

    val breath = touchBreath || padBreath ||
      pressedKeys.contains(KeyEvent.KEYCODE_SPACE) ||
      pressedKeys.contains(KeyEvent.KEYCODE_BUTTON_L1)

    return InputState(
      moveX = mx,
      moveY = my,
      lookDeltaX = ldx,
      lookDeltaY = ldy,
      stickLookX = padLookX,
      stickLookY = padLookY,
      sprintHeld = sprint,
      breathHeld = breath,
      aimBottleHeld = touchAimBottle
    )
  }

  fun clearHeld() {
    pressedKeys.clear()
    touchMoveX = 0f
    touchMoveY = 0f
    touchSprint = false
    touchBreath = false
    touchAimBottle = false
  }

  private fun deadzone(v: Float, dz: Float): Float =
    if (abs(v) < dz) 0f else ((v - dz * kotlin.math.sign(v)) / (1f - dz)).coerceIn(-1f, 1f)
}
