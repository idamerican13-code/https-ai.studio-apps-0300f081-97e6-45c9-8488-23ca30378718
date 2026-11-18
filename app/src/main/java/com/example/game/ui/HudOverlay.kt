package com.example.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.*
import com.example.ui.theme.*
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun HudOverlay(
  state: GameUiState,
  inputHandler: GameInputHandler,
  onInteract: () -> Unit,
  onCycleTorch: () -> Unit,
  onCrankDynamo: () -> Unit,
  onToggleCrouch: () -> Unit,
  onThrowBottle: () -> Unit,
  onPause: () -> Unit,
  onSkipTutorial: () -> Unit
) {
  Box(modifier = Modifier.fillMaxSize()) {
    // Full-right-half touch look drag layer (underneath buttons)
    if (!state.isKeyboardMouseActive) {
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .fillMaxWidth(0.62f)
          .align(Alignment.CenterEnd)
          .pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
              change.consume()
              inputHandler.addLookDelta(dragAmount.x * 1.45f, dragAmount.y * 1.25f)
            }
          }
      )
    }

    // Main safe-drawing HUD layout
    Box(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
      // Top-Left: Zone, Objective & 4 Fuse Slots
      Column(
        modifier = Modifier
          .align(Alignment.TopStart)
          .widthIn(max = 360.dp)
          .background(VoidBlack.copy(alpha = 0.68f), RoundedCornerShape(10.dp))
          .border(1.dp, PeelingTeal.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = "Zone",
            tint = if (state.powerRestored) SafeMint else TorchAmber,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = state.zoneName.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = HospitalCream
          )
          Spacer(modifier = Modifier.width(6.dp))
          // 4 Fuse LEDs
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 4) {
              val installed = i < state.fusesInstalledCount
              val collected = i < state.fusesCollectedCount
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(
                    when {
                      installed -> SafeMint
                      collected -> TorchAmber
                      else -> MutedAsh.copy(alpha = 0.35f)
                    }
                  )
                  .border(1.dp, HospitalCream.copy(alpha = 0.4f), CircleShape)
              )
            }
          }
        }
        Text(
          text = state.objectiveText,
          style = MaterialTheme.typography.bodyMedium,
          color = if (state.powerRestored) SafeMint else AgedPaper
        )
      }

      // Top-Center: Tutorial Banner or Status Banner
      Column(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 4.dp)
          .widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        if (state.tutorialStep != TutorialStep.COMPLETE) {
          Surface(
            color = CorridorSlate.copy(alpha = 0.88f),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TorchAmber.copy(alpha = 0.7f))
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(state.tutorialStep.title, style = MaterialTheme.typography.labelLarge, color = TorchAmber)
                Text(state.tutorialStep.instruction, style = MaterialTheme.typography.bodyMedium, color = HospitalCream)
              }
              TextButton(
                onClick = onSkipTutorial,
                modifier = Modifier.testTag("skip_tutorial_btn")
              ) {
                Text("SKIP", color = PeelingTeal)
              }
            }
          }
        }

        AnimatedVisibility(
          visible = state.statusBanner != null,
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          state.statusBanner?.let { banner ->
            Surface(
              color = VoidBlack.copy(alpha = 0.85f),
              shape = RoundedCornerShape(8.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, TorchAmber.copy(alpha = 0.6f))
            ) {
              Text(
                text = banner,
                style = MaterialTheme.typography.labelLarge,
                color = TorchAmber,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
              )
            }
          }
        }
      }

      // Top-Right: Torch Battery, Bottles, Notes, Pause
      Row(
        modifier = Modifier.align(Alignment.TopEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Surface(
          color = VoidBlack.copy(alpha = 0.70f),
          shape = RoundedCornerShape(10.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, PeelingTeal.copy(alpha = 0.45f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Torch indicator
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Icon(
                imageVector = if (state.torchMode == TorchMode.OFF) Icons.Outlined.FlashlightOff else Icons.Filled.FlashlightOn,
                contentDescription = "Torch state",
                tint = when (state.torchMode) {
                  TorchMode.OFF -> MutedAsh
                  TorchMode.LOW -> PeelingTeal
                  TorchMode.HIGH -> TorchAmber
                },
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = "${state.torchMode.label} ${state.torchBattery.roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.torchBattery < 20f) EmergencyCrimson else HospitalCream
              )
            }

            // Bottles
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
              Icon(Icons.Outlined.Liquor, contentDescription = "Bottles", tint = SafeMint, modifier = Modifier.size(16.dp))
              Text("${state.bottlesCount}/3", style = MaterialTheme.typography.labelSmall, color = HospitalCream)
            }

            // Notes
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
              Icon(Icons.Outlined.Description, contentDescription = "Notes", tint = AgedPaper, modifier = Modifier.size(16.dp))
              Text("${state.notesFoundCount}/${state.totalNotesCount}", style = MaterialTheme.typography.labelSmall, color = AgedPaper)
            }
          }
        }

        IconButton(
          onClick = onPause,
          modifier = Modifier
            .size(48.dp)
            .background(VoidBlack.copy(alpha = 0.72f), CircleShape)
            .border(1.dp, PeelingTeal.copy(alpha = 0.5f), CircleShape)
            .testTag("pause_btn")
        ) {
          Icon(Icons.Filled.Pause, contentDescription = "Pause game", tint = HospitalCream)
        }
      }

      // Center Reticle & Contextual Interaction Prompt
      Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (!state.isHiding) {
          Box(
            modifier = Modifier
              .size(if (state.activePrompt != null) 9.dp else 5.dp)
              .clip(CircleShape)
              .background(
                if (state.isAimingBottle) SafeMint
                else if (state.activePrompt != null) TorchAmber
                else HospitalCream.copy(alpha = 0.55f)
              )
          )
        }

        state.activePrompt?.let { prompt ->
          Surface(
            color = VoidBlack.copy(alpha = 0.85f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TorchAmber),
            modifier = Modifier
              .clickable { onInteract() }
              .minimumInteractiveComponentSize()
              .testTag("prompt_interact_pill")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = if (state.isGamepadConnected) "[A]" else if (state.isKeyboardMouseActive) "[E]" else "TAP",
                style = MaterialTheme.typography.labelLarge,
                color = VoidBlack,
                modifier = Modifier
                  .background(TorchAmber, RoundedCornerShape(4.dp))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              )
              Text(prompt.text, style = MaterialTheme.typography.bodyLarge, color = HospitalCream)
            }
          }
        }
      }

      // Locker Breath-Hold Meter & Release Window (when hiding!)
      if (state.isHiding) {
        Column(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 18.dp)
            .widthIn(max = 360.dp)
            .background(VoidBlack.copy(alpha = 0.82f), RoundedCornerShape(12.dp))
            .border(
              1.dp,
              if (state.breathDangerActive) EmergencyCrimson else PeelingTeal,
              RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          val inSweetSpot = state.breathMeter in state.breathSweetSpotMin..state.breathSweetSpotMax
          Text(
            text = when {
              state.isHoldingBreath && inSweetSpot -> "RELEASE NOW — SAFE EXHALE WINDOW"
              state.isHoldingBreath -> "HOLDING BREATH — RELEASE IN GREEN ZONE"
              state.breathDangerActive -> "MATRON LIM NEARBY — HOLD BREATH!"
              else -> "CONCEALED IN LOCKER — PEEK THROUGH SLATS"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
              inSweetSpot -> SafeMint
              state.breathDangerActive -> EmergencyCrimson
              else -> AgedPaper
            }
          )

          // Breath Meter with Sweet Spot Marker
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(14.dp)
              .clip(RoundedCornerShape(7.dp))
              .background(WardSurface)
              .border(1.dp, MutedAsh.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
          ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              // Sweet spot zone (18%..40%)
              val s0 = (state.breathSweetSpotMin / 100f) * size.width
              val s1 = (state.breathSweetSpotMax / 100f) * size.width
              drawRect(
                color = SafeMint.copy(alpha = 0.38f),
                topLeft = Offset(s0, 0f),
                size = Size(s1 - s0, size.height)
              )
              val fillW = (state.breathMeter / 100f) * size.width
              val barColor = when {
                inSweetSpot -> SafeMint
                state.breathMeter < 18f -> EmergencyCrimson
                else -> TorchAmber
              }
              drawRect(color = barColor, size = Size(fillW, size.height))
            }
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Press-and-hold Breath Button
            Box(
              modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (state.isHoldingBreath) SafeMint.copy(alpha = 0.85f) else WardElevated)
                .border(1.dp, if (state.isHoldingBreath) SafeMint else TorchAmber, RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                  detectTapGestures(
                    onPress = {
                      inputHandler.touchBreath = true
                      tryAwaitRelease()
                      inputHandler.touchBreath = false
                    }
                  )
                }
                .padding(horizontal = 18.dp)
                .testTag("breath_btn"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = if (state.isHoldingBreath) "RELEASING..." else "HOLD BREATH [SPACE]",
                style = MaterialTheme.typography.labelLarge,
                color = if (state.isHoldingBreath) VoidBlack else HospitalCream
              )
            }

            OutlinedButton(
              onClick = onInteract,
              modifier = Modifier
                .height(48.dp)
                .testTag("exit_locker_btn")
            ) {
              Text("EXIT LOCKER", color = HospitalCream)
            }
          }
        }
      }

      // Accessibility Subtitles / Audio Cues
      state.subtitleCue?.let { cue ->
        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = if (state.isHiding) 142.dp else 72.dp)
            .background(VoidBlack.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
            .border(
              1.dp,
              if (cue.isUrgent) EmergencyCrimson.copy(alpha = 0.8f) else PeelingTeal.copy(alpha = 0.4f),
              RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
          Text(
            text = cue.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (cue.isUrgent) Color(0xFFFFB4B4) else HospitalCream
          )
        }
      }

      // Touch Virtual Thumbstick & Action Buttons (shown when not hiding and not solely using KBM)
      if (!state.isHiding) {
        // Left Virtual Analog Stick
        VirtualThumbstick(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 8.dp, bottom = 8.dp),
          onMove = { mx, my ->
            inputHandler.touchMoveX = mx
            inputHandler.touchMoveY = my
          }
        )

        // Bottom-center Stamina + Stealth status bar
        Row(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 8.dp)
            .background(VoidBlack.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = when {
              state.isCrouching -> "CROUCHED (STEALTH)"
              state.isSprinting -> "SPRINTING (LOUD)"
              else -> "STAMINA"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (state.isSprinting) EmergencyCrimson else if (state.isCrouching) SafeMint else MutedAsh
          )
          LinearProgressIndicator(
            progress = { (state.stamina / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
              .width(86.dp)
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp)),
            color = if (state.stamina < 25f) EmergencyCrimson else PeelingTeal,
            trackColor = WardSurface
          )
        }

        // Right-side tactile action pad
        Column(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 6.dp, bottom = 6.dp),
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.torchBattery < 75f) {
              HudActionButton(
                label = "CRANK",
                icon = Icons.Outlined.BatteryChargingFull,
                active = false,
                accentColor = TorchAmber,
                tag = "crank_btn",
                onClick = onCrankDynamo
              )
            }
            HudActionButton(
              label = state.torchMode.label,
              icon = if (state.torchMode == TorchMode.OFF) Icons.Outlined.FlashlightOff else Icons.Filled.FlashlightOn,
              active = state.torchMode != TorchMode.OFF,
              accentColor = if (state.torchMode == TorchMode.HIGH) TorchAmber else PeelingTeal,
              tag = "torch_btn",
              onClick = onCycleTorch
            )
            HudActionButton(
              label = "CROUCH",
              icon = Icons.Outlined.AirlineSeatLegroomReduced,
              active = state.isCrouching,
              accentColor = SafeMint,
              tag = "crouch_btn",
              onClick = onToggleCrouch
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudActionButton(
              label = "THROW (${state.bottlesCount})",
              icon = Icons.Outlined.Liquor,
              active = state.isAimingBottle,
              accentColor = SafeMint,
              enabled = state.bottlesCount > 0,
              tag = "throw_btn",
              onClick = onThrowBottle
            )

            // Sprint hold button
            Box(
              modifier = Modifier
                .size(width = 62.dp, height = 54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (state.isSprinting) EmergencyCrimson.copy(alpha = 0.8f) else VoidBlack.copy(alpha = 0.72f))
                .border(
                  1.dp,
                  if (state.isSprinting) EmergencyCrimson else PeelingTeal.copy(alpha = 0.55f),
                  RoundedCornerShape(12.dp)
                )
                .pointerInput(Unit) {
                  detectTapGestures(
                    onPress = {
                      inputHandler.touchSprint = true
                      tryAwaitRelease()
                      inputHandler.touchSprint = false
                    }
                  )
                }
                .testTag("sprint_btn"),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.DirectionsRun, contentDescription = "Hold to Sprint", tint = HospitalCream, modifier = Modifier.size(20.dp))
                Text("SPRINT", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = HospitalCream)
              }
            }

            HudActionButton(
              label = state.activePrompt?.actionLabel ?: "USE",
              icon = Icons.Filled.TouchApp,
              active = state.activePrompt != null,
              accentColor = TorchAmber,
              tag = "interact_btn",
              onClick = onInteract
            )
          }
        }
      }

      // Desktop/Gamepad Keybind Strip when active & hints enabled
      if (state.settings.showControlHints && (state.isKeyboardMouseActive || state.isGamepadConnected) && !state.isHiding) {
        Text(
          text = if (state.isGamepadConnected) {
            "L-Stick: Move • R-Stick: Look • A: Use • B: Crouch • X: Torch • Y: Bottle • RT: Sprint • LT: Breath"
          } else {
            "WASD: Move • Drag/Mouse: Look • E: Use • F: Torch • R: Crank • C: Crouch • Q: Bottle • Shift: Sprint • Esc: Pause"
          },
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = MutedAsh,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 36.dp)
        )
      }
    }
  }
}

