package com.example.game.engine

import com.example.game.model.*
import kotlin.math.*
import kotlin.random.Random

sealed interface SimEvent {
  data class PlaySound(val type: SoundType, val volume: Float = 1f, val pitch: Float = 1f) : SimEvent
  data class ShowSubtitle(val text: String, val urgent: Boolean = false) : SimEvent
  data class ShowBanner(val text: String) : SimEvent
  data class TriggerHaptic(val kind: HapticKind) : SimEvent
  data class SpawnParticles(val x: Float, val y: Float, val z: Float, val kind: ParticleKind, val count: Int) : SimEvent
  data class OpenNote(val note: LoreNote) : SimEvent
  data object CheckpointReached : SimEvent
  data class PlayerCaught(val ending: EndingType = EndingType.PERMANENT_DARK) : SimEvent
  data class PlayerEscaped(val ending: EndingType) : SimEvent
}

enum class SoundType {
  FOOTSTEP_SOFT,
  FOOTSTEP_SPRINT,
  NURSE_FOOTSTEP,
  NURSE_ALERT_STING,
  TORCH_CLICK,
  TORCH_DYNAMO,
  BOTTLE_THROW,
  BOTTLE_SHATTER,
  DOOR_CREAK,
  DOOR_SLAM,
  HAZARD_CLATTER,
  LOCKER_OPEN,
  LOCKER_CLOSE,
  BREATH_INHALE,
  BREATH_EXHALE,
  BREATH_GASP,
  PICKUP_FUSE,
  PICKUP_BOTTLE,
  NOTE_RUSTLE,
  BREAKER_INSTALL,
  GENERATOR_POWER_ON,
  CAUGHT_IMPACT
}

enum class HapticKind {
  LIGHT_TAP,
  HEARTBEAT,
  DOOR_SLAM,
  BOTTLE_SHATTER,
  CAUGHT
}

enum class ParticleKind {
  GLASS_SHARD,
  SPARK,
  DUST_BURST
}

data class ActiveBottle(
  var x: Float,
  var y: Float,
  var z: Float,
  var vx: Float,
  var vy: Float,
  var vz: Float,
  var spin: Float = 0f,
  var active: Boolean = true
)

class GameSimulation {
  // Player state
  var playerX = HospitalMap.PLAYER_SPAWN_X
  var playerZ = HospitalMap.PLAYER_SPAWN_Z
  var playerYaw = 0f // Facing North (-Z)
  var playerPitch = 0f
  var eyeHeight = 1.62f
  var headBobPhase = 0f
  var headBobAmount = 0f
  var screenShake = 0f

  var torchMode = TorchMode.LOW
  var torchBattery = 100f
  var torchFlicker = 1f

  var stamina = 100f
  var isCrouching = false
  var isSprinting = false

  var bottlesCount = 0
  var isAimingBottle = false
  var isHiding = false
  var currentLockerId = -1
  var lockerEntryYaw = 0f

  var isHoldingBreath = false
  var breathMeter = 100f
  private var breathHoldPenaltyCooldown = 0f
  private var exhalePromptCooldown = 0f

  // Nurse state
  var nurseX = HospitalMap.NURSE_SPAWN_X
  var nurseZ = HospitalMap.NURSE_SPAWN_Z
  var nurseYaw = PI.toFloat()
  var nurseWalkPhase = 0f
  var nurseSpeed = 0f
  var nurseHeadTilt = 0.12f
  var nurseLunge = 0f
  var nurseAlertState = NurseAlertState.PATROL
  private var nurseSuspicion = 0f
  private var nurseTargetX = HospitalMap.NURSE_SPAWN_X
  private var nurseTargetZ = HospitalMap.NURSE_SPAWN_Z
  private var nursePath: List<GridCell> = emptyList()
  private var nursePathTimer = 0f
  private var nurseWaitTimer = 0f
  private var nurseStepTimer = 0f
  private var nurseSearchLockerId = -1
  private var nurseSearchTimer = 0f
  private var nurseScanTime = 0f
  private var nurseEscortGraceTimer = 0f
  private var lastKnownPlayerX = 0f
  private var lastKnownPlayerZ = 0f
  private var hasLastKnownPlayer = false
  private var playerHeardInLocker = false

  // World entities
  val doors = HospitalMap.createInitialDoors().toMutableList()
  val fuses = HospitalMap.createInitialFuses().toMutableList()
  val bottles = HospitalMap.createInitialBottles().toMutableList()
  val lockers = HospitalMap.createInitialLockers().toMutableList()
  val hazards = HospitalMap.createInitialHazards().toMutableList()
  val notes = HospitalMap.createInitialNotes().toMutableList()
  val props = HospitalMap.createPropBoxes()
  val ceilingLights = HospitalMap.createCeilingLights()
  val activeBottles = mutableListOf<ActiveBottle>()

  // Progress & Campaign state
  var tutorialStep = TutorialStep.MOVE_LOOK
  var tutorialGateOpened = false
  var powerRestored = false
  var elapsedSeconds = 0f
  var timesSpotted = 0
  var activePrompt: PromptInfo? = null
  private var playerStepTimer = 0f
  private var heartbeatTimer = 0f
  private var totalMoveDistanceForTutorial = 0f
  private var tutorialToggledTorch = false
  private var tutorialCrouched = false
  private var tutorialThrewBottle = false
  private var tutorialHidInLocker = false

  // Reusable event queue drained each frame by ViewModel
  val pendingEvents = mutableListOf<SimEvent>()
  private var rng = Random(1997)
  private var finalPingTimer = 0f
  private var redVignette = 0f

  fun getRedVignette(): Float = redVignette

  fun resetNewGame(skipTutorial: Boolean = false) {
    playerX = HospitalMap.PLAYER_SPAWN_X
    playerZ = HospitalMap.PLAYER_SPAWN_Z
    playerYaw = 0f
    playerPitch = 0f
    eyeHeight = 1.62f
    torchMode = TorchMode.LOW
    torchBattery = 100f
    torchFlicker = 1f
    stamina = 100f
    isCrouching = false
    isSprinting = false
    bottlesCount = if (skipTutorial) 1 else 0
    isAimingBottle = false
    isHiding = false
    currentLockerId = -1
    isHoldingBreath = false
    breathMeter = 100f
    screenShake = 0f
    redVignette = 0f

    doors.clear(); doors.addAll(HospitalMap.createInitialDoors())
    fuses.clear(); fuses.addAll(HospitalMap.createInitialFuses())
    bottles.clear(); bottles.addAll(HospitalMap.createInitialBottles())
    lockers.clear(); lockers.addAll(HospitalMap.createInitialLockers())
    hazards.clear(); hazards.addAll(HospitalMap.createInitialHazards())
    notes.clear(); notes.addAll(HospitalMap.createInitialNotes())
    activeBottles.clear()

    if (skipTutorial) {
      tutorialStep = TutorialStep.COMPLETE
      tutorialGateOpened = true
      doors.find { it.isTutorialGate }?.apply { openAmount = 1f; targetOpen = 1f }
      val (sx, sz) = HospitalMap.cellCenter(3, 15)
      playerX = sx
      playerZ = sz
    } else {
      tutorialStep = TutorialStep.MOVE_LOOK
      tutorialGateOpened = false
    }

    powerRestored = false
    elapsedSeconds = 0f
    timesSpotted = 0
    totalMoveDistanceForTutorial = 0f
    tutorialToggledTorch = false
    tutorialCrouched = false
    tutorialThrewBottle = false
    tutorialHidInLocker = false

    resetNurseToSafePatrol()
    pendingEvents.clear()
  }

