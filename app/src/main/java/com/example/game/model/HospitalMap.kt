package com.example.game.model

import kotlin.math.PI

data class FuseItem(
  val id: Int,
  val bitMask: Int,
  val name: String,
  val rating: String,
  val wingName: String,
  val x: Float,
  val z: Float,
  var collected: Boolean = false,
  var installed: Boolean = false
)

data class BottleItem(
  val id: Int,
  val x: Float,
  val z: Float,
  var collected: Boolean = false
)

data class LockerSpot(
  val id: Int,
  val x: Float,
  val z: Float,
  val facingYaw: Float, // Radians: direction door faces into the room
  val exitX: Float,
  val exitZ: Float,
  var doorOpenAngle: Float = 0f // 0..1.65 rad
)

data class DoorObj(
  val id: Int,
  val col: Int,
  val row: Int,
  val horizontalAxis: Boolean, // true if doorway spans East-West (hinged on West edge)
  val isTutorialGate: Boolean = false,
  var openAmount: Float = 0f, // 0 = closed, 1 = 90 deg open
  var targetOpen: Float = 0f
)

data class HazardObj(
  val id: Int,
  val name: String,
  val x: Float,
  val z: Float,
  var cooldown: Float = 0f
)

data class LoreNote(
  val id: Int,
  val bitMask: Int,
  val title: String,
  val subtitle: String,
  val body: String,
  val x: Float,
  val z: Float,
  val isSecretLedger: Boolean = false,
  var read: Boolean = false
)

data class PropBox(
  val x: Float,
  val z: Float,
  val halfW: Float,
  val halfD: Float,
  val height: Float,
  val kind: PropKind
)

enum class PropKind {
  BED,
  SURGICAL_TABLE,
  MORGUE_CABINET,
  GURNEY,
  DESK,
  BOILER_PIPE
}

data class CeilingLight(
  val x: Float,
  val z: Float,
  val baseIntensity: Float,
  val phase: Float,
  val isEmergencyRed: Boolean = false
)

object HospitalMap {
  const val COLS = 28
  const val ROWS = 24
  const val CELL_SIZE = 2.6f
  const val CEILING_HEIGHT = 3.15f

  // 28 chars x 24 rows
  // # = Solid wall
  // . = Open floor
  // D = Hinged doorway
  // E = South Emergency Exit Shutter (opens after 4/4 fuses installed)
  val GRID: Array<String> = arrayOf(
    "############################", // 00
    "#......#......#......#.....#", // 01
    "#......#......#......#.....#", // 02
    "#......D......D......D.....#", // 03
    "#......#......#......#.....#", // 04
    "###D####......####D#####D###", // 05
    "#......#......#............#", // 06
    "#......D......D.....#......#", // 07
    "#......#......#............#", // 08
    "###D####......####D#####.###", // 09
    "#..........................#", // 10
    "#..###...###..###...###....#", // 11
    "#..###...#......#...###....#", // 12
    "#........D......D..........#", // 13
    "#..###...###..###...###....#", // 14
    "#..........................#", // 15
    "###D####......#####D########", // 16
    "#......#......#......#.....#", // 17
    "#......#......#......#.....#", // 18
    "#......#......#......#.....#", // 19
    "#......#......#......D.....#", // 20
    "#......#......#......#.....#", // 21
    "#......#..EE..#......#.....#", // 22
    "############################"  // 23
  )

  fun cellCenter(col: Int, row: Int): Pair<Float, Float> =
    Pair((col + 0.5f) * CELL_SIZE, (row + 0.5f) * CELL_SIZE)

  fun worldToCol(x: Float): Int = kotlin.math.floor((x / CELL_SIZE).toDouble()).toInt()
  fun worldToRow(z: Float): Int = kotlin.math.floor((z / CELL_SIZE).toDouble()).toInt()

  fun charAt(col: Int, row: Int): Char {
    if (row !in 0 until ROWS || col !in 0 until COLS) return '#'
    return GRID[row][col]
  }

  fun isWall(col: Int, row: Int): Boolean = charAt(col, row) == '#'

