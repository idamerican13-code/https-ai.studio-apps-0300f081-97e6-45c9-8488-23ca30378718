package com.example

import com.example.game.engine.*
import com.example.game.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class WardZeroPlaytest {

  @Test
  fun mapConnectivity_allFusesLockersNotesBreakerAndExitAreReachable() {
    val startCol = HospitalMap.worldToCol(HospitalMap.PLAYER_SPAWN_X)
    val startRow = HospitalMap.worldToRow(HospitalMap.PLAYER_SPAWN_Z)

    val visited = HashSet<Pair<Int, Int>>()
    val queue = ArrayDeque<Pair<Int, Int>>()
    queue.add(startCol to startRow)
    visited.add(startCol to startRow)

    val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    while (queue.isNotEmpty()) {
      val (c, r) = queue.removeFirst()
      for ((dc, dr) in dirs) {
        val nc = c + dc
        val nr = r + dr
        if (nc !in 0 until HospitalMap.COLS || nr !in 0 until HospitalMap.ROWS) continue
        if (HospitalMap.isWall(nc, nr)) continue
        val p = nc to nr
        if (visited.add(p)) queue.add(p)
      }
    }

    val sim = GameSimulation()
    for (fuse in sim.fuses) {
      val fc = HospitalMap.worldToCol(fuse.x)
      val fr = HospitalMap.worldToRow(fuse.z)
      assertTrue("Fuse ${fuse.name} must be reachable", (fc to fr) in visited)
    }
    for (locker in sim.lockers) {
      val lc = HospitalMap.worldToCol(locker.exitX)
      val lr = HospitalMap.worldToRow(locker.exitZ)
      assertTrue("Locker ${locker.id} exit must be reachable", (lc to lr) in visited)
      assertFalse("Locker ${locker.id} body should not be inside wall", HospitalMap.isWall(HospitalMap.worldToCol(locker.x), HospitalMap.worldToRow(locker.z)))
    }
    for (note in sim.notes) {
      val nc = HospitalMap.worldToCol(note.x)
      val nr = HospitalMap.worldToRow(note.z)
      assertTrue("Note ${note.title} must be reachable", (nc to nr) in visited)
    }
    val bc = HospitalMap.worldToCol(HospitalMap.BREAKER_X)
    val br = HospitalMap.worldToRow(HospitalMap.BREAKER_Z)
    assertTrue("Central Breaker must be reachable", (bc to br) in visited)

    val ec = HospitalMap.worldToCol(HospitalMap.EXIT_X)
    val er = HospitalMap.worldToRow(HospitalMap.EXIT_Z)
    assertTrue("South Exit must be reachable", (ec to er) in visited)
  }

  @Test
  fun fullGameplayLoop_tutorialLureLockerBreathFusesAndMultipleEndings() {
    val sim = GameSimulation()
    val settings = SettingsState()
    sim.resetNewGame(skipTutorial = false)
    assertEquals(TutorialStep.MOVE_LOOK, sim.tutorialStep)

    // 1. Move & look to advance tutorial
    repeat(35) {
      sim.step(0.03f, InputState(moveY = 1f, lookDeltaX = 12f), settings)
    }
    assertEquals(TutorialStep.TORCH_CROUCH, sim.tutorialStep)

    // 2. Toggle torch & crouch
    sim.cycleTorch()
    sim.toggleCrouch()
    sim.step(0.03f, InputState(), settings)
    assertEquals(TutorialStep.BOTTLE_LURE, sim.tutorialStep)

    // 3. Pick up vestibule bottle & throw it
    val vestBottle = sim.bottles.first()
    sim.playerX = vestBottle.x
    sim.playerZ = vestBottle.z
    sim.interact()
    assertEquals(1, sim.bottlesCount)
    sim.throwBottle()
    assertEquals(0, sim.bottlesCount)
    assertTrue("Thrown bottle should be flying", sim.activeBottles.isNotEmpty())
    // Step until bottle shatters
    repeat(60) { sim.step(0.03f, InputState(), settings) }
    assertTrue("Bottle should shatter on impact", sim.activeBottles.isEmpty())
    assertEquals(TutorialStep.LOCKER_BREATH, sim.tutorialStep)

    // 4. Enter locker, hold breath into sweet spot, release & exit
    val vestLocker = sim.lockers.first()
    sim.playerX = vestLocker.exitX
    sim.playerZ = vestLocker.exitZ
    sim.interact()
    assertTrue(sim.isHiding)
    repeat(120) { sim.step(0.03f, InputState(breathHeld = true), settings) }
    assertTrue("Breath meter should drain into or past sweet spot", sim.breathMeter < 45f)
    sim.step(0.03f, InputState(breathHeld = false), settings)
    sim.interact() // exit locker
    assertFalse(sim.isHiding)
    assertEquals(TutorialStep.EXIT_VESTIBULE, sim.tutorialStep)

    // 5. Open Tutorial Gate to enter Wing C
    val gate = sim.doors.first { it.isTutorialGate }
    val (gx, gz) = HospitalMap.cellCenter(gate.col, gate.row)
    sim.playerX = gx
    sim.playerZ = gz + 1.2f
    sim.interact()
    assertTrue(sim.tutorialGateOpened)
    assertEquals(TutorialStep.COMPLETE, sim.tutorialStep)

    // 6. Test bottle lure drawing Nurse
    val (lureX, lureZ) = HospitalMap.cellCenter(11, 10)
    sim.nurseX = lureX + 4f
    sim.nurseZ = lureZ
    sim.playerX = lureX
    sim.playerZ = lureZ
    sim.bottlesCount = 1
    sim.throwBottle()
    repeat(60) { sim.step(0.03f, InputState(), settings) }
    assertTrue(
      "Nurse should investigate bottle shatter",
      sim.nurseAlertState == NurseAlertState.INVESTIGATE || sim.nurseAlertState == NurseAlertState.HUNT
    )

    // 7. Collect all 4 fuses, save checkpoint, install at Central Breaker, verify Final Chase & True Ending
    sim.fuses.forEach { fuse ->
      sim.playerX = fuse.x
      sim.playerZ = fuse.z
      sim.interact()
      assertTrue("${fuse.name} should be collected", fuse.collected)
    }

    val cp = sim.buildCheckpoint()
    assertEquals(0b1111, cp.fusesCollectedMask)

    sim.playerX = HospitalMap.BREAKER_X + 0.8f
    sim.playerZ = HospitalMap.BREAKER_Z
    sim.interact()
    assertTrue("Power should be restored after installing 4 fuses", sim.powerRestored)
    assertEquals(NurseAlertState.FINAL_CHASE, sim.nurseAlertState)

    // Read Secret Ledger -> True Ending
    val ledger = sim.notes.first { it.isSecretLedger }
    sim.playerX = ledger.x
    sim.playerZ = ledger.z
    sim.interact()
    assertTrue(ledger.read)

    // Escape at South Lobby Shutter
    sim.pendingEvents.clear()
    sim.playerX = HospitalMap.EXIT_X
    sim.playerZ = HospitalMap.EXIT_Z - 1.2f
    sim.interact()
    val escaped = sim.pendingEvents.filterIsInstance<SimEvent.PlayerEscaped>().firstOrNull()
    assertNotNull("Player should trigger escape", escaped)
    assertEquals(EndingType.TRUTH_LEDGER, escaped!!.ending)
  }

  @Test
  fun checkpointRestore_placesNurseFarFromPlayerAndPreservesProgress() {
    val sim = GameSimulation()
    sim.resetNewGame(skipTutorial = true)
    sim.fuses[0].collected = true
    sim.fuses[0].installed = true
    sim.notes[2].read = true
    val cp = sim.buildCheckpoint()

    val restored = GameSimulation()
    restored.restoreFromCheckpoint(cp)
    assertTrue(restored.fuses[0].installed)
    assertTrue(restored.notes[2].read)
    assertTrue(
      "Nurse must spawn safely far on checkpoint restore",
      hypot(restored.nurseX - restored.playerX, restored.nurseZ - restored.playerZ) > 16f
    )
  }
}
