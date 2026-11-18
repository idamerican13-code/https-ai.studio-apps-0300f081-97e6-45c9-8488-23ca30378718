package com.example.game.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.audio.HapticManager
import com.example.game.audio.HorrorAudioEngine
import com.example.game.data.SettingsAndSaveRepository
import com.example.game.engine.*
import com.example.game.model.*
import com.example.game.renderer.HospitalGlRenderer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.hypot

class WardZeroViewModel(app: Application) : AndroidViewModel(app) {

  private val repo = SettingsAndSaveRepository(app.applicationContext)
  private val audio = HorrorAudioEngine()
  private val haptics = HapticManager(app.applicationContext)
  val simulation = GameSimulation()

  private val _uiState = MutableStateFlow(GameUiState())
  val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

  private var latestCheckpoint = CheckpointData()
  private var bannerJob: Job? = null
  private var subtitleJob: Job? = null
  private var subtitleSeq = 0L
  private var uiSyncAccum = 0f

  // Guard cross-thread GL -> Main mutations
  private val simLock = Any()

  val inputHandler = GameInputHandler(
    onInteract = { synchronized(simLock) { if (isPlayable()) simulation.interact() } },
    onCycleTorch = { synchronized(simLock) { if (isPlayable()) simulation.cycleTorch() } },
    onCrankDynamo = { synchronized(simLock) { if (isPlayable()) simulation.crankTorchDynamo() } },
    onToggleCrouch = { synchronized(simLock) { if (isPlayable()) simulation.toggleCrouch() } },
    onThrowBottle = { synchronized(simLock) { if (isPlayable()) simulation.throwBottle() } },
    onPauseToggle = { togglePause() },
    onDeviceModeChanged = { pad, kbm ->
      if (_uiState.value.isGamepadConnected != pad || _uiState.value.isKeyboardMouseActive != kbm) {
        _uiState.update { it.copy(isGamepadConnected = pad, isKeyboardMouseActive = kbm) }
      }
    }
  )

  val glRenderer = HospitalGlRenderer(
    simProvider = { simulation },
    settingsProvider = { _uiState.value.settings },
    onFrameTick = { dt -> onGlFrame(dt) }
  )

  init {
    audio.start()
    viewModelScope.launch {
      repo.settingsFlow.collect { s ->
        audio.applySettings(s)
        _uiState.update { it.copy(settings = s) }
      }
    }
    viewModelScope.launch {
      repo.checkpointFlow.collect { cp ->
        latestCheckpoint = cp
        _uiState.update { it.copy(hasCheckpoint = cp.hasCheckpoint) }
      }
    }
  }

  override fun onCleared() {
    audio.stop()
    super.onCleared()
  }

  fun onAppPause() {
    if (_uiState.value.screen == ScreenState.PLAYING) {
      pauseGame()
    }
    audio.isPlaying = false
  }

  private fun isPlayable(): Boolean = _uiState.value.screen == ScreenState.PLAYING

  fun startNewShift(showIntro: Boolean = true) {
    synchronized(simLock) {
      simulation.resetNewGame(skipTutorial = false)
    }
    inputHandler.clearHeld()
    _uiState.update {
      it.copy(
        screen = if (showIntro) ScreenState.INTRO else ScreenState.PLAYING,
        collectedNotes = emptyList(),
        activeNote = null,
        statusBanner = null,
        subtitleCue = null
      )
    }
    syncUiFromSim()
  }

  fun enterWingFromIntro(skipTutorial: Boolean) {
    synchronized(simLock) {
      if (skipTutorial) {
        simulation.skipTutorial()
      }
    }
    _uiState.update { it.copy(screen = ScreenState.PLAYING) }
    syncUiFromSim()
  }

  fun continueFromCheckpoint() {
    if (!latestCheckpoint.hasCheckpoint) {
      startNewShift(showIntro = false)
      return
    }
    synchronized(simLock) {
      simulation.restoreFromCheckpoint(latestCheckpoint)
    }
    inputHandler.clearHeld()
    _uiState.update {
      it.copy(
        screen = ScreenState.PLAYING,
        activeNote = null,
        statusBanner = "Checkpoint Restored — Stay quiet",
        subtitleCue = null
      )
    }
    syncUiFromSim()
  }