@Composable
private fun HudActionButton(
  label: String,
  icon: ImageVector,
  active: Boolean,
  accentColor: Color,
  tag: String,
  enabled: Boolean = true,
  onClick: () -> Unit
) {
  val bg = if (active) accentColor.copy(alpha = 0.28f) else VoidBlack.copy(alpha = 0.72f)
  val border = if (active) accentColor else PeelingTeal.copy(alpha = 0.45f)
  val contentCol = if (!enabled) MutedAsh.copy(alpha = 0.4f) else if (active) accentColor else HospitalCream

  Box(
    modifier = Modifier
      .size(width = 62.dp, height = 54.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(bg)
      .border(1.dp, border, RoundedCornerShape(12.dp))
      .clickable(enabled = enabled, onClick = onClick)
      .testTag(tag),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(icon, contentDescription = label, tint = contentCol, modifier = Modifier.size(20.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
        color = contentCol,
        maxLines = 1
      )
    }
  }
}

@Composable
private fun VirtualThumbstick(
  modifier: Modifier = Modifier,
  onMove: (Float, Float) -> Unit
) {
  var knobOffset by remember { mutableStateOf(Offset.Zero) }
  val maxRadiusPx = 110f

  Box(
    modifier = modifier
      .size(116.dp)
      .clip(CircleShape)
      .background(
        Brush.radialGradient(
          listOf(VoidBlack.copy(alpha = 0.35f), VoidBlack.copy(alpha = 0.72f))
        )
      )
      .border(1.5.dp, PeelingTeal.copy(alpha = 0.45f), CircleShape)
      .pointerInput(Unit) {
        detectDragGestures(
          onDragEnd = {
            knobOffset = Offset.Zero
            onMove(0f, 0f)
          },
          onDragCancel = {
            knobOffset = Offset.Zero
            onMove(0f, 0f)
          }
        ) { change, dragAmount ->
          change.consume()
          val next = knobOffset + dragAmount
          val dist = hypot(next.x, next.y)
          knobOffset = if (dist > maxRadiusPx) {
            Offset(next.x * maxRadiusPx / dist, next.y * maxRadiusPx / dist)
          } else {
            next
          }
          onMove(
            (knobOffset.x / maxRadiusPx).coerceIn(-1f, 1f),
            (-knobOffset.y / maxRadiusPx).coerceIn(-1f, 1f)
          )
        }
      },
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .offset { IntOffset(knobOffset.x.roundToInt(), knobOffset.y.roundToInt()) }
        .size(46.dp)
        .clip(CircleShape)
        .background(PeelingTeal.copy(alpha = 0.45f))
        .border(1.5.dp, HospitalCream.copy(alpha = 0.7f), CircleShape)
    )
  }
}
