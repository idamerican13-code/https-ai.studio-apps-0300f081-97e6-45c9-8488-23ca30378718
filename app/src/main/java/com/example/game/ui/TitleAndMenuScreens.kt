package com.example.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.model.EndingType
import com.example.game.model.GameUiState
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun TitleScreen(
  state: GameUiState,
  onStartNewShift: () -> Unit,
  onContinueCheckpoint: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenNotes: () -> Unit
) {
  Box(modifier = Modifier.fillMaxSize().background(VoidBlack)) {
    Image(
      painter = painterResource(id = R.drawable.img_changi_corridor),
      contentDescription = "Old Changi Hospital dark corridor",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
      alpha = 0.62f
    )
    // Dramatic radial/horizontal dark gradient so typography pops crisply
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.horizontalGradient(
            listOf(
              VoidBlack.copy(alpha = 0.94f),
              VoidBlack.copy(alpha = 0.72f),
              VoidBlack.copy(alpha = 0.35f)
            )
          )
        )
    )

    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
      val isWide = maxWidth >= 620.dp
      if (isWide) {
        Row(
          modifier = Modifier.fillMaxSize(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          TitlePrimaryColumn(
            state = state,
            modifier = Modifier.weight(1f).widthIn(max = 500.dp),
            onStartNewShift = onStartNewShift,
            onContinueCheckpoint = onContinueCheckpoint,
            onOpenSettings = onOpenSettings,
            onOpenNotes = onOpenNotes
          )
          TitleRulesCard(
            modifier = Modifier
              .padding(start = 24.dp)
              .widthIn(min = 240.dp, max = 300.dp)
          )
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          TitlePrimaryColumn(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            onStartNewShift = onStartNewShift,
            onContinueCheckpoint = onContinueCheckpoint,
            onOpenSettings = onOpenSettings,
            onOpenNotes = onOpenNotes
          )
          TitleRulesCard(modifier = Modifier.fillMaxWidth())
        }
      }
    }
  }
}

@Composable
private fun TitlePrimaryColumn(
  state: GameUiState,
  modifier: Modifier,
  onStartNewShift: () -> Unit,
  onContinueCheckpoint: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenNotes: () -> Unit
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Text(
      text = "OLD CHANGI HOSPITAL • WING C",
      style = MaterialTheme.typography.labelLarge,
      color = PeelingTeal
    )
    Text(
      text = "WARD ZERO",
      style = MaterialTheme.typography.displayLarge,
      color = HospitalCream
    )
    Text(
      text = "She hunts every flicker of light and scrape of sound. Find four fuses, hide behind rusted steel, hold your breath, and restore power before the dark becomes permanent.",
      style = MaterialTheme.typography.bodyLarge,
      color = AgedPaper
    )

    Spacer(modifier = Modifier.height(6.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Button(
        onClick = onStartNewShift,
        modifier = Modifier.height(50.dp).testTag("start_new_shift_btn")
      ) {
        Icon(Icons.Outlined.FlashlightOn, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("START SHIFT")
      }

      if (state.hasCheckpoint) {
        OutlinedButton(
          onClick = onContinueCheckpoint,
          border = BorderStroke(1.dp, TorchAmber),
          modifier = Modifier.height(50.dp).testTag("continue_checkpoint_btn")
        ) {
          Text("CONTINUE CHECKPOINT", color = TorchAmber)
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      TextButton(
        onClick = onOpenSettings,
        modifier = Modifier.height(48.dp).testTag("settings_btn")
      ) {
        Icon(Icons.Outlined.Settings, contentDescription = null, tint = HospitalCream, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("ACCESSIBILITY & SETTINGS", color = HospitalCream)
      }

      TextButton(
        onClick = onOpenNotes,
        modifier = Modifier.height(48.dp).testTag("title_notes_btn")
      ) {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = AgedPaper, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("FIELD NOTES", color = AgedPaper)
      }
    }
  }
}

@Composable
private fun TitleRulesCard(modifier: Modifier = Modifier) {
  Surface(
    color = CorridorSlate.copy(alpha = 0.80f),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, PeelingTeal.copy(alpha = 0.45f)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text("NIGHT SHIFT RULES", style = MaterialTheme.typography.titleMedium, color = TorchAmber)
      RuleBullet("TORCH", "LOW hides your beam; HIGH reveals clues but draws Matron Lim.")
      RuleBullet("SOUND", "Sprinting, slammed doors, and metal trays alert her. Crouch to silence steps.")
      RuleBullet("LURES", "Throw glass bottles to pull her down opposite corridors.")
      RuleBullet("LOCKERS", "Hide inside steel lockers and release breath inside the green window.")
    }
  }
}

@Composable
private fun RuleBullet(tag: String, text: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
    Text(
      text = tag,
      style = MaterialTheme.typography.labelSmall,
      color = VoidBlack,
      modifier = Modifier
        .background(PeelingTeal, RoundedCornerShape(4.dp))
        .padding(horizontal = 5.dp, vertical = 2.dp)
    )
    Text(text, style = MaterialTheme.typography.bodyMedium, color = HospitalCream)
  }
}

@Composable
fun IntroBriefingScreen(
  onBeginWithTutorial: () -> Unit,
  onSkipTutorial: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(VoidBlack)
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      color = CorridorSlate,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, TorchAmber.copy(alpha = 0.6f)),
      modifier = Modifier
        .widthIn(min = 360.dp, max = 600.dp)
        .padding(20.dp)
    ) {
      Column(
        modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text("23:42 — OLD CHANGI HOSPITAL, WING C", style = MaterialTheme.typography.labelLarge, color = TorchAmber)
        Text("THE BULKHEAD LOCKED BEHIND YOU", style = MaterialTheme.typography.headlineMedium, color = HospitalCream)
        Text(
          "The demolition survey was supposed to take twenty minutes. Then the substation breaker tripped, sealing the South Exit Shutter.",
          style = MaterialTheme.typography.bodyLarge,
          color = AgedPaper
        )
        Text(
          "Four industrial fuses were pulled from the Central Breaker Room and scattered across Isolation Ward B, the Operating Theatre, Cryo Storage, and the North Boiler Stairwell. Matron Lim still makes her rounds through the dark.",
          style = MaterialTheme.typography.bodyLarge,
          color = HospitalCream
        )
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
          OutlinedButton(
            onClick = onSkipTutorial,
            modifier = Modifier.height(48.dp).testTag("intro_skip_tutorial_btn")
          ) {
            Text("SKIP TUTORIAL", color = PeelingTeal)
          }
          Button(
            onClick = onBeginWithTutorial,
            modifier = Modifier.height(48.dp).testTag("intro_begin_btn")
          ) {
            Text("STEP INTO VESTIBULE")
          }
        }
      }
    }
  }
}