  fun zoneNameFor(x: Float, z: Float): String {
    val c = worldToCol(x)
    val r = worldToRow(z)
    return when {
      c in 1..6 && r in 1..4 -> "Operating Theatre"
      c in 8..13 && r in 1..4 -> "Sterilization Wing"
      c in 15..20 && r in 1..4 -> "Morgue & Cryo Bay"
      c in 22..26 && r in 1..4 -> "North Stairwell & Boiler"
      c in 1..6 && r in 6..8 -> "West Isolation Ward B"
      c in 8..13 && r in 6..9 -> "Nurses' North Atrium"
      c in 15..26 && r in 6..8 -> "East Service Corridor"
      c in 10..15 && r in 12..13 -> "Central Breaker Room"
      c in 1..6 && r in 17..22 -> "Entry Vestibule"
      c in 8..13 && r in 16..22 -> "South Exit Lobby"
      c in 15..20 && r in 17..22 -> "Records & Pharmacy"
      c in 22..26 && r in 17..22 -> "Matron's Sealed Office"
      else -> "Wing C Main Spine"
    }
  }

  fun createInitialDoors(): List<DoorObj> = listOf(
    DoorObj(id = 0, col = 7, row = 3, horizontalAxis = false),
    DoorObj(id = 1, col = 14, row = 3, horizontalAxis = false),
    DoorObj(id = 2, col = 21, row = 3, horizontalAxis = false),
    DoorObj(id = 3, col = 3, row = 5, horizontalAxis = true),
    DoorObj(id = 4, col = 18, row = 5, horizontalAxis = true),
    DoorObj(id = 5, col = 24, row = 5, horizontalAxis = true),
    DoorObj(id = 6, col = 7, row = 7, horizontalAxis = false),
    DoorObj(id = 7, col = 14, row = 7, horizontalAxis = false),
    DoorObj(id = 8, col = 3, row = 9, horizontalAxis = true),
    DoorObj(id = 9, col = 18, row = 9, horizontalAxis = true),
    DoorObj(id = 10, col = 9, row = 13, horizontalAxis = false),
    DoorObj(id = 11, col = 16, row = 13, horizontalAxis = false),
    DoorObj(id = 12, col = 3, row = 16, horizontalAxis = true, isTutorialGate = true),
    DoorObj(id = 13, col = 19, row = 16, horizontalAxis = true),
    DoorObj(id = 14, col = 21, row = 20, horizontalAxis = false)
  )

  fun createInitialFuses(): List<FuseItem> {
    val (x0, z0) = cellCenter(2, 7)   // West Isolation Ward B
    val (x1, z1) = cellCenter(2, 2)   // Operating Theatre
    val (x2, z2) = cellCenter(18, 2)  // Morgue & Cryo Bay
    val (x3, z3) = cellCenter(25, 2)  // North Stairwell & Boiler
    return listOf(
      FuseItem(0, 1 shl 0, "Ward B Main Fuse", "30A Ceramic", "West Isolation Ward B", x0, z0),
      FuseItem(1, 1 shl 1, "Theatre Aux Fuse", "45A Brass", "Operating Theatre", x1, z1),
      FuseItem(2, 1 shl 2, "Morgue Cryo Fuse", "60A Heavy", "Morgue & Cryo Bay", x2, z2),
      FuseItem(3, 1 shl 3, "Boiler Service Fuse", "80A Industrial", "North Boiler Stairwell", x3, z3)
    )
  }

  fun createInitialBottles(): List<BottleItem> {
    val spots = listOf(
      cellCenter(5, 19),  // Entry Vestibule tutorial bottle
      cellCenter(1, 10),  // West spine end
      cellCenter(11, 7),  // Nurses' North Atrium
      cellCenter(23, 8),  // East service hall
      cellCenter(5, 1),   // Operating Theatre corner
      cellCenter(12, 2),  // Sterilization wing
      cellCenter(16, 4),  // Morgue entrance
      cellCenter(17, 19), // Records & Pharmacy
      cellCenter(24, 18), // Matron's Sealed Office
      cellCenter(25, 13)  // East Spine alcoves
    )
    return spots.mapIndexed { idx, (x, z) -> BottleItem(id = idx, x = x, z = z) }
  }

