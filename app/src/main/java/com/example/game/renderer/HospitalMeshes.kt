package com.example.game.renderer

import com.example.game.model.HospitalMap
import com.example.game.model.PropKind

/**
 * Builds all 3D hospital architecture, detailed props, doors, lockers, items,
 * and articulated Nurse ("Matron Lim") meshes.
 */
object HospitalMeshes {

  fun buildStaticHospital(): GlMesh {
    val mb = MeshBuilder(65536)
    val cs = HospitalMap.CELL_SIZE
    val h = HospitalMap.CEILING_HEIGHT

    for (r in 0 until HospitalMap.ROWS) {
      for (c in 0 until HospitalMap.COLS) {
        val ch = HospitalMap.charAt(c, r)
        val x0 = c * cs
        val x1 = (c + 1) * cs
        val z0 = r * cs
        val z1 = (r + 1) * cs
        val cx = (x0 + x1) * 0.5f
        val cz = (z0 + z1) * 0.5f

        if (ch != '#') {
          // Floor quad
          mb.quad(
            x0, 0f, z1, 0f, 1f,
            x1, 0f, z1, 1f, 1f,
            x1, 0f, z0, 1f, 0f,
            x0, 0f, z0, 0f, 0f,
            0f, 1f, 0f,
            ProceduralTextures.LAYER_FLOOR_TILE
          )
          // Ceiling quad
          mb.quad(
            x0, h, z0, 0f, 0f,
            x1, h, z0, 1f, 0f,
            x1, h, z1, 1f, 1f,
            x0, h, z1, 0f, 1f,
            0f, -1f, 0f,
            ProceduralTextures.LAYER_CEILING
          )
          // Overhead conduit pipe along spine rows 10 and 15
          if (r == 10 || r == 15) {
            mb.box(cx, h - 0.14f, cz - 0.85f, cs * 0.5f, 0.05f, 0.05f, ProceduralTextures.LAYER_RUST_METAL, 0.5f)
          }
          // Doorframe headers for 'D' cells
          if (ch == 'D') {
            val horiz = HospitalMap.isWall(c - 1, r) && HospitalMap.isWall(c + 1, r)
            if (horiz) {
              mb.box(cx, h - 0.35f, cz, cs * 0.5f, 0.35f, 0.12f, ProceduralTextures.LAYER_RUST_METAL, 0.4f)
            } else {
              mb.box(cx, h - 0.35f, cz, 0.12f, 0.35f, cs * 0.5f, ProceduralTextures.LAYER_RUST_METAL, 0.4f)
            }
          }
          // Emergency Exit Shutter at 'E'
          if (ch == 'E') {
            mb.box(cx, 1.3f, z1 - 0.15f, cs * 0.5f, 1.3f, 0.12f, ProceduralTextures.LAYER_RUST_METAL, 0.5f)
            // Exit glowing sign bar above shutter
            mb.box(cx, 2.75f, z1 - 0.25f, 0.42f, 0.14f, 0.06f, ProceduralTextures.LAYER_ACCENT, 1f, tintMode = 1f)
          }
        } else {
          // Wall faces exposed to open tiles + realistic baseboards
          if (!HospitalMap.isWall(c, r + 1)) { // South face (+Z)
            mb.quad(x0, 0f, z1, 0f, 1f, x1, 0f, z1, 1f, 1f, x1, h, z1, 1f, 0f, x0, h, z1, 0f, 0f, 0f, 0f, 1f, ProceduralTextures.LAYER_WALL_PLASTER)
            mb.box(cx, 0.08f, z1 + 0.02f, cs * 0.5f, 0.08f, 0.02f, ProceduralTextures.LAYER_RUST_METAL, 0.4f)
          }
          if (!HospitalMap.isWall(c, r - 1)) { // North face (-Z)
            mb.quad(x1, 0f, z0, 0f, 1f, x0, 0f, z0, 1f, 1f, x0, h, z0, 1f, 0f, x1, h, z0, 0f, 0f, 0f, 0f, -1f, ProceduralTextures.LAYER_WALL_PLASTER)
            mb.box(cx, 0.08f, z0 - 0.02f, cs * 0.5f, 0.08f, 0.02f, ProceduralTextures.LAYER_RUST_METAL, 0.4f)
          }
          if (!HospitalMap.isWall(c + 1, r)) { // East face (+X)
            mb.quad(x1, 0f, z1, 0f, 1f, x1, 0f, z0, 1f, 1f, x1, h, z0, 1f, 0f, x1, h, z1, 0f, 0f, 1f, 0f, 0f, ProceduralTextures.LAYER_WALL_PLASTER)
            mb.box(x1 + 0.02f, 0.08f, cz, 0.02f, 0.08f, cs * 0.5f, ProceduralTextures.LAYER_RUST_METAL, 0.4f)
          }
          if (!HospitalMap.isWall(c - 1, r)) { // West face (-X)
            mb.quad(x0, 0f, z0, 0f, 1f, x0, 0f, z1, 1f, 1f, x0, h, z1, 1f, 0f, x0, h, z0, 0f, 0f, -1f, 0f, 0f, ProceduralTextures.LAYER_WALL_PLASTER)
            mb.box(x0 - 0.02f, 0.08f, cz, 0.02f, 0.08f, cs * 0.5f, ProceduralTextures.LAYER_RUST_METAL, 0.4f)
          }
        }
      }
    }

    // Add detailed 3D hospital furniture & environmental storytelling props
    for (p in HospitalMap.createPropBoxes()) {
      when (p.kind) {
        PropKind.BED -> {
          // Steel bed frame legs & stained mattress + pillow
          mb.box(p.x - p.halfW + 0.06f, 0.22f, p.z - p.halfD + 0.06f, 0.04f, 0.22f, 0.04f, ProceduralTextures.LAYER_RUST_METAL)
          mb.box(p.x + p.halfW - 0.06f, 0.22f, p.z - p.halfD + 0.06f, 0.04f, 0.22f, 0.04f, ProceduralTextures.LAYER_RUST_METAL)
          mb.box(p.x - p.halfW + 0.06f, 0.22f, p.z + p.halfD - 0.06f, 0.04f, 0.22f, 0.04f, ProceduralTextures.LAYER_RUST_METAL)
          mb.box(p.x + p.halfW - 0.06f, 0.22f, p.z + p.halfD - 0.06f, 0.04f, 0.22f, 0.04f, ProceduralTextures.LAYER_RUST_METAL)
          mb.box(p.x, 0.46f, p.z, p.halfW, 0.05f, p.halfD, ProceduralTextures.LAYER_RUST_METAL)
          mb.box(p.x, 0.58f, p.z, p.halfW * 0.92f, 0.09f, p.halfD * 0.94f, ProceduralTextures.LAYER_WOOD_FABRIC)
          mb.box(p.x, 0.70f, p.z - p.halfD * 0.65f, p.halfW * 0.6f, 0.05f, 0.18f, ProceduralTextures.LAYER_WOOD_FABRIC)
          // Headboard bars
          mb.box(p.x, 0.78f, p.z - p.halfD + 0.04f, p.halfW, 0.34f, 0.03f, ProceduralTextures.LAYER_RUST_METAL)
        }
        PropKind.SURGICAL_TABLE -> {
          mb.box(p.x, 0.38f, p.z, 0.24f, 0.38f, 0.32f, ProceduralTextures.LAYER_RUST_METAL)
          mb.box(p.x, 0.80f, p.z, p.halfW, 0.07f, p.halfD, ProceduralTextures.LAYER_RUST_METAL)
          mb.box(p.x, 0.89f, p.z, p.halfW * 0.85f, 0.04f, p.halfD * 0.88f, ProceduralTextures.LAYER_WOOD_FABRIC)
          // Overhead dead surgical lamp fixture
          mb.box(p.x, h - 0.45f, p.z, 0.05f, 0.45f, 0.05f, ProceduralTextures.LAYER_RUST_METAL)
          mb.box(p.x, h - 0.92f, p.z, 0.38f, 0.06f, 0.38f, ProceduralTextures.LAYER_RUST_METAL)
        }
        PropKind.MORGUE_CABINET -> {
          mb.box(p.x, p.height * 0.5f, p.z, p.halfW, p.height * 0.5f, p.halfD, ProceduralTextures.LAYER_RUST_METAL)
          // 4 protruding cryo drawer fronts
          for (row in 0..1) {
            for (col in 0..1) {
              val dx = p.x + (col - 0.5f) * p.halfW * 0.95f
              val dy = 0.55f + row * 0.65f
              mb.box(dx, dy, p.z + p.halfD + 0.03f, p.halfW * 0.42f, 0.25f, 0.03f, ProceduralTextures.LAYER_RUST_METAL)
            }
          }
        }
        PropKind.GURNEY -> {
          mb.box(p.x, 0.35f, p.z, p.halfW * 0.7f, 0.35f, p.halfD * 0.7f, ProceduralTextures.LAYER_RUST_METAL)
          mb.box(p.x, 0.74f, p.z, p.halfW, 0.06f, p.halfD, ProceduralTextures.LAYER_WOOD_FABRIC)
        }
        PropKind.DESK -> {
          mb.box(p.x - p.halfW + 0.08f, 0.40f, p.z, 0.06f, 0.40f, p.halfD * 0.88f, ProceduralTextures.LAYER_WOOD_FABRIC)
          mb.box(p.x + p.halfW - 0.08f, 0.40f, p.z, 0.06f, 0.40f, p.halfD * 0.88f, ProceduralTextures.LAYER_WOOD_FABRIC)
          mb.box(p.x, 0.82f, p.z, p.halfW, 0.04f, p.halfD, ProceduralTextures.LAYER_WOOD_FABRIC)
        }
        PropKind.BOILER_PIPE -> {
          mb.box(p.x, 1.15f, p.z, p.halfW, 1.15f, p.halfD, ProceduralTextures.LAYER_RUST_METAL)
        }
      }
    }

    // Ceiling fluorescent light housings
    for (cl in HospitalMap.createCeilingLights()) {
      mb.box(cl.x, h - 0.04f, cl.z, 0.45f, 0.04f, 0.14f, ProceduralTextures.LAYER_ACCENT, 1f, tintMode = if (cl.isEmergencyRed) 1f else 0f)
    }

    // Central Breaker Panel housing
    mb.box(HospitalMap.BREAKER_X - 0.18f, 1.35f, HospitalMap.BREAKER_Z, 0.12f, 0.65f, 0.55f, ProceduralTextures.LAYER_RUST_METAL)

    return mb.uploadVbo()
  }

