package com.example.game.model

enum class ScreenState {
  TITLE,
  INTRO,
  PLAYING,
  PAUSED,
  NOTE_READER,
  CAUGHT,
  ENDING
}

enum class TorchMode(val label: String, val power: Float, val range: Float, val drainPerSec: Float) {
  OFF("OFF", 0f, 0f, -4.2f),
  LOW("LOW", 0.52f, 11.5f, 1.5f),
  HIGH("HIGH", 1.0f, 20.5f, 4.8f)
}

enum class NurseAlertState(val label: String) {
  PATROL("Roaming"),
  SUSPICIOUS("Alerted"),
  INVESTIGATE("Searching"),
  HUNT("Pursuing"),
  SEARCH_LOCKER("Checking Locker"),
  FINAL_CHASE("Relentless Pursuit")
}

enum class EndingType(
  val title: String,
  val subtitle: String,
  val epilogue: String
) {
  SURVIVAL_STANDARD(
    title = "Emergency Exit",
    subtitle = "Escaped Wing C Before Dawn",
    epilogue = "The auxiliary shutter groaned upward just as footsteps thundered behind you. You spilled out onto the damp hillside rain, alive—yet as the generator echoed behind the rusted gate, a pale silhouette paused behind the third-floor glass."
  ),
  TRUTH_LEDGER(
    title = "Truth in the Ledger",
    subtitle = "True Ending — The 1997 Blackout Uncovered",
    epilogue = "Clutching Matron Lim's sealed ledger and the ward logs, you forced the Emergency Shutter open into the cold Changi night. Inside the soot-stained pages lay the names of the patients left behind during the final blackout—and why the Matron never stopped making her rounds."
  ),
  PERMANENT_DARK(
    title = "Permanent Dark",
    subtitle = "Claimed by Wing C",
    epilogue = "Your torch sputtered out against cold terrazzo tiles. Somewhere above the silent ward, a single brass fuse dropped from an unpowered socket, and the heavy chime of keys faded back into the dark."
  )
}

enum class TutorialStep(val title: String, val instruction: String) {
  MOVE_LOOK(
    "Step 1/5 — Orient & Move",
    "Look around (right drag / mouse / right stick) and move forward (left stick / WASD)."
  ),
  TORCH_CROUCH(
    "Step 2/5 — Torch & Stealth",
    "Cycle your Torch (Low keeps you hidden; High reveals dark corners) and toggle Crouch to move quietly."
  ),
  BOTTLE_LURE(
    "Step 3/5 — Glass Lure",
    "Pick up the empty Saline Bottle nearby and throw it toward the corner to test sound lures."
  ),
  LOCKER_BREATH(
    "Step 4/5 — Locker & Breath",
    "Step up to the rusted Locker to hide, hold Breath while watching the meter, then release inside the green zone."
  ),
  EXIT_VESTIBULE(
    "Step 5/5 — Enter Wing C",
    "Open the Vestibule Door north of you. Locate all 4 Fuses and install them in the Central Breaker Room."
  ),
  COMPLETE("", "")
}

data class SettingsState(
  val lookSensitivity: Float = 1.0f,
  val subtitlesEnabled: Boolean = true,
  val brightness: Float = 1.0f,
  val reducedScares: Boolean = false,
  val masterVolume: Float = 0.85f,
  val musicVolume: Float = 0.70f,
  val sfxVolume: Float = 0.90f,
  val showControlHints: Boolean = true
)

data class CheckpointData(
  val hasCheckpoint: Boolean = false,
  val fusesCollectedMask: Int = 0,
  val fusesInstalledMask: Int = 0,
  val notesReadMask: Int = 0,
  val secretLedgerFound: Boolean = false,
  val playerX: Float = 6.5f,
  val playerZ: Float = 53.3f,
  val playerYaw: Float = 0f,
  val elapsedSeconds: Float = 0f,
  val bottlesCount: Int = 1,
  val torchBattery: Float = 100f
)

data class PromptInfo(
  val text: String,
  val actionLabel: String
)

data class SubtitleCue(
  val id: Long,
  val text: String,
  val isUrgent: Boolean
)

data class GameUiState(
  val screen: ScreenState = ScreenState.TITLE,
  val zoneName: String = "Entry Vestibule",
  val objectiveText: String = "Locate 4 Fuses & restore the Central Breaker (0/4)",
  val fusesCollectedCount: Int = 0,
  val fusesInstalledCount: Int = 0,
  val bottlesCount: Int = 0,
  val notesFoundCount: Int = 0,
  val totalNotesCount: Int = 8,
  val secretLedgerFound: Boolean = false,
  val torchMode: TorchMode = TorchMode.LOW,
  val torchBattery: Float = 100f,
  val stamina: Float = 100f,
  val isCrouching: Boolean = false,
  val isSprinting: Boolean = false,
  val isHiding: Boolean = false,
  val isHoldingBreath: Boolean = false,
  val breathMeter: Float = 100f,
  val breathDangerActive: Boolean = false,
  val breathSweetSpotMin: Float = 18f,
  val breathSweetSpotMax: Float = 40f,
  val isAimingBottle: Boolean = false,
  val nurseDistance: Float = 99f,
  val nurseAlertState: NurseAlertState = NurseAlertState.PATROL,
  val powerRestored: Boolean = false,
  val elapsedSeconds: Float = 0f,
  val timesSpotted: Int = 0,
  val activePrompt: PromptInfo? = null,
  val activeNote: LoreNote? = null,
  val collectedNotes: List<LoreNote> = emptyList(),
  val tutorialStep: TutorialStep = TutorialStep.MOVE_LOOK,
  val subtitleCue: SubtitleCue? = null,
  val statusBanner: String? = null,
  val endingType: EndingType = EndingType.SURVIVAL_STANDARD,
  val hasCheckpoint: Boolean = false,
  val isGamepadConnected: Boolean = false,
  val isKeyboardMouseActive: Boolean = false,
  val settings: SettingsState = SettingsState()
)

data class InputState(
  val moveX: Float = 0f, // -1 left .. +1 right
  val moveY: Float = 0f, // +1 forward .. -1 backward
  val lookDeltaX: Float = 0f,
  val lookDeltaY: Float = 0f,
  val stickLookX: Float = 0f,
  val stickLookY: Float = 0f,
  val sprintHeld: Boolean = false,
  val breathHeld: Boolean = false,
  val aimBottleHeld: Boolean = false
)