  fun createInitialLockers(): List<LockerSpot> {
    fun locker(id: Int, col: Int, row: Int, yawDeg: Float): LockerSpot {
      val rad = (yawDeg * PI / 180.0).toFloat()
      val dirX = kotlin.math.sin(rad)
      val dirZ = -kotlin.math.cos(rad)
      val cx = (col + 0.5f) * CELL_SIZE - dirX * (CELL_SIZE * 0.5f - 0.46f)
      val cz = (row + 0.5f) * CELL_SIZE - dirZ * (CELL_SIZE * 0.5f - 0.46f)
      val ex = cx + dirX * 1.05f
      val ez = cz + dirZ * 1.05f
      return LockerSpot(id, cx, cz, rad, ex, ez)
    }
    return listOf(
      // 0: Entry Vestibule (against West wall at col 1, facing East = +90 deg)
      locker(0, 1, 19, 90f),
      // 1: Main Spine West-North alcove (col 6, row 10 facing South = 180 deg)
      locker(1, 6, 10, 180f),
      // 2: West Isolation Ward B (col 1, row 6 facing East = 90 deg)
      locker(2, 1, 6, 90f),
      // 3: Operating Theatre (col 5, row 4 facing North = 0 deg)
      locker(3, 5, 4, 0f),
      // 4: Sterilization Wing (col 8, row 1 facing East = 90 deg)
      locker(4, 8, 1, 90f),
      // 5: Morgue & Cryo Bay (col 15, row 1 facing East = 90 deg)
      locker(5, 15, 1, 90f),
      // 6: North Boiler Stairwell (col 26, row 2 facing West = -90 deg)
      locker(6, 26, 2, -90f),
      // 7: East Service Corridor (col 16, row 8 facing North = 0 deg)
      locker(7, 16, 8, 0f),
      // 8: Records & Pharmacy (col 15, row 21 facing East = 90 deg)
      locker(8, 15, 21, 90f),
      // 9: Matron's Sealed Office (col 26, row 21 facing West = -90 deg)
      locker(9, 26, 21, -90f)
    )
  }

  fun createInitialHazards(): List<HazardObj> {
    val spots = listOf(
      "Surgical Tray" to cellCenter(4, 3),
      "Rusted Tin Cans" to cellCenter(11, 10),
      "Scalpel Basin" to cellCenter(17, 3),
      "Collapsed IV Stand" to cellCenter(22, 7),
      "Glass Ampoules" to cellCenter(17, 18),
      "Loose Conduit" to cellCenter(22, 15)
    )
    return spots.mapIndexed { idx, (name, pos) -> HazardObj(idx, name, pos.first, pos.second) }
  }

  fun createInitialNotes(): List<LoreNote> {
    fun note(
      id: Int,
      title: String,
      subtitle: String,
      col: Int,
      row: Int,
      isSecret: Boolean = false,
      body: String
    ): LoreNote {
      val (x, z) = cellCenter(col, row)
      return LoreNote(id, 1 shl id, title, subtitle, body, x, z, isSecret)
    }
    return listOf(
      note(
        0,
        "Facility Evacuation Notice",
        "Entry Vestibule — Oct 14, 1997",
        col = 4,
        row = 20,
        body = "ALL CONTRACTORS: Wing C power mains have been isolated prior to demolition. Four heavy fuses were pulled from the Central Breaker Panel and stowed in Ward B, Theatre, Cryo, and Boiler Storage. Do NOT cross the shutter after sundown."
      ),
      note(
        1,
        "Shift Supervisor's Memo",
        "Central Breaker Room",
        col = 11,
        row = 12,
        body = "Three nights in a row the auxiliary breaker tripped at 23:40. Someone is still walking the upper corridor with a heavy key ring. Keep your torch on LOW beam—high beams seem to draw her straight across the hall."
      ),
      note(
        2,
        "Nurse's Chart #402",
        "West Isolation Ward B",
        col = 5,
        row = 7,
        body = "Patient beds stripped for relocation, yet Matron Lim refuses to sign off Ward B. She insists the night-shift count is still short by one. When footsteps echo down the linoleum, get inside a steel locker and hold your breath until she passes."
      ),
      note(
        3,
        "Anaesthetist's Fragment",
        "Operating Theatre",
        col = 2,
        row = 4,
        body = "We dropped a tray of saline bottles during the blackout and watched her pivot mid-stride toward the shatter. Sound carries further than sight down these tiles. Throw glass opposite the direction you intend to run."
      ),
      note(
        4,
        "Sterilization Log",
        "Sterilization Wing",
        col = 12,
        row = 1,
        body = "The steam valves seized again. Every fuse pulled out of the central bus seems to stir the building awake—floor relays hum louder, and the Matron walks faster each time a circuit closes."
      ),
      note(
        5,
        "Cold Storage Tag #04",
        "Morgue & Cryo Bay",
        col = 19,
        row = 3,
        body = "Drawer 04 stands empty, frost thawed since Tuesday. Whoever hid the 60A Cryo Fuse behind the refrigeration manifold left fingernail marks inside the latch."
      ),
      note(
        6,
        "Boiler Room Chalk Note",
        "North Boiler Stairwell",
        col = 23,
        row = 4,
        body = "South Exit Shutter runs straight off the 4-fuse central bus. Once the fourth fuse seats, the hallway strobes will kick on—don't look back, sprint straight to the South Lobby."
      ),
      note(
        7,
        "Matron Lim's Sealed Ledger",
        "Matron's Sealed Office (Secret)",
        col = 25,
        row = 19,
        isSecret = true,
        body = "CONFIDENTIAL — 1997 CLOSURE AUDIT: Four isolation patients were never transferred on the final lorry; their records were locked in Wing C when the substation failed. I stayed behind with my ward keys to keep the dark from taking them. If you restore the circuit, let the world read every page."
      )
    )
  }