@Composable
fun CaughtScreen(
  state: GameUiState,
  onRetryCheckpoint: () -> Unit,
  onRestartShift: () -> Unit,
  onReturnTitle: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.radialGradient(
          listOf(Color(0xFF3A0707), VoidBlack)
        )
      )
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      color = CorridorSlate.copy(alpha = 0.92f),
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.5.dp, EmergencyCrimson),
      modifier = Modifier
        .widthIn(min = 340.dp, max = 520.dp)
        .padding(20.dp)
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text("PERMANENT DARK", style = MaterialTheme.typography.displayLarge, color = EmergencyCrimson)
        Text(
          "Matron Lim found you in ${state.zoneName}. Fuses restored: ${state.fusesInstalledCount}/4.",
          style = MaterialTheme.typography.bodyLarge,
          color = HospitalCream
        )
        Text(
          EndingType.PERMANENT_DARK.epilogue,
          style = MaterialTheme.typography.bodyMedium,
          color = AgedPaper
        )
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
          if (state.hasCheckpoint) {
            Button(
              onClick = onRetryCheckpoint,
              modifier = Modifier.height(48.dp).testTag("retry_checkpoint_btn")
            ) {
              Text("RETRY CHECKPOINT")
            }
          }
          OutlinedButton(
            onClick = onRestartShift,
            modifier = Modifier.height(48.dp).testTag("restart_shift_btn")
          ) {
            Text("RESTART SHIFT", color = HospitalCream)
          }
          TextButton(
            onClick = onReturnTitle,
            modifier = Modifier.height(48.dp).testTag("caught_title_btn")
          ) {
            Text("TITLE", color = MutedAsh)
          }
        }
      }
    }
  }
}

@Composable
fun EndingScreen(
  state: GameUiState,
  onPlayAgain: () -> Unit,
  onReturnTitle: () -> Unit
) {
  val mins = (state.elapsedSeconds / 60).toInt()
  val secs = (state.elapsedSeconds % 60).roundToInt()
  val timeStr = "%02d:%02d".format(mins, secs)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(VoidBlack)
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      color = CorridorSlate,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(
        1.5.dp,
        if (state.endingType == EndingType.TRUTH_LEDGER) TorchAmber else SafeMint
      ),
      modifier = Modifier
        .widthIn(min = 380.dp, max = 620.dp)
        .padding(20.dp)
    ) {
      Column(
        modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = state.endingType.subtitle.uppercase(),
          style = MaterialTheme.typography.labelLarge,
          color = if (state.endingType == EndingType.TRUTH_LEDGER) TorchAmber else SafeMint
        )
        Text(state.endingType.title.uppercase(), style = MaterialTheme.typography.displayLarge, color = HospitalCream)
        Text(state.endingType.epilogue, style = MaterialTheme.typography.bodyLarge, color = AgedPaper)

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(WardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, PeelingTeal.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(14.dp),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          StatPill("TIME", timeStr)
          StatPill("FUSES", "${state.fusesInstalledCount}/4")
          StatPill("NOTES", "${state.notesFoundCount}/${state.totalNotesCount}")
          StatPill("SPOTTED", "${state.timesSpotted}")
        }

        if (state.endingType != EndingType.TRUTH_LEDGER) {
          Text(
            "Hint: Search the locked east wing off Records & Pharmacy to uncover Matron Lim's Sealed Ledger for the True Ending.",
            style = MaterialTheme.typography.bodyMedium,
            color = PeelingTeal
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
          OutlinedButton(
            onClick = onReturnTitle,
            modifier = Modifier.height(48.dp).testTag("ending_title_btn")
          ) {
            Text("TITLE MENU", color = HospitalCream)
          }
          Button(
            onClick = onPlayAgain,
            modifier = Modifier.height(48.dp).testTag("ending_replay_btn")
          ) {
            Text("PLAY AGAIN")
          }
        }
      }
    }
  }
}

@Composable
private fun StatPill(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MutedAsh)
    Text(value, style = MaterialTheme.typography.headlineMedium, color = HospitalCream)
  }
}
