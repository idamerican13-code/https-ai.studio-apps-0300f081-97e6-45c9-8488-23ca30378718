package com.example.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.game.model.LoreNote
import com.example.game.model.SettingsState
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun PauseModal(
  onResume: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenCodex: () -> Unit,
  onRestartCheckpoint: () -> Unit,
  hasCheckpoint: Boolean,
  onExitToTitle: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(VoidBlack.copy(alpha = 0.82f))
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      color = CorridorSlate,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, PeelingTeal.copy(alpha = 0.65f)),
      modifier = Modifier
        .widthIn(min = 320.dp, max = 440.dp)
        .padding(16.dp)
    ) {
      Column(
        modifier = Modifier
          .padding(22.dp)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text("SHIFT PAUSED", style = MaterialTheme.typography.headlineMedium, color = TorchAmber)
        Text("Old Changi Hospital — Wing C", style = MaterialTheme.typography.bodyMedium, color = MutedAsh)
        Spacer(modifier = Modifier.height(6.dp))

        Button(
          onClick = onResume,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("resume_btn")
        ) {
          Text("RESUME SHIFT")
        }

        if (hasCheckpoint) {
          OutlinedButton(
            onClick = onRestartCheckpoint,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_checkpoint_btn")
          ) {
            Text("RELOAD LAST CHECKPOINT", color = HospitalCream)
          }
        }

        OutlinedButton(
          onClick = onOpenCodex,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_notes_btn")
        ) {
          Icon(Icons.Outlined.Description, contentDescription = null, tint = AgedPaper, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("FIELD NOTES", color = AgedPaper)
        }

        OutlinedButton(
          onClick = onOpenSettings,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_settings_btn")
        ) {
          Icon(Icons.Outlined.Settings, contentDescription = null, tint = HospitalCream, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("ACCESSIBILITY & SETTINGS", color = HospitalCream)
        }

        TextButton(
          onClick = onExitToTitle,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("exit_title_btn")
        ) {
          Text("RETURN TO TITLE", color = EmergencyCrimson)
        }
      }
    }
  }
}

@Composable
fun NoteReaderModal(
  note: LoreNote,
  onClose: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(VoidBlack.copy(alpha = 0.85f))
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      color = Color(0xFF1F1D18),
      shape = RoundedCornerShape(10.dp),
      border = BorderStroke(1.5.dp, if (note.isSecretLedger) TorchAmber else AgedPaper.copy(alpha = 0.55f)),
      modifier = Modifier
        .widthIn(min = 340.dp, max = 560.dp)
        .padding(20.dp)
    ) {
      Column(
        modifier = Modifier
          .padding(22.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = note.title.uppercase(),
              style = MaterialTheme.typography.headlineMedium,
              color = if (note.isSecretLedger) TorchAmber else AgedPaper
            )
            Text(note.subtitle, style = MaterialTheme.typography.labelSmall, color = MutedAsh)
          }
          IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("close_note_btn")) {
            Icon(Icons.Filled.Close, contentDescription = "Close note", tint = HospitalCream)
          }
        }
        HorizontalDivider(color = AgedPaper.copy(alpha = 0.25f))
        Text(
          text = note.body,
          style = MaterialTheme.typography.bodyLarge,
          color = HospitalCream
        )
        Spacer(modifier = Modifier.height(6.dp))
        Button(
          onClick = onClose,
          modifier = Modifier.align(Alignment.End).height(48.dp).testTag("resume_after_note_btn")
        ) {
          Text("STOW DOCUMENT")
        }
      }
    }
  }
}