  fun createPropBoxes(): List<PropBox> {
    fun prop(col: Float, row: Float, hw: Float, hd: Float, h: Float, kind: PropKind): PropBox =
      PropBox(col * CELL_SIZE, row * CELL_SIZE, hw, hd, h, kind)
    return listOf(
      // Entry Vestibule welcome desk
      prop(4.8f, 20.8f, 0.7f, 0.45f, 0.85f, PropKind.DESK),
      // West Isolation Ward B hospital beds
      prop(2.2f, 6.6f, 0.52f, 0.95f, 0.72f, PropKind.BED),
      prop(4.8f, 8.2f, 0.52f, 0.95f, 0.72f, PropKind.BED),
      // Operating Theatre surgical table
      prop(3.5f, 2.5f, 0.55f, 1.05f, 0.88f, PropKind.SURGICAL_TABLE),
      // Sterilization gurney
      prop(10.8f, 2.7f, 0.48f, 0.92f, 0.78f, PropKind.GURNEY),
      // Morgue cryo cabinets
      prop(19.6f, 1.6f, 0.95f, 0.45f, 1.85f, PropKind.MORGUE_CABINET),
      prop(16.6f, 3.8f, 0.50f, 0.85f, 0.75f, PropKind.GURNEY),
      // Boiler pipes
      prop(25.8f, 4.1f, 0.45f, 0.65f, 2.2f, PropKind.BOILER_PIPE),
      // Records & Pharmacy desk
      prop(18.5f, 19.2f, 0.85f, 0.45f, 0.85f, PropKind.DESK),
      // Matron's office desk
      prop(24.8f, 18.8f, 0.75f, 0.48f, 0.86f, PropKind.DESK)
    )
  }

  fun createCeilingLights(): List<CeilingLight> {
    val list = listOf(
      cellCenter(3, 19) to false,
      cellCenter(11, 20) to true, // South Exit emergency red
      cellCenter(12, 12) to false, // Central Breaker room
      cellCenter(4, 10) to false,
      cellCenter(13, 10) to false,
      cellCenter(23, 10) to false,
      cellCenter(4, 15) to false,
      cellCenter(18, 15) to false,
      cellCenter(10, 7) to false,
      cellCenter(23, 7) to false,
      cellCenter(3, 2) to false,
      cellCenter(17, 2) to false
    )
    return list.mapIndexed { idx, (pos, red) ->
      CeilingLight(pos.first, pos.second, if (red) 0.9f else 0.75f, idx * 1.37f, red)
    }
  }

  // Central Breaker Panel position (inside Central Breaker Room north-west wall)
  val BREAKER_X: Float = 10.6f * CELL_SIZE
  val BREAKER_Z: Float = 12.45f * CELL_SIZE

  // Main Emergency Exit Shutter center (Row 22, Col 10..11)
  val EXIT_X: Float = 11.0f * CELL_SIZE
  val EXIT_Z: Float = 22.35f * CELL_SIZE

  // Initial Player spawn (Entry Vestibule facing North toward tutorial bottle & door)
  val PLAYER_SPAWN_X: Float = 3.5f * CELL_SIZE
  val PLAYER_SPAWN_Z: Float = 21.0f * CELL_SIZE

  // Initial Nurse spawn (North Nurses' Atrium / Spine far from Entry Vestibule)
  val NURSE_SPAWN_X: Float = 18.5f * CELL_SIZE
  val NURSE_SPAWN_Z: Float = 7.5f * CELL_SIZE

  val PATROL_WAYPOINTS: List<Pair<Float, Float>> = listOf(
    cellCenter(11, 10),
    cellCenter(4, 10),
    cellCenter(24, 10),
    cellCenter(24, 15),
    cellCenter(13, 15),
    cellCenter(4, 15),
    cellCenter(3, 7),
    cellCenter(11, 7),
    cellCenter(18, 7),
    cellCenter(24, 7),
    cellCenter(11, 3),
    cellCenter(4, 2),
    cellCenter(18, 3),
    cellCenter(24, 2),
    cellCenter(18, 19),
    cellCenter(24, 20),
    cellCenter(11, 19)
  )
}