  /** Hinged door panel pivoted around local X = 0, extending to +2.55m along +X (width) and 2.4m tall. */
  fun buildDoorPanel(): GlMesh {
    val mb = MeshBuilder(1024)
    val w = HospitalMap.CELL_SIZE - 0.06f
    mb.box(w * 0.5f, 1.22f, 0f, w * 0.5f, 1.22f, 0.055f, ProceduralTextures.LAYER_RUST_METAL, 0.45f)
    // Door handle bar
    mb.box(w * 0.84f, 1.05f, 0.08f, 0.08f, 0.03f, 0.03f, ProceduralTextures.LAYER_ACCENT)
    mb.box(w * 0.84f, 1.05f, -0.08f, 0.08f, 0.03f, 0.03f, ProceduralTextures.LAYER_ACCENT)
    // Small frosted upper observation slit frame
    mb.box(w * 0.5f, 1.78f, 0f, 0.22f, 0.28f, 0.07f, ProceduralTextures.LAYER_ACCENT, 1f, tintMode = 3f)
    return mb.uploadVbo()
  }

  /** Hollow 3-sided steel locker cabinet centered at (0,0,0), opening facing +Z. */
  fun buildLockerBody(): GlMesh {
    val mb = MeshBuilder(1024)
    val hw = 0.46f
    val hd = 0.42f
    val h = 2.15f
    // Back wall (-Z)
    mb.box(0f, h * 0.5f, -hd, hw, h * 0.5f, 0.03f, ProceduralTextures.LAYER_RUST_METAL)
    // Left wall (-X)
    mb.box(-hw, h * 0.5f, 0f, 0.03f, h * 0.5f, hd, ProceduralTextures.LAYER_RUST_METAL)
    // Right wall (+X)
    mb.box(hw, h * 0.5f, 0f, 0.03f, h * 0.5f, hd, ProceduralTextures.LAYER_RUST_METAL)
    // Top & Bottom
    mb.box(0f, h, 0f, hw, 0.03f, hd, ProceduralTextures.LAYER_RUST_METAL)
    mb.box(0f, 0.03f, 0f, hw, 0.03f, hd, ProceduralTextures.LAYER_RUST_METAL)
    return mb.uploadVbo()
  }

