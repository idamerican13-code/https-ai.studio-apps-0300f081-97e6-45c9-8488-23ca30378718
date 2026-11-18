package com.example.game.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.game.model.CheckpointData
import com.example.game.model.SettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.wardZeroDataStore: DataStore<Preferences> by preferencesDataStore(name = "ward_zero_prefs")

class SettingsAndSaveRepository(private val context: Context) {

  private object Keys {
    val SENSITIVITY = floatPreferencesKey("sensitivity")
    val SUBTITLES = booleanPreferencesKey("subtitles")
    val BRIGHTNESS = floatPreferencesKey("brightness")
    val REDUCED_SCARES = booleanPreferencesKey("reduced_scares")
    val MASTER_VOL = floatPreferencesKey("master_vol")
    val MUSIC_VOL = floatPreferencesKey("music_vol")
    val SFX_VOL = floatPreferencesKey("sfx_vol")
    val CONTROL_HINTS = booleanPreferencesKey("control_hints")

    val HAS_CHECKPOINT = booleanPreferencesKey("has_checkpoint")
    val FUSES_COLLECTED = intPreferencesKey("fuses_collected")
    val FUSES_INSTALLED = intPreferencesKey("fuses_installed")
    val NOTES_READ = intPreferencesKey("notes_read")
    val SECRET_LEDGER = booleanPreferencesKey("secret_ledger")
    val PLAYER_X = floatPreferencesKey("player_x")
    val PLAYER_Z = floatPreferencesKey("player_z")
    val PLAYER_YAW = floatPreferencesKey("player_yaw")
    val ELAPSED = floatPreferencesKey("elapsed")
    val BOTTLES = intPreferencesKey("bottles")
    val BATTERY = floatPreferencesKey("battery")
  }

  val settingsFlow: Flow<SettingsState> = context.wardZeroDataStore.data
    .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
    .map { prefs ->
      SettingsState(
        lookSensitivity = prefs[Keys.SENSITIVITY] ?: 1.0f,
        subtitlesEnabled = prefs[Keys.SUBTITLES] ?: true,
        brightness = prefs[Keys.BRIGHTNESS] ?: 1.0f,
        reducedScares = prefs[Keys.REDUCED_SCARES] ?: false,
        masterVolume = prefs[Keys.MASTER_VOL] ?: 0.85f,
        musicVolume = prefs[Keys.MUSIC_VOL] ?: 0.70f,
        sfxVolume = prefs[Keys.SFX_VOL] ?: 0.90f,
        showControlHints = prefs[Keys.CONTROL_HINTS] ?: true
      )
    }

  val checkpointFlow: Flow<CheckpointData> = context.wardZeroDataStore.data
    .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
    .map { prefs ->
      CheckpointData(
        hasCheckpoint = prefs[Keys.HAS_CHECKPOINT] ?: false,
        fusesCollectedMask = prefs[Keys.FUSES_COLLECTED] ?: 0,
        fusesInstalledMask = prefs[Keys.FUSES_INSTALLED] ?: 0,
        notesReadMask = prefs[Keys.NOTES_READ] ?: 0,
        secretLedgerFound = prefs[Keys.SECRET_LEDGER] ?: false,
        playerX = prefs[Keys.PLAYER_X] ?: 9.1f,
        playerZ = prefs[Keys.PLAYER_Z] ?: 39.0f,
        playerYaw = prefs[Keys.PLAYER_YAW] ?: 0f,
        elapsedSeconds = prefs[Keys.ELAPSED] ?: 0f,
        bottlesCount = prefs[Keys.BOTTLES] ?: 1,
        torchBattery = prefs[Keys.BATTERY] ?: 100f
      )
    }

  suspend fun saveSettings(s: SettingsState) {
    context.wardZeroDataStore.edit { prefs ->
      prefs[Keys.SENSITIVITY] = s.lookSensitivity
      prefs[Keys.SUBTITLES] = s.subtitlesEnabled
      prefs[Keys.BRIGHTNESS] = s.brightness
      prefs[Keys.REDUCED_SCARES] = s.reducedScares
      prefs[Keys.MASTER_VOL] = s.masterVolume
      prefs[Keys.MUSIC_VOL] = s.musicVolume
      prefs[Keys.SFX_VOL] = s.sfxVolume
      prefs[Keys.CONTROL_HINTS] = s.showControlHints
    }
  }

  suspend fun saveCheckpoint(cp: CheckpointData) {
    context.wardZeroDataStore.edit { prefs ->
      prefs[Keys.HAS_CHECKPOINT] = cp.hasCheckpoint
      prefs[Keys.FUSES_COLLECTED] = cp.fusesCollectedMask
      prefs[Keys.FUSES_INSTALLED] = cp.fusesInstalledMask
      prefs[Keys.NOTES_READ] = cp.notesReadMask
      prefs[Keys.SECRET_LEDGER] = cp.secretLedgerFound
      prefs[Keys.PLAYER_X] = cp.playerX
      prefs[Keys.PLAYER_Z] = cp.playerZ
      prefs[Keys.PLAYER_YAW] = cp.playerYaw
      prefs[Keys.ELAPSED] = cp.elapsedSeconds
      prefs[Keys.BOTTLES] = cp.bottlesCount
      prefs[Keys.BATTERY] = cp.torchBattery
    }
  }

  suspend fun clearCheckpoint() {
    context.wardZeroDataStore.edit { prefs ->
      prefs[Keys.HAS_CHECKPOINT] = false
    }
  }
}