  fun pauseGame() {
    if (_uiState.value.screen == ScreenState.PLAYING) {
      inputHandler.clearHeld()
      _uiState.update { it.copy(screen = ScreenState.PAUSED) }
    }
  }

  fun resumeGame() {
    if (_uiState.value.screen == ScreenState.PAUSED || _uiState.value.screen == ScreenState.NOTE_READER) {
      inputHandler.clearHeld()
      _uiState.update { it.copy(screen = ScreenState.PLAYING, activeNote = null) }
    }
  }

  fun togglePause() {
    when (_uiState.value.screen) {
      ScreenState.PLAYING -> pauseGame()
      ScreenState.PAUSED, ScreenState.NOTE_READER -> resumeGame()
      else -> {}
    }
  }

  fun returnToTitle() {
    inputHandler.clearHeld()
    _uiState.update { it.copy(screen = ScreenState.TITLE, activeNote = null) }
  }

  fun skipTutorialInGame() {
    synchronized(simLock) {
      simulation.skipTutorial()
    }
    syncUiFromSim()
  }

  fun interact() {
    synchronized(simLock) {
      if (isPlayable()) simulation.interact()
    }
    syncUiFromSim()
  }

  fun cycleTorch() {
    synchronized(simLock) {
      if (isPlayable()) simulation.cycleTorch()
    }
    syncUiFromSim()
  }

  fun crankDynamo() {
    synchronized(simLock) {
      if (isPlayable()) simulation.crankTorchDynamo()
    }
    syncUiFromSim()
  }

  fun toggleCrouch() {
    synchronized(simLock) {
      if (isPlayable()) simulation.toggleCrouch()
    }
    syncUiFromSim()
  }

  fun throwBottle() {
    synchronized(simLock) {
      if (isPlayable()) simulation.throwBottle()
    }
    syncUiFromSim()
  }

  fun updateSettings(transform: (SettingsState) -> SettingsState) {
    val updated = transform(_uiState.value.settings)
    audio.applySettings(updated)
    _uiState.update { it.copy(settings = updated) }
    viewModelScope.launch {
      repo.saveSettings(updated)
    }
  }

  private fun onGlFrame(dt: Float) {
    val currentScreen = _uiState.value.screen
    if (currentScreen == ScreenState.TITLE || currentScreen == ScreenState.INTRO) {
      // Gentle atmospheric idle camera sway in background
      synchronized(simLock) {
        simulation.elapsedSeconds += dt
        simulation.playerYaw = kotlin.math.sin(simulation.elapsedSeconds * 0.18f) * 0.25f
      }
      audio.updateAtmosphere(
        playing = false,
        nurseDist = 20f,
        alertState = NurseAlertState.PATROL,
        powerOn = false,
        hiding = false
      )
      return
    }

    if (currentScreen != ScreenState.PLAYING) {
      audio.updateAtmosphere(
        playing = false,
        nurseDist = 25f,
        alertState = NurseAlertState.PATROL,
        powerOn = simulation.powerRestored,
        hiding = simulation.isHiding
      )
      return
    }

    val eventsToProcess: List<SimEvent>
    synchronized(simLock) {
      val input = inputHandler.consumeInputState()
      simulation.step(dt, input, _uiState.value.settings)
      eventsToProcess = simulation.pendingEvents.toList()
      simulation.pendingEvents.clear()
    }

    val nurseDist = hypot(simulation.nurseX - simulation.playerX, simulation.nurseZ - simulation.playerZ)
    audio.updateAtmosphere(
      playing = true,
      nurseDist = nurseDist,
      alertState = simulation.nurseAlertState,
      powerOn = simulation.powerRestored,
      hiding = simulation.isHiding
    )

    if (eventsToProcess.isNotEmpty()) {
      viewModelScope.launch {
        handleEvents(eventsToProcess)
      }
    }

    uiSyncAccum += dt
    if (uiSyncAccum >= 0.05f) { // 20 Hz UI HUD sync
      uiSyncAccum = 0f
      syncUiFromSim()
    }
  }