  /** Slatted locker door pivoted at left edge (X = 0) extending +0.92m along +X so player sees out through slats! */
  fun buildLockerDoor(): GlMesh {
    val mb = MeshBuilder(1024)
    val w = 0.90f
    // Lower solid door panel (0.05..1.30m)
    mb.box(w * 0.5f, 0.68f, 0f, w * 0.5f, 0.63f, 0.025f, ProceduralTextures.LAYER_RUST_METAL)
    // Side vertical stiles around upper vent slats (1.30..1.85m)
    mb.box(0.06f, 1.58f, 0f, 0.06f, 0.28f, 0.025f, ProceduralTextures.LAYER_RUST_METAL)
    mb.box(w - 0.06f, 1.58f, 0f, 0.06f, 0.28f, 0.025f, ProceduralTextures.LAYER_RUST_METAL)
    // 4 horizontal louver slats with real gaps between them to peek through!
    for (i in 0 until 4) {
      mb.box(w * 0.5f, 1.38f + i * 0.12f, 0f, w * 0.42f, 0.02f, 0.02f, ProceduralTextures.LAYER_RUST_METAL)
    }
    // Top header panel (1.85..2.12m)
    mb.box(w * 0.5f, 1.98f, 0f, w * 0.5f, 0.14f, 0.025f, ProceduralTextures.LAYER_RUST_METAL)
    return mb.uploadVbo()
  }