  fun restoreFromCheckpoint(cp: CheckpointData) {
    resetNewGame(skipTutorial = true)
    playerX = cp.playerX
    playerZ = cp.playerZ
    playerYaw = cp.playerYaw
    elapsedSeconds = cp.elapsedSeconds
    bottlesCount = cp.bottlesCount.coerceAtLeast(1)
    torchBattery = cp.torchBattery.coerceIn(35f, 100f)
    fuses.forEach { f ->
      f.collected = (cp.fusesCollectedMask and f.bitMask) != 0
      f.installed = (cp.fusesInstalledMask and f.bitMask) != 0
      if (f.installed) f.collected = true
    }
    notes.forEach { n ->
      n.read = (cp.notesReadMask and n.bitMask) != 0
    }
    powerRestored = fuses.all { it.installed }
    resetNurseToSafePatrol()
    nurseEscortGraceTimer = 3.0f
  }

  fun buildCheckpoint(): CheckpointData {
    var colMask = 0
    var instMask = 0
    fuses.forEach {
      if (it.collected) colMask = colMask or it.bitMask
      if (it.installed) instMask = instMask or it.bitMask
    }
    var notesMask = 0
    notes.forEach { if (it.read) notesMask = notesMask or it.bitMask }
    return CheckpointData(
      hasCheckpoint = true,
      fusesCollectedMask = colMask,
      fusesInstalledMask = instMask,
      notesReadMask = notesMask,
      secretLedgerFound = notes.any { it.isSecretLedger && it.read },
      playerX = playerX,
      playerZ = playerZ,
      playerYaw = playerYaw,
      elapsedSeconds = elapsedSeconds,
      bottlesCount = bottlesCount,
      torchBattery = torchBattery
    )
  }

  private fun resetNurseToSafePatrol() {
    // Pick a patrol point at least 18m away from the player
    val farPoints = HospitalMap.PATROL_WAYPOINTS.filter { (wx, wz) ->
      hypot(wx - playerX, wz - playerZ) > 18f
    }
    val chosen = farPoints.randomOrNull(rng) ?: Pair(HospitalMap.NURSE_SPAWN_X, HospitalMap.NURSE_SPAWN_Z)
    nurseX = chosen.first
    nurseZ = chosen.second
    nurseAlertState = if (powerRestored) NurseAlertState.FINAL_CHASE else NurseAlertState.PATROL
    nurseSuspicion = 0f
    nursePath = emptyList()
    nurseWaitTimer = 0.8f
    nurseSearchLockerId = -1
    nurseLunge = 0f
    hasLastKnownPlayer = false
  }

  fun skipTutorial() {
    if (tutorialStep == TutorialStep.COMPLETE) return
    tutorialStep = TutorialStep.COMPLETE
    tutorialGateOpened = true
    doors.find { it.isTutorialGate }?.let {
      it.targetOpen = 1f
      it.openAmount = 1f
    }
    if (bottlesCount == 0) bottlesCount = 1
    pendingEvents.add(SimEvent.ShowBanner("Tutorial skipped — Locate 4 Fuses in Wing C"))
  }