  private suspend fun handleEvents(events: List<SimEvent>) {
    val reduced = _uiState.value.settings.reducedScares
    for (ev in events) {
      when (ev) {
        is SimEvent.PlaySound -> audio.playSound(ev.type, ev.volume, ev.pitch)
        is SimEvent.TriggerHaptic -> haptics.trigger(ev.kind, reduced)
        is SimEvent.SpawnParticles -> glRenderer.spawnParticles(ev.x, ev.y, ev.z, ev.kind, ev.count)
        is SimEvent.ShowBanner -> showBanner(ev.text)
        is SimEvent.ShowSubtitle -> showSubtitle(ev.text, ev.urgent)
        is SimEvent.OpenNote -> {
          inputHandler.clearHeld()
          _uiState.update { it.copy(screen = ScreenState.NOTE_READER, activeNote = ev.note) }
          syncUiFromSim()
        }
        is SimEvent.CheckpointReached -> {
          val cp = synchronized(simLock) { simulation.buildCheckpoint() }
          latestCheckpoint = cp
          repo.saveCheckpoint(cp)
          _uiState.update { it.copy(hasCheckpoint = true) }
        }
        is SimEvent.PlayerCaught -> {
          inputHandler.clearHeld()
          syncUiFromSim()
          _uiState.update { it.copy(screen = ScreenState.CAUGHT, endingType = ev.ending) }
        }
        is SimEvent.PlayerEscaped -> {
          inputHandler.clearHeld()
          repo.clearCheckpoint()
          syncUiFromSim()
          _uiState.update { it.copy(screen = ScreenState.ENDING, endingType = ev.ending, hasCheckpoint = false) }
        }
      }
    }
  }

  private fun showBanner(text: String) {
    bannerJob?.cancel()
    _uiState.update { it.copy(statusBanner = text) }
    bannerJob = viewModelScope.launch {
      delay(3200)
      _uiState.update { it.copy(statusBanner = null) }
    }
  }

  private fun showSubtitle(text: String, urgent: Boolean) {
    if (!_uiState.value.settings.subtitlesEnabled) return
    subtitleJob?.cancel()
    val id = ++subtitleSeq
    _uiState.update { it.copy(subtitleCue = SubtitleCue(id, text, urgent)) }
    subtitleJob = viewModelScope.launch {
      delay(2600)
      _uiState.update { state ->
        if (state.subtitleCue?.id == id) state.copy(subtitleCue = null) else state
      }
    }
  }

  private fun syncUiFromSim() {
    val snapshot = synchronized(simLock) {
      val collectedFuses = simulation.fuses.count { it.collected }
      val installedFuses = simulation.fuses.count { it.installed }
      val nurseDist = hypot(simulation.nurseX - simulation.playerX, simulation.nurseZ - simulation.playerZ)
      val zone = HospitalMap.zoneNameFor(simulation.playerX, simulation.playerZ)
      val objective = when {
        !simulation.tutorialGateOpened -> "Complete Vestibule checks & open North Ward Door"
        simulation.powerRestored -> "POWER ONLINE — Sprint to South Exit Lobby & Escape!"
        collectedFuses > installedFuses -> "Return to Central Breaker Room & install fuses ($installedFuses/4)"
        else -> "Search Wing C for Fuses ($installedFuses/4 installed, $collectedFuses/4 found)"
      }
      val readNotes = simulation.notes.filter { it.read }
      Triple(
        _uiState.value.copy(
          zoneName = zone,
          objectiveText = objective,
          fusesCollectedCount = collectedFuses,
          fusesInstalledCount = installedFuses,
          bottlesCount = simulation.bottlesCount,
          notesFoundCount = readNotes.size,
          totalNotesCount = simulation.notes.size,
          secretLedgerFound = simulation.notes.any { it.isSecretLedger && it.read },
          torchMode = simulation.torchMode,
          torchBattery = simulation.torchBattery,
          stamina = simulation.stamina,
          isCrouching = simulation.isCrouching,
          isSprinting = simulation.isSprinting,
          isHiding = simulation.isHiding,
          isHoldingBreath = simulation.isHoldingBreath,
          breathMeter = simulation.breathMeter,
          breathDangerActive = simulation.isHiding && nurseDist < 11.5f,
          isAimingBottle = simulation.isAimingBottle,
          nurseDistance = nurseDist,
          nurseAlertState = simulation.nurseAlertState,
          powerRestored = simulation.powerRestored,
          elapsedSeconds = simulation.elapsedSeconds,
          timesSpotted = simulation.timesSpotted,
          activePrompt = simulation.activePrompt,
          collectedNotes = readNotes,
          tutorialStep = simulation.tutorialStep
        ),
        Unit,
        Unit
      ).first
    }
    _uiState.value = snapshot
  }
}