  fun buildFuseMesh(): GlMesh {
    val mb = MeshBuilder(512)
    // Glowing brass/amber cartridge sitting on small tray
    mb.box(0f, 0.04f, 0f, 0.16f, 0.04f, 0.10f, ProceduralTextures.LAYER_RUST_METAL)
    mb.box(0f, 0.11f, 0f, 0.12f, 0.055f, 0.055f, ProceduralTextures.LAYER_ACCENT, 1f, tintMode = 1f)
    return mb.uploadVbo()
  }

  fun buildBottleMesh(): GlMesh {
    val mb = MeshBuilder(512)
    mb.box(0f, 0.11f, 0f, 0.065f, 0.11f, 0.065f, ProceduralTextures.LAYER_ACCENT, 1f, tintMode = 3f)
    mb.box(0f, 0.26f, 0f, 0.03f, 0.05f, 0.03f, ProceduralTextures.LAYER_ACCENT, 1f, tintMode = 3f)
    return mb.uploadVbo()
  }

  fun buildNoteMesh(): GlMesh {
    val mb = MeshBuilder(256)
    mb.box(0f, 0.02f, 0f, 0.14f, 0.01f, 0.18f, ProceduralTextures.LAYER_ACCENT, 1f, tintMode = 4f)
    mb.box(0f, 0.035f, -0.15f, 0.06f, 0.012f, 0.025f, ProceduralTextures.LAYER_RUST_METAL)
    return mb.uploadVbo()
  }