@Composable
fun CodexModal(
  notes: List<LoreNote>,
  totalCount: Int,
  onClose: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(VoidBlack.copy(alpha = 0.88f))
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      color = CorridorSlate,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, PeelingTeal.copy(alpha = 0.65f)),
      modifier = Modifier
        .widthIn(min = 360.dp, max = 640.dp)
        .fillMaxHeight(0.86f)
        .padding(16.dp)
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("FIELD NOTES (${notes.size} / $totalCount)", style = MaterialTheme.typography.headlineMedium, color = TorchAmber)
            Text("Discover hospital logs to unlock the full truth of Wing C", style = MaterialTheme.typography.bodyMedium, color = MutedAsh)
          }
          IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("close_codex_btn")) {
            Icon(Icons.Filled.Close, contentDescription = "Close field notes", tint = HospitalCream)
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(
          modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (notes.isEmpty()) {
            Text(
              "No notes collected yet. Look for clipboards and glowing documents on desks and ward floors.",
              style = MaterialTheme.typography.bodyLarge,
              color = MutedAsh
            )
          } else {
            notes.forEach { n ->
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(WardSurface, RoundedCornerShape(8.dp))
                  .border(1.dp, if (n.isSecretLedger) TorchAmber.copy(alpha = 0.6f) else PeelingTeal.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                  .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(n.title, style = MaterialTheme.typography.titleMedium, color = if (n.isSecretLedger) TorchAmber else AgedPaper)
                Text(n.subtitle, style = MaterialTheme.typography.labelSmall, color = MutedAsh)
                Text(n.body, style = MaterialTheme.typography.bodyMedium, color = HospitalCream)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun SettingsModal(
  settings: SettingsState,
  onUpdate: ((SettingsState) -> SettingsState) -> Unit,
  onClose: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(VoidBlack.copy(alpha = 0.88f))
      .windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      color = CorridorSlate,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(1.dp, PeelingTeal.copy(alpha = 0.65f)),
      modifier = Modifier
        .widthIn(min = 360.dp, max = 600.dp)
        .fillMaxHeight(0.88f)
        .padding(16.dp)
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("ACCESSIBILITY & SETTINGS", style = MaterialTheme.typography.headlineMedium, color = TorchAmber)
          IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("close_settings_btn")) {
            Icon(Icons.Filled.Close, contentDescription = "Close settings", tint = HospitalCream)
          }
        }
        HorizontalDivider(color = PeelingTeal.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

        Column(
          modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          SettingSliderRow(
            label = "Look Sensitivity",
            valueText = "${(settings.lookSensitivity * 100).roundToInt()}%",
            value = settings.lookSensitivity,
            range = 0.4f..2.2f,
            onChange = { v -> onUpdate { it.copy(lookSensitivity = v) } }
          )
          SettingSliderRow(
            label = "Brightness / Gamma",
            valueText = "${(settings.brightness * 100).roundToInt()}%",
            value = settings.brightness,
            range = 0.5f..1.8f,
            onChange = { v -> onUpdate { it.copy(brightness = v) } }
          )
          SettingSliderRow(
            label = "Master Volume",
            valueText = "${(settings.masterVolume * 100).roundToInt()}%",
            value = settings.masterVolume,
            range = 0f..1f,
            onChange = { v -> onUpdate { it.copy(masterVolume = v) } }
          )
          SettingSliderRow(
            label = "Music Tension Volume",
            valueText = "${(settings.musicVolume * 100).roundToInt()}%",
            value = settings.musicVolume,
            range = 0f..1f,
            onChange = { v -> onUpdate { it.copy(musicVolume = v) } }
          )
          SettingSliderRow(
            label = "SFX Volume",
            valueText = "${(settings.sfxVolume * 100).roundToInt()}%",
            value = settings.sfxVolume,
            range = 0f..1f,
            onChange = { v -> onUpdate { it.copy(sfxVolume = v) } }
          )

          SettingToggleRow(
            title = "Sound Subtitles & Danger Captions",
            subtitle = "Shows directional audio cues for footsteps, bottles, and breathing",
            checked = settings.subtitlesEnabled,
            onToggle = { c -> onUpdate { it.copy(subtitlesEnabled = c) } }
          )
          SettingToggleRow(
            title = "Reduced Scares Mode",
            subtitle = "Softens torch flicker strobing, camera shake, and red flash intensity",
            checked = settings.reducedScares,
            onToggle = { c -> onUpdate { it.copy(reducedScares = c) } }
          )
          SettingToggleRow(
            title = "Show Keyboard & Gamepad Hints",
            subtitle = "Displays desktop/controller bindings along the bottom HUD",
            checked = settings.showControlHints,
            onToggle = { c -> onUpdate { it.copy(showControlHints = c) } }
          )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
          onClick = onClose,
          modifier = Modifier.align(Alignment.End).height(48.dp).testTag("done_settings_btn")
        ) {
          Text("SAVE & CLOSE")
        }
      }
    }
  }
}

@Composable
private fun SettingSliderRow(
  label: String,
  valueText: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onChange: (Float) -> Unit
) {
  Column {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, style = MaterialTheme.typography.titleMedium, color = HospitalCream)
      Text(valueText, style = MaterialTheme.typography.bodyLarge, color = TorchAmber)
    }
    Slider(value = value, onValueChange = onChange, valueRange = range)
  }
}

@Composable
private fun SettingToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onToggle: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(WardSurface, RoundedCornerShape(8.dp))
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = HospitalCream)
      Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MutedAsh)
    }
    Switch(checked = checked, onCheckedChange = onToggle)
  }
}
