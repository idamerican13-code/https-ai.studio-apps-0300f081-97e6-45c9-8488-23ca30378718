package com.example.game.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.game.engine.HapticKind

class HapticManager(context: Context) {
  private val vibrator: Vibrator? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      vm?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  } catch (_: Throwable) {
    null
  }

  fun trigger(kind: HapticKind, reducedScares: Boolean) {
    val vib = vibrator ?: return
    if (!vib.hasVibrator()) return
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val (ms, amp) = when (kind) {
          HapticKind.LIGHT_TAP -> 18L to 70
          HapticKind.HEARTBEAT -> 36L to if (reducedScares) 65 else 135
          HapticKind.DOOR_SLAM -> 75L to if (reducedScares) 90 else 190
          HapticKind.BOTTLE_SHATTER -> 55L to 150
          HapticKind.CAUGHT -> 240L to if (reducedScares) 110 else 255
        }
        vib.vibrate(VibrationEffect.createOneShot(ms, amp))
      } else {
        @Suppress("DEPRECATION")
        vib.vibrate(if (kind == HapticKind.CAUGHT) 200L else 30L)
      }
    } catch (_: Throwable) {
    }
  }
}