  fun buildHazardMesh(): GlMesh {
    val mb = MeshBuilder(512)
    mb.box(0f, 0.025f, 0f, 0.28f, 0.025f, 0.20f, ProceduralTextures.LAYER_RUST_METAL)
    mb.box(-0.08f, 0.06f, 0.04f, 0.04f, 0.04f, 0.04f, ProceduralTextures.LAYER_ACCENT)
    mb.box(0.09f, 0.055f, -0.05f, 0.05f, 0.03f, 0.03f, ProceduralTextures.LAYER_RUST_METAL)
    return mb.uploadVbo()
  }

  fun buildUnitBox(layer: Int, tintMode: Float = 0f): GlMesh {
    val mb = MeshBuilder(256)
    mb.box(0f, 0f, 0f, 0.5f, 0.5f, 0.5f, layer, 1f, tintMode)
    return mb.uploadVbo()
  }

  // Articulated Nurse Parts (rendered hierarchically in HospitalGlRenderer)
  fun buildNurseTorso(): GlMesh {
    val mb = MeshBuilder(1024)
    // Tapered vintage nurse dress/coat + dark apron front
    mb.box(0f, 0.92f, 0f, 0.22f, 0.34f, 0.14f, ProceduralTextures.LAYER_NURSE)
    mb.box(0f, 0.90f, -0.145f, 0.16f, 0.32f, 0.015f, ProceduralTextures.LAYER_NURSE, 1f, tintMode = 2f)
    // Lower skirt hem
    mb.box(0f, 0.44f, 0f, 0.25f, 0.24f, 0.17f, ProceduralTextures.LAYER_NURSE, 1f, tintMode = 2f)
    return mb.uploadVbo()
  }

  fun buildNurseHead(): GlMesh {
    val mb = MeshBuilder(1024)
    // Gaunt pale head + vintage winged nurse cap + eerie pale glowing eyes (-Z is forward)
    mb.box(0f, 0.14f, 0f, 0.115f, 0.14f, 0.125f, ProceduralTextures.LAYER_NURSE)
    // Winged nurse cap
    mb.box(0f, 0.31f, 0.02f, 0.15f, 0.055f, 0.08f, ProceduralTextures.LAYER_NURSE)
    mb.box(0f, 0.32f, -0.055f, 0.04f, 0.04f, 0.015f, ProceduralTextures.LAYER_ACCENT, 1f, tintMode = 1f)
    // Pinpoint pale glowing eyes facing -Z
    mb.box(-0.045f, 0.16f, -0.128f, 0.016f, 0.012f, 0.008f, ProceduralTextures.LAYER_ACCENT, 1f, tintMode = 1f)
    mb.box(0.045f, 0.16f, -0.128f, 0.016f, 0.012f, 0.008f, ProceduralTextures.LAYER_ACCENT, 1f, tintMode = 1f)
    return mb.uploadVbo()
  }

  fun buildNurseLimb(length: Float, radius: Float, withLantern: Boolean = false): GlMesh {
    val mb = MeshBuilder(512)
    mb.box(0f, -length * 0.5f, 0f, radius, length * 0.5f, radius, ProceduralTextures.LAYER_NURSE)
    if (withLantern) {
      // Surgical rusted lantern hanging from hand
      mb.box(0f, -length - 0.10f, -0.08f, 0.06f, 0.10f, 0.06f, ProceduralTextures.LAYER_RUST_METAL)
      mb.box(0f, -length - 0.10f, -0.08f, 0.04f, 0.06f, 0.04f, ProceduralTextures.LAYER_ACCENT, 1f, tintMode = 1f)
    }
    return mb.uploadVbo()
  }
}