  fun cycleTorch() {
    torchMode = when (torchMode) {
      TorchMode.OFF -> if (torchBattery > 4f) TorchMode.LOW else TorchMode.OFF
      TorchMode.LOW -> if (torchBattery > 4f) TorchMode.HIGH else TorchMode.OFF
      TorchMode.HIGH -> TorchMode.OFF
    }
    tutorialToggledTorch = true
    pendingEvents.add(SimEvent.PlaySound(SoundType.TORCH_CLICK, 0.8f))
    pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.LIGHT_TAP))
  }

  fun crankTorchDynamo() {
    torchBattery = (torchBattery + 24f).coerceAtMost(100f)
    if (torchMode == TorchMode.OFF && torchBattery > 8f) {
      torchMode = TorchMode.LOW
    }
    pendingEvents.add(SimEvent.PlaySound(SoundType.TORCH_DYNAMO, 0.85f))
    pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.LIGHT_TAP))
    pendingEvents.add(SimEvent.ShowSubtitle("*Dynamo crank clicks softly*", urgent = false))
    emitNoise(playerX, playerZ, radius = 8.5f, isLure = false)
  }

  fun toggleCrouch() {
    if (isHiding) return
    isCrouching = !isCrouching
    if (isCrouching) isSprinting = false
    tutorialCrouched = true
    pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.LIGHT_TAP))
  }

  fun throwBottle() {
    if (isHiding || bottlesCount <= 0) return
    bottlesCount--
    isAimingBottle = false
    tutorialThrewBottle = true

    val fx = sin(playerYaw) * cos(playerPitch)
    val fy = -sin(playerPitch)
    val fz = -cos(playerYaw) * cos(playerPitch)
    val speed = 11.8f
    activeBottles.add(
      ActiveBottle(
        x = playerX + fx * 0.45f,
        y = eyeHeight - 0.15f,
        z = playerZ + fz * 0.45f,
        vx = fx * speed,
        vy = fy * speed + 3.1f,
        vz = fz * speed
      )
    )
    pendingEvents.add(SimEvent.PlaySound(SoundType.BOTTLE_THROW, 0.9f))
    pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.LIGHT_TAP))
  }

  fun interact() {
    if (isHiding) {
      exitLocker()
      return
    }

    // 1. Check nearby fuse (within 2.1m)
    val nearFuse = fuses.firstOrNull { !it.collected && hypot(it.x - playerX, it.z - playerZ) < 2.1f }
    if (nearFuse != null) {
      nearFuse.collected = true
      pendingEvents.add(SimEvent.PlaySound(SoundType.PICKUP_FUSE, 1.0f))
      pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.LIGHT_TAP))
      pendingEvents.add(SimEvent.ShowBanner("Collected ${nearFuse.name} (${nearFuse.rating})"))
      pendingEvents.add(SimEvent.CheckpointReached)
      return
    }

    // 2. Check Central Breaker Panel (within 2.3m)
    if (hypot(HospitalMap.BREAKER_X - playerX, HospitalMap.BREAKER_Z - playerZ) < 2.35f) {
      val collectedNotInstalled = fuses.filter { it.collected && !it.installed }
      if (collectedNotInstalled.isNotEmpty()) {
        collectedNotInstalled.forEach { it.installed = true }
        val installedCount = fuses.count { it.installed }
        pendingEvents.add(SimEvent.PlaySound(SoundType.BREAKER_INSTALL, 1.0f))
        pendingEvents.add(
          SimEvent.SpawnParticles(
            HospitalMap.BREAKER_X,
            1.4f,
            HospitalMap.BREAKER_Z,
            ParticleKind.SPARK,
            count = 16
          )
        )
        pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.DOOR_SLAM))
        if (installedCount >= 4) {
          powerRestored = true
          nurseAlertState = NurseAlertState.FINAL_CHASE
          pendingEvents.add(SimEvent.PlaySound(SoundType.GENERATOR_POWER_ON, 1.0f))
          pendingEvents.add(SimEvent.ShowBanner("POWER RESTORED — SPRINT TO SOUTH EXIT LOBBY!"))
          pendingEvents.add(SimEvent.ShowSubtitle("*Auxiliary generator roars alive! Emergency shutter unlocked!*", urgent = true))
          screenShake = 0.45f
        } else {
          pendingEvents.add(SimEvent.ShowBanner("Breaker Loaded: $installedCount / 4 Fuses Seated"))
        }
        pendingEvents.add(SimEvent.CheckpointReached)
        return
      }
    }

    // 3. Check Emergency Exit Shutter
    if (powerRestored && hypot(HospitalMap.EXIT_X - playerX, HospitalMap.EXIT_Z - playerZ) < 2.5f) {
      triggerEscape()
      return
    }

    // 4. Check nearby Bottle
    val nearBottle = bottles.firstOrNull { !it.collected && hypot(it.x - playerX, it.z - playerZ) < 2.0f }
    if (nearBottle != null && bottlesCount < 3) {
      nearBottle.collected = true
      bottlesCount++
      pendingEvents.add(SimEvent.PlaySound(SoundType.PICKUP_BOTTLE, 0.85f))
      pendingEvents.add(SimEvent.ShowBanner("Picked up Empty Saline Bottle ($bottlesCount/3)"))
      return
    }

    // 5. Check nearby Note
    val nearNote = notes.firstOrNull { hypot(it.x - playerX, it.z - playerZ) < 2.0f }
    if (nearNote != null) {
      nearNote.read = true
      pendingEvents.add(SimEvent.PlaySound(SoundType.NOTE_RUSTLE, 0.9f))
      pendingEvents.add(SimEvent.OpenNote(nearNote))
      return
    }

    // 6. Check nearby Locker
    val nearLocker = lockers.firstOrNull { hypot(it.x - playerX, it.z - playerZ) < 1.95f }
    if (nearLocker != null) {
      enterLocker(nearLocker)
      return
    }

    // 7. Check nearby Door
    val nearDoor = findNearestDoor(2.3f)
    if (nearDoor != null) {
      toggleDoor(nearDoor, slam = isSprinting)
    }
  }

  private fun enterLocker(locker: LockerSpot) {
    isHiding = true
    isSprinting = false
    isAimingBottle = false
    currentLockerId = locker.id
    lockerEntryYaw = locker.facingYaw
    playerX = locker.x
    playerZ = locker.z
    playerYaw = locker.facingYaw
    playerPitch = 0f
    locker.doorOpenAngle = 1.25f
    tutorialHidInLocker = true
    playerHeardInLocker = false
    pendingEvents.add(SimEvent.PlaySound(SoundType.LOCKER_OPEN, 0.9f))
    pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.LIGHT_TAP))

    // If Nurse currently has line of sight while entering, she knows!
    if (canNurseSeePlayer(ignoreLocker = true)) {
      nurseAlertState = NurseAlertState.SEARCH_LOCKER
      nurseSearchLockerId = locker.id
      nurseSearchTimer = 1.6f
      nurseTargetX = locker.exitX
      nurseTargetZ = locker.exitZ
      playerHeardInLocker = true
    }
  }

  private fun exitLocker() {
    val locker = lockers.find { it.id == currentLockerId }
    isHiding = false
    isHoldingBreath = false
    currentLockerId = -1
    if (locker != null) {
      playerX = locker.exitX
      playerZ = locker.exitZ
      playerYaw = locker.facingYaw
      locker.doorOpenAngle = 1.15f
    }
    updateTutorial(0f, 0f)
    pendingEvents.add(SimEvent.PlaySound(SoundType.LOCKER_CLOSE, 0.85f))
  }

  private fun findNearestDoor(maxDist: Float): DoorObj? {
    var best: DoorObj? = null
    var bestD = maxDist
    for (d in doors) {
      val (dx, dz) = HospitalMap.cellCenter(d.col, d.row)
      val dist = hypot(dx - playerX, dz - playerZ)
      if (dist < bestD) {
        bestD = dist
        best = d
      }
    }
    return best
  }

  private fun toggleDoor(door: DoorObj, slam: Boolean) {
    val opening = door.targetOpen < 0.5f
    door.targetOpen = if (opening) 1f else 0f
    if (door.isTutorialGate && opening && !tutorialGateOpened) {
      tutorialGateOpened = true
      tutorialStep = TutorialStep.COMPLETE
      pendingEvents.add(SimEvent.ShowBanner("Entered Wing C — Watch your light and noise"))
      pendingEvents.add(SimEvent.CheckpointReached)
    }
    val (dx, dz) = HospitalMap.cellCenter(door.col, door.row)
    if (slam) {
      pendingEvents.add(SimEvent.PlaySound(SoundType.DOOR_SLAM, 1.0f))
      pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.DOOR_SLAM))
      pendingEvents.add(SimEvent.ShowSubtitle("*Metal ward door slams shut!*", urgent = true))
      emitNoise(dx, dz, radius = 16f, isLure = false)
      screenShake = max(screenShake, 0.22f)
    } else {
      pendingEvents.add(SimEvent.PlaySound(SoundType.DOOR_CREAK, 0.7f))
      emitNoise(dx, dz, radius = 4.5f, isLure = false)
    }
  }

  fun step(dtRaw: Float, input: InputState, settings: SettingsState) {
    val dt = dtRaw.coerceIn(0.001f, 0.05f)
    elapsedSeconds += dt
    if (nurseEscortGraceTimer > 0f) nurseEscortGraceTimer -= dt
    if (breathHoldPenaltyCooldown > 0f) breathHoldPenaltyCooldown -= dt
    if (exhalePromptCooldown > 0f) exhalePromptCooldown -= dt
    screenShake = (screenShake - dt * 1.8f).coerceAtLeast(0f)

    // 1. Look input
    val sens = 0.0034f * settings.lookSensitivity
    val stickSpeed = 2.4f * settings.lookSensitivity
    val rawYawDelta = input.lookDeltaX * sens + input.stickLookX * stickSpeed * dt
    val rawPitchDelta = input.lookDeltaY * sens + input.stickLookY * stickSpeed * dt

    playerYaw += rawYawDelta
    playerPitch = (playerPitch + rawPitchDelta).coerceIn(-0.95f, 0.95f)

    if (isHiding) {
      // Constrain peek angle inside locker
      val diff = normalizeAngle(playerYaw - lockerEntryYaw)
      val clamped = diff.coerceIn(-0.48f, 0.48f)
      playerYaw = lockerEntryYaw + clamped
      playerPitch = playerPitch.coerceIn(-0.38f, 0.38f)
    }

    // 2. Torch battery & flicker
    if (torchMode != TorchMode.OFF) {
      torchBattery = (torchBattery - torchMode.drainPerSec * dt).coerceIn(0f, 100f)
      if (torchBattery <= 0f) {
        torchMode = TorchMode.OFF
        pendingEvents.add(SimEvent.PlaySound(SoundType.TORCH_CLICK, 0.9f))
        pendingEvents.add(SimEvent.ShowSubtitle("*Torch battery depleted! Tap Recharge or keep OFF*", urgent = true))
      }
    } else {
      torchBattery = (torchBattery + 4.2f * dt).coerceAtMost(100f)
    }

    val nurseDist = hypot(nurseX - playerX, nurseZ - playerZ)
    torchFlicker = if (torchMode == TorchMode.OFF) {
      0f
    } else if (nurseDist < 13f) {
      val threat = (1f - nurseDist / 13f).coerceIn(0f, 1f)
      val wave = sin(elapsedSeconds * (18f + threat * 26f)) * cos(elapsedSeconds * 31f)
      (1f - threat * 0.55f * (0.5f + 0.5f * wave)).coerceIn(0.22f, 1f)
    } else {
      1f
    }

    // 3. Player movement (if not hiding)
    isAimingBottle = !isHiding && bottlesCount > 0 && input.aimBottleHeld
    if (!isHiding) {
      updatePlayerMovement(dt, input)
    } else {
      isSprinting = false
      headBobAmount = (headBobAmount - dt * 4f).coerceAtLeast(0f)
      updateLockerBreath(dt, input.breathHeld, nurseDist)
    }

    // Smooth eye height for crouch
    val targetEye = when {
      isHiding -> 1.48f
      isCrouching -> 0.96f
      else -> 1.62f
    }
    eyeHeight += (targetEye - eyeHeight) * (dt * 10f).coerceAtMost(1f)

    // 4. Animate doors & lockers
    for (door in doors) {
      door.openAmount += (door.targetOpen - door.openAmount) * (dt * 6.5f).coerceAtMost(1f)
    }
    for (locker in lockers) {
      val target = if (isHiding && locker.id == currentLockerId) 0.04f
      else if (nurseAlertState == NurseAlertState.SEARCH_LOCKER && nurseSearchLockerId == locker.id && nurseSearchTimer < 0.7f) 1.25f
      else 0f
      locker.doorOpenAngle += (target - locker.doorOpenAngle) * (dt * 7f).coerceAtMost(1f)
    }

    // 5. Update hazards cooldown & check stepping on hazard
    for (h in hazards) {
      if (h.cooldown > 0f) h.cooldown -= dt
      if (!isHiding && !isCrouching && h.cooldown <= 0f) {
        if (hypot(h.x - playerX, h.z - playerZ) < 0.85f) {
          h.cooldown = 5.0f
          pendingEvents.add(SimEvent.PlaySound(SoundType.HAZARD_CLATTER, 1.0f))
          pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.DOOR_SLAM))
          pendingEvents.add(SimEvent.ShowSubtitle("*You kicked ${h.name}! Metal clatters loudly!*", urgent = true))
          emitNoise(h.x, h.z, radius = 16.5f, isLure = false)
          screenShake = max(screenShake, 0.25f)
        }
      }
    }

    // 6. Active bottle projectiles
    updateBottles(dt)

    // 7. Update Nurse AI
    updateNurseAI(dt, nurseDist)

    // 8. Heartbeat & red vignette telegraph
    val targetVignette = when {
      nurseAlertState == NurseAlertState.HUNT || nurseAlertState == NurseAlertState.FINAL_CHASE ->
        (0.25f + (1f - (nurseDist / 14f).coerceIn(0f, 1f)) * 0.65f)
      nurseDist < 12f -> (1f - nurseDist / 12f) * 0.45f
      isHiding && isHoldingBreath && breathMeter < 22f -> (1f - breathMeter / 22f) * 0.55f
      else -> 0f
    }
    redVignette += (targetVignette - redVignette) * (dt * 5f).coerceAtMost(1f)

    if (nurseDist < 15f || (isHiding && isHoldingBreath && breathMeter < 45f)) {
      val urgency = max(
        (1f - (nurseDist / 15f).coerceIn(0f, 1f)),
        if (isHoldingBreath) (1f - breathMeter / 60f).coerceIn(0f, 1f) else 0f
      )
      val beatInterval = (0.95f - urgency * 0.56f).coerceIn(0.36f, 0.95f)
      heartbeatTimer -= dt
      if (heartbeatTimer <= 0f) {
        heartbeatTimer = beatInterval
        if (urgency > 0.35f) {
          pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.HEARTBEAT))
        }
      }
    }

    // 9. Check Escape proximity automatically once powered
    if (powerRestored && !isHiding && hypot(HospitalMap.EXIT_X - playerX, HospitalMap.EXIT_Z - playerZ) < 1.8f) {
      triggerEscape()
      return
    }

    // 10. Update Tutorial & Interaction Prompt
    updateTutorial(rawYawDelta, rawPitchDelta)
    updateInteractionPrompt()
  }

  private fun updatePlayerMovement(dt: Float, input: InputState) {
    val mag = hypot(input.moveX, input.moveY).coerceAtMost(1f)
    val wantsSprint = input.sprintHeld && !isCrouching && mag > 0.25f && stamina > 8f
    isSprinting = wantsSprint

    if (isSprinting) {
      stamina = (stamina - 22f * dt).coerceAtLeast(0f)
    } else {
      stamina = (stamina + 16f * dt).coerceAtMost(100f)
    }

    val moveSpeed = when {
      isCrouching -> 2.1f
      isSprinting -> 5.35f
      else -> 3.45f
    }

    if (mag > 0.05f) {
      val fx = sin(playerYaw)
      val fz = -cos(playerYaw)
      val rx = cos(playerYaw)
      val rz = sin(playerYaw)

      val vx = (fx * input.moveY + rx * input.moveX) * moveSpeed
      val vz = (fz * input.moveY + rz * input.moveX) * moveSpeed

      val prevX = playerX
      val prevZ = playerZ
      moveWithCollision(vx * dt, vz * dt, radius = 0.35f)
      val moved = hypot(playerX - prevX, playerZ - prevZ)
      totalMoveDistanceForTutorial += moved

      // Auto push closed doors on contact
      val contactDoor = findNearestDoor(1.05f)
      if (contactDoor != null && contactDoor.targetOpen < 0.5f) {
        toggleDoor(contactDoor, slam = isSprinting)
      }

      // Footsteps & noise
      headBobPhase += moved * (if (isSprinting) 2.4f else 2.8f)
      headBobAmount = (headBobAmount + dt * 6f).coerceAtMost(1f)
      playerStepTimer -= dt
      if (playerStepTimer <= 0f && moved > 0.005f) {
        playerStepTimer = when {
          isSprinting -> 0.31f
          isCrouching -> 0.58f
          else -> 0.44f
        }
        if (isSprinting) {
          pendingEvents.add(SimEvent.PlaySound(SoundType.FOOTSTEP_SPRINT, 0.85f))
          emitNoise(playerX, playerZ, radius = 11.5f, isLure = false)
        } else if (!isCrouching) {
          pendingEvents.add(SimEvent.PlaySound(SoundType.FOOTSTEP_SOFT, 0.55f))
          emitNoise(playerX, playerZ, radius = 4.2f, isLure = false)
        } else {
          pendingEvents.add(SimEvent.PlaySound(SoundType.FOOTSTEP_SOFT, 0.24f))
        }
      }
    } else {
      headBobAmount = (headBobAmount - dt * 5f).coerceAtLeast(0f)
    }
  }

  private fun updateLockerBreath(dt: Float, breathHeld: Boolean, nurseDist: Float) {
    val wasHolding = isHoldingBreath
    val canHold = breathHoldPenaltyCooldown <= 0f
    isHoldingBreath = breathHeld && canHold

    if (!wasHolding && isHoldingBreath) {
      pendingEvents.add(SimEvent.PlaySound(SoundType.BREATH_INHALE, 0.75f))
    } else if (wasHolding && !isHoldingBreath) {
      // Released breath! Check sweet spot window (18..40%)
      if (breathMeter in 15f..42f || nurseDist > 7f) {
        pendingEvents.add(SimEvent.PlaySound(SoundType.BREATH_EXHALE, 0.65f))
      } else if (nurseDist < 6.5f) {
        // Released too early in panic while Nurse is right outside
        pendingEvents.add(SimEvent.PlaySound(SoundType.BREATH_EXHALE, 0.9f))
        if (breathMeter > 65f) {
          emitNoise(playerX, playerZ, radius = 6.0f, isLure = false)
        }
      }
    }

    if (isHoldingBreath) {
      breathMeter = (breathMeter - 18.5f * dt).coerceAtLeast(0f)
      if (breathMeter in 18f..40f && exhalePromptCooldown <= 0f) {
        exhalePromptCooldown = 1.6f
        pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.LIGHT_TAP))
      }
      if (breathMeter <= 0f) {
        // Out of breath — loud gasp!
        isHoldingBreath = false
        breathHoldPenaltyCooldown = 1.5f
        pendingEvents.add(SimEvent.PlaySound(SoundType.BREATH_GASP, 1.0f))
        pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.DOOR_SLAM))
        pendingEvents.add(SimEvent.ShowSubtitle("*You gasp for air inside the locker!*", urgent = true))
        emitNoise(playerX, playerZ, radius = 10.5f, isLure = false)
        playerHeardInLocker = true
      }
    } else {
      breathMeter = (breathMeter + 36f * dt).coerceAtMost(100f)
      // If Nurse is very close (<5m) and player does NOT hold breath, nervous breathing alerts her
      if (nurseDist < 5.0f && breathHoldPenaltyCooldown <= 0f && exhalePromptCooldown <= 0f) {
        exhalePromptCooldown = 2.0f
        pendingEvents.add(SimEvent.ShowSubtitle("*Hold Breath! Matron Lim is listening outside!*", urgent = true))
        emitNoise(playerX, playerZ, radius = 5.5f, isLure = false)
      }
    }
  }

  private fun updateBottles(dt: Float) {
    val iter = activeBottles.iterator()
    while (iter.hasNext()) {
      val b = iter.next()
      b.vy -= 11.5f * dt
      b.spin += 9f * dt
      val nx = b.x + b.vx * dt
      val ny = b.y + b.vy * dt
      val nz = b.z + b.vz * dt

      val hitWall = isSolidAt(nx, nz, margin = 0.08f)
      if (hitWall || ny <= 0.08f) {
        val sx = if (hitWall) b.x else nx
        val sz = if (hitWall) b.z else nz
        pendingEvents.add(SimEvent.PlaySound(SoundType.BOTTLE_SHATTER, 1.0f))
        pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.BOTTLE_SHATTER))
        pendingEvents.add(SimEvent.SpawnParticles(sx, 0.25f, sz, ParticleKind.GLASS_SHARD, 18))
        pendingEvents.add(SimEvent.ShowSubtitle("*Glass shatters loudly — Matron Lim turns toward the sound*", urgent = false))
        emitNoise(sx, sz, radius = 25f, isLure = true)
        iter.remove()
      } else {
        b.x = nx
        b.y = ny
        b.z = nz
      }
    }
  }

  private fun emitNoise(nx: Float, nz: Float, radius: Float, isLure: Boolean) {
    // Before Tutorial Door opened, keep Nurse calm unless bottle is thrown right inside Wing C
    if (!tutorialGateOpened && isInVestibule(nx, nz)) return

    val installedCount = fuses.count { it.installed }
    val effectiveRadius = radius * (1f + installedCount * 0.08f)
    val distToNurse = hypot(nurseX - nx, nurseZ - nz)
    if (distToNurse <= effectiveRadius) {
      if (isLure || nurseAlertState != NurseAlertState.HUNT) {
        nurseTargetX = nx
        nurseTargetZ = nz
        nursePathTimer = 0f // Recompute immediately
        if (isLure) {
          nurseAlertState = NurseAlertState.INVESTIGATE
          nurseSuspicion = 0f
          nurseWaitTimer = 2.6f
        } else if (nurseAlertState == NurseAlertState.PATROL || nurseAlertState == NurseAlertState.SUSPICIOUS) {
          nurseAlertState = if (isHiding && distToNurse < 6.5f) {
            nurseSearchLockerId = currentLockerId
            nurseSearchTimer = 2.2f
            NurseAlertState.SEARCH_LOCKER
          } else {
            NurseAlertState.INVESTIGATE
          }
          nurseWaitTimer = 2.0f
        }
      }
    }
  }

  private fun updateNurseAI(dt: Float, nurseDist: Float) {
    if (!tutorialGateOpened) {
      // Gently patrol distant North corridor during safe tutorial
      nurseAlertState = NurseAlertState.PATROL
      nurseSpeed = 1.6f
      stepNurseAlongPath(dt, nurseSpeed)
      if (nursePath.isEmpty()) {
        val (wx, wz) = HospitalMap.cellCenter(18, 7)
        val (nx, nz) = HospitalMap.cellCenter(11, 10)
        val pick = if (hypot(nurseX - wx, nurseZ - wz) < 2f) Pair(nx, nz) else Pair(wx, wz)
        nurseTargetX = pick.first
        nurseTargetZ = pick.second
        computeNursePath()
      }
      return
    }

    val installedCount = fuses.count { it.installed }
    val patrolSpeed = 1.95f + installedCount * 0.22f
    val huntSpeed = 3.15f + installedCount * 0.24f

    // Periodic pressure pulse in Final Escape
    if (powerRestored) {
      finalPingTimer -= dt
      if (finalPingTimer <= 0f) {
        finalPingTimer = 5.5f
        if (!isHiding) {
          nurseTargetX = playerX
          nurseTargetZ = playerZ
          nursePathTimer = 0f
          nurseAlertState = NurseAlertState.FINAL_CHASE
        }
      }
    }

    // Sight check
    val canSee = !isHiding && canNurseSeePlayer(ignoreLocker = false)
    if (canSee) {
      val sightRate = when {
        nurseDist < 5.0f -> 2.8f
        torchMode == TorchMode.HIGH -> 1.85f
        torchMode == TorchMode.LOW -> 1.15f
        else -> 0.72f
      } * (if (isCrouching) 0.72f else 1.0f)

      val prevSusp = nurseSuspicion
      nurseSuspicion = (nurseSuspicion + sightRate * dt).coerceAtMost(1f)
      if (prevSusp < 0.45f && nurseSuspicion >= 0.45f) {
        pendingEvents.add(SimEvent.PlaySound(SoundType.NURSE_ALERT_STING, 0.75f))
        pendingEvents.add(SimEvent.ShowSubtitle("*A sharp gasp echoes — Matron Lim spotted your light!*", urgent = true))
      }
      if (nurseSuspicion >= 1f) {
        nurseAlertState = if (powerRestored) NurseAlertState.FINAL_CHASE else NurseAlertState.HUNT
        lastKnownPlayerX = playerX
        lastKnownPlayerZ = playerZ
        hasLastKnownPlayer = true
        nurseTargetX = playerX
        nurseTargetZ = playerZ
      } else if (nurseAlertState == NurseAlertState.PATROL) {
        nurseAlertState = NurseAlertState.SUSPICIOUS
        nurseTargetX = playerX
        nurseTargetZ = playerZ
      }
    } else {
      nurseSuspicion = (nurseSuspicion - 0.35f * dt).coerceAtLeast(0f)
    }

    // State machine
    when (nurseAlertState) {
      NurseAlertState.PATROL -> {
        nurseSpeed = patrolSpeed
        nurseLunge = (nurseLunge - dt * 3f).coerceAtLeast(0f)
        if (nurseWaitTimer > 0f) {
          nurseWaitTimer -= dt
          nurseSpeed = 0f
          nurseScanTime += dt
          nurseYaw += sin(nurseScanTime * 1.4f) * 0.45f * dt
        } else {
          stepNurseAlongPath(dt, nurseSpeed)
          if (nursePath.isEmpty() || hypot(nurseTargetX - nurseX, nurseTargetZ - nurseZ) < 0.8f) {
            pickNewPatrolTarget()
            nurseWaitTimer = 1.0f + rng.nextFloat() * 1.2f
          }
        }
      }

      NurseAlertState.SUSPICIOUS, NurseAlertState.INVESTIGATE -> {
        nurseSpeed = patrolSpeed * 1.18f
        nurseLunge = (nurseLunge - dt * 3f).coerceAtLeast(0f)
        stepNurseAlongPath(dt, nurseSpeed)
        if (hypot(nurseTargetX - nurseX, nurseTargetZ - nurseZ) < 1.0f || nursePath.isEmpty()) {
          nurseSpeed = 0f
          nurseWaitTimer -= dt
          nurseScanTime += dt
          nurseYaw += sin(nurseScanTime * 2.6f) * 1.3f * dt
          if (nurseWaitTimer <= 0f) {
            nurseAlertState = if (powerRestored) NurseAlertState.FINAL_CHASE else NurseAlertState.PATROL
            pickNewPatrolTarget()
          }
        }
      }

      NurseAlertState.SEARCH_LOCKER -> {
        val locker = lockers.find { it.id == nurseSearchLockerId }
        if (locker == null) {
          nurseAlertState = NurseAlertState.PATROL
        } else {
          nurseTargetX = locker.exitX
          nurseTargetZ = locker.exitZ
          val distToExit = hypot(locker.exitX - nurseX, locker.exitZ - nurseZ)
          if (distToExit > 0.9f) {
            nurseSpeed = patrolSpeed * 1.22f
            stepNurseAlongPath(dt, nurseSpeed)
          } else {
            nurseSpeed = 0f
            // Face the locker
            val targetYaw = atan2(locker.x - nurseX, -(locker.z - nurseZ))
            nurseYaw = lerpAngle(nurseYaw, targetYaw, dt * 6f)
            nurseSearchTimer -= dt
            if (nurseSearchTimer <= 0f) {
              if (isHiding && currentLockerId == locker.id && (playerHeardInLocker || !isHoldingBreath)) {
                // Caught inside locker!
                pendingEvents.add(SimEvent.PlaySound(SoundType.CAUGHT_IMPACT, 1.0f))
                pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.CAUGHT))
                pendingEvents.add(SimEvent.PlayerCaught(EndingType.PERMANENT_DARK))
                return
              } else {
                // Held breath and stayed silent — she moves on!
                pendingEvents.add(SimEvent.ShowSubtitle("*Locker latches rattle... then footsteps recede*", urgent = false))
                nurseAlertState = NurseAlertState.PATROL
                nurseSearchLockerId = -1
                pickNewPatrolTarget()
              }
            }
          }
        }
      }

      NurseAlertState.HUNT, NurseAlertState.FINAL_CHASE -> {
        nurseSpeed = huntSpeed
        nurseLunge = (nurseLunge + dt * 2.5f).coerceAtMost(1f)
        if (canSee) {
          nurseTargetX = playerX
          nurseTargetZ = playerZ
          hasLastKnownPlayer = true
        }
        stepNurseAlongPath(dt, nurseSpeed)

        // Capture check
        if (!isHiding && nurseEscortGraceTimer <= 0f && nurseDist < 1.18f) {
          timesSpotted++
          pendingEvents.add(SimEvent.PlaySound(SoundType.CAUGHT_IMPACT, 1.0f))
          pendingEvents.add(SimEvent.TriggerHaptic(HapticKind.CAUGHT))
          pendingEvents.add(SimEvent.PlayerCaught(EndingType.PERMANENT_DARK))
          return
        }

        if (!canSee && hypot(nurseTargetX - nurseX, nurseTargetZ - nurseZ) < 0.95f) {
          if (powerRestored && !isHiding) {
            nurseTargetX = playerX
            nurseTargetZ = playerZ
          } else {
            nurseAlertState = NurseAlertState.INVESTIGATE
            nurseWaitTimer = 2.4f
          }
        }
      }
    }

    // Nurse walk cycle & eerie footsteps
    if (nurseSpeed > 0.1f) {
      nurseWalkPhase += nurseSpeed * dt * 2.6f
      nurseHeadTilt = 0.14f + sin(nurseWalkPhase * 0.5f) * 0.08f
      nurseStepTimer -= dt
      if (nurseStepTimer <= 0f) {
        nurseStepTimer = (1.15f / nurseSpeed).coerceIn(0.28f, 0.65f)
        if (nurseDist < 22f) {
          val vol = (1f / (1f + nurseDist * nurseDist * 0.032f)).coerceIn(0.05f, 1f)
          pendingEvents.add(SimEvent.PlaySound(SoundType.NURSE_FOOTSTEP, vol))
          if (nurseDist < 9.5f) {
            pendingEvents.add(SimEvent.ShowSubtitle("*Heavy footsteps echoing nearby*", urgent = nurseDist < 5.5f))
          }
        }
      }
    } else {
      nurseHeadTilt = 0.22f + sin(elapsedSeconds * 1.9f) * 0.11f
    }
  }

  private fun canNurseSeePlayer(ignoreLocker: Boolean): Boolean {
    if (isHiding && !ignoreLocker) return false
    if (!tutorialGateOpened && isInVestibule(playerX, playerZ)) return false

    val dx = playerX - nurseX
    val dz = playerZ - nurseZ
    val dist = hypot(dx, dz)

    val installedCount = fuses.count { it.installed }
    val baseRange = when (torchMode) {
      TorchMode.OFF -> 5.2f
      TorchMode.LOW -> 12.0f
      TorchMode.HIGH -> 21.0f
    } * (if (isCrouching) 0.72f else 1.0f) * (1f + installedCount * 0.10f)

    if (dist > baseRange) return false

    // Vision cone (wider if player has Torch HIGH or is close)
    val dirToPlayer = atan2(dx, -dz)
    val angleDiff = abs(normalizeAngle(dirToPlayer - nurseYaw))
    val maxCone = when {
      dist < 3.0f -> PI.toFloat() // Senses presence right next to her
      torchMode == TorchMode.HIGH -> 1.35f
      else -> 1.05f
    }
    if (angleDiff > maxCone) return false

    return Pathfinder.hasLineOfSight(
      nurseX / HospitalMap.CELL_SIZE,
      nurseZ / HospitalMap.CELL_SIZE,
      playerX / HospitalMap.CELL_SIZE,
      playerZ / HospitalMap.CELL_SIZE
    ) { col, row -> isOpaqueForSight(col, row) }
  }

  private fun stepNurseAlongPath(dt: Float, speed: Float) {
    nursePathTimer -= dt
    if (nursePathTimer <= 0f || nursePath.isEmpty()) {
      nursePathTimer = 0.45f
      computeNursePath()
    }

    val nextTarget = if (nursePath.isNotEmpty()) {
      HospitalMap.cellCenter(nursePath.first().col, nursePath.first().row)
    } else {
      Pair(nurseTargetX, nurseTargetZ)
    }

    val dx = nextTarget.first - nurseX
    val dz = nextTarget.second - nurseZ
    val dist = hypot(dx, dz)

    if (dist < 0.35f && nursePath.isNotEmpty()) {
      nursePath = nursePath.drop(1)
    }

    if (dist > 0.05f) {
      val stepDist = (speed * dt).coerceAtMost(dist)
      nurseX += (dx / dist) * stepDist
      nurseZ += (dz / dist) * stepDist
      val desiredYaw = atan2(dx, -dz)
      nurseYaw = lerpAngle(nurseYaw, desiredYaw, (dt * 7.5f).coerceAtMost(1f))

      // Nurse pushes open any door she walks through
      val nc = HospitalMap.worldToCol(nurseX)
      val nr = HospitalMap.worldToRow(nurseZ)
      doors.find { it.col == nc && it.row == nr && it.targetOpen < 0.5f }?.let {
        it.targetOpen = 1f
        pendingEvents.add(SimEvent.PlaySound(SoundType.DOOR_CREAK, 0.55f))
      }
    }
  }

  private fun computeNursePath() {
    val sc = HospitalMap.worldToCol(nurseX)
    val sr = HospitalMap.worldToRow(nurseZ)
    val gc = HospitalMap.worldToCol(nurseTargetX)
    val gr = HospitalMap.worldToRow(nurseTargetZ)
    nursePath = Pathfinder.findPath(
      HospitalMap.COLS,
      HospitalMap.ROWS,
      isWalkable = { c, r -> !HospitalMap.isWall(c, r) && HospitalMap.charAt(c, r) != 'E' },
      sc,
      sr,
      gc,
      gr
    )
  }

  private fun pickNewPatrolTarget() {
    val candidates = HospitalMap.PATROL_WAYPOINTS.filter { (wx, wz) ->
      hypot(wx - nurseX, wz - nurseZ) > 5f && (tutorialGateOpened || !isInVestibule(wx, wz))
    }
    val chosen = candidates.randomOrNull(rng) ?: HospitalMap.PATROL_WAYPOINTS.first()
    nurseTargetX = chosen.first
    nurseTargetZ = chosen.second
    computeNursePath()
  }

  private fun isOpaqueForSight(col: Int, row: Int): Boolean {
    val ch = HospitalMap.charAt(col, row)
    if (ch == '#' || ch == 'E') return true
    if (ch == 'D') {
      val door = doors.find { it.col == col && it.row == row }
      if (door != null && door.openAmount < 0.5f) return true
    }
    return false
  }

  private fun isSolidAt(x: Float, z: Float, margin: Float): Boolean {
    val checks = floatArrayOf(-margin, margin)
    for (ox in checks) {
      for (oz in checks) {
        val c = HospitalMap.worldToCol(x + ox)
        val r = HospitalMap.worldToRow(z + oz)
        val ch = HospitalMap.charAt(c, r)
        if (ch == '#' || ch == 'E') return true
        if (ch == 'D') {
          val d = doors.find { it.col == c && it.row == r }
          if (d != null && d.openAmount < 0.45f) return true
        }
      }
    }
    return false
  }

  private fun moveWithCollision(dx: Float, dz: Float, radius: Float) {
    // Axis-separated sliding against walls + prop AABBs
    val tryX = playerX + dx
    if (!collidesWorld(tryX, playerZ, radius)) {
      playerX = tryX
    }
    val tryZ = playerZ + dz
    if (!collidesWorld(playerX, tryZ, radius)) {
      playerZ = tryZ
    }
  }

  private fun collidesWorld(x: Float, z: Float, r: Float): Boolean {
    val minC = HospitalMap.worldToCol(x - r)
    val maxC = HospitalMap.worldToCol(x + r)
    val minR = HospitalMap.worldToRow(z - r)
    val maxR = HospitalMap.worldToRow(z + r)
    for (row in minR..maxR) {
      for (col in minC..maxC) {
        val ch = HospitalMap.charAt(col, row)
        if (ch == '#' || ch == 'E') return true
        if (ch == 'D') {
          val door = doors.find { it.col == col && it.row == row }
          if (door != null && door.isTutorialGate && tutorialStep.ordinal < TutorialStep.LOCKER_BREATH.ordinal) {
            return true
          }
          if (door != null && door.openAmount < 0.35f) return true
        }
      }
    }
    for (p in props) {
      if (x + r > p.x - p.halfW && x - r < p.x + p.halfW &&
        z + r > p.z - p.halfD && z - r < p.z + p.halfD
      ) {
        return true
      }
    }
    for (l in lockers) {
      if (x + r > l.x - 0.38f && x - r < l.x + 0.38f &&
        z + r > l.z - 0.38f && z - r < l.z + 0.38f
      ) {
        return true
      }
    }
    return false
  }

  private fun isInVestibule(x: Float, z: Float): Boolean {
    val c = HospitalMap.worldToCol(x)
    val r = HospitalMap.worldToRow(z)
    return c in 1..6 && r in 17..22
  }

  private fun triggerEscape() {
    val secretFound = notes.any { it.isSecretLedger && it.read }
    val allNotes = notes.count { it.read } >= 6
    val ending = if (secretFound || allNotes) EndingType.TRUTH_LEDGER else EndingType.SURVIVAL_STANDARD
    pendingEvents.add(SimEvent.PlayerEscaped(ending))
  }

  private fun updateTutorial(yawDelta: Float, pitchDelta: Float) {
    if (tutorialStep == TutorialStep.COMPLETE) return
    tutorialStep = when (tutorialStep) {
      TutorialStep.MOVE_LOOK ->
        if (totalMoveDistanceForTutorial > 1.8f && (abs(yawDelta) + abs(pitchDelta) > 0f || totalMoveDistanceForTutorial > 2.5f)) {
          TutorialStep.TORCH_CROUCH
        } else TutorialStep.MOVE_LOOK

      TutorialStep.TORCH_CROUCH ->
        if (tutorialToggledTorch && tutorialCrouched) TutorialStep.BOTTLE_LURE
        else TutorialStep.TORCH_CROUCH

      TutorialStep.BOTTLE_LURE ->
        if (tutorialThrewBottle) TutorialStep.LOCKER_BREATH
        else TutorialStep.BOTTLE_LURE

      TutorialStep.LOCKER_BREATH ->
        if (tutorialHidInLocker && !isHiding) TutorialStep.EXIT_VESTIBULE
        else TutorialStep.LOCKER_BREATH

      TutorialStep.EXIT_VESTIBULE ->
        if (tutorialGateOpened) TutorialStep.COMPLETE
        else TutorialStep.EXIT_VESTIBULE

      TutorialStep.COMPLETE -> TutorialStep.COMPLETE
    }
  }

  private fun updateInteractionPrompt() {
    if (isHiding) {
      activePrompt = PromptInfo("Exit Steel Locker", "EXIT")
      return
    }

    val nearFuse = fuses.firstOrNull { !it.collected && hypot(it.x - playerX, it.z - playerZ) < 2.1f }
    if (nearFuse != null) {
      activePrompt = PromptInfo("Pick up ${nearFuse.name}", "TAKE FUSE")
      return
    }

    if (hypot(HospitalMap.BREAKER_X - playerX, HospitalMap.BREAKER_Z - playerZ) < 2.35f) {
      val ready = fuses.count { it.collected && !it.installed }
      val installed = fuses.count { it.installed }
      activePrompt = if (ready > 0) {
        PromptInfo("Install $ready Fuse(s) into Central Breaker", "INSTALL")
      } else if (installed < 4) {
        PromptInfo("Central Breaker ($installed/4 Installed — Find fuses)", "INSPECT")
      } else {
        PromptInfo("Central Breaker Powered (4/4)", "POWERED")
      }
      return
    }

    if (powerRestored && hypot(HospitalMap.EXIT_X - playerX, HospitalMap.EXIT_Z - playerZ) < 2.5f) {
      activePrompt = PromptInfo("Open Emergency Exit Shutter", "ESCAPE")
      return
    }

    val nearNote = notes.firstOrNull { hypot(it.x - playerX, it.z - playerZ) < 2.0f }
    if (nearNote != null) {
      activePrompt = PromptInfo("Read \"${nearNote.title}\"", "READ")
      return
    }

    val nearBottle = bottles.firstOrNull { !it.collected && hypot(it.x - playerX, it.z - playerZ) < 2.0f }
    if (nearBottle != null) {
      activePrompt = if (bottlesCount < 3) {
        PromptInfo("Pick up Empty Saline Bottle", "TAKE")
      } else {
        PromptInfo("Bottle pouch full (3/3)", "FULL")
      }
      return
    }

    val nearLocker = lockers.firstOrNull { hypot(it.x - playerX, it.z - playerZ) < 1.95f }
    if (nearLocker != null) {
      activePrompt = PromptInfo("Hide inside Steel Locker", "HIDE")
      return
    }

    val nearDoor = findNearestDoor(2.3f)
    if (nearDoor != null) {
      activePrompt = PromptInfo(
        if (nearDoor.targetOpen < 0.5f) "Open Ward Door" else "Close Ward Door",
        if (nearDoor.targetOpen < 0.5f) "OPEN" else "CLOSE"
      )
      return
    }

    activePrompt = null
  }

  private fun normalizeAngle(a: Float): Float {
    var v = a
    while (v > PI.toFloat()) v -= (2f * PI.toFloat())
    while (v < -PI.toFloat()) v += (2f * PI.toFloat())
    return v
  }

  private fun lerpAngle(a: Float, b: Float, t: Float): Float =
    normalizeAngle(a + normalizeAngle(b - a) * t)
}
