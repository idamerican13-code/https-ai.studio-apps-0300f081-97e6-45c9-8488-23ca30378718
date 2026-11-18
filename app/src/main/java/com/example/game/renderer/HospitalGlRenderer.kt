package com.example.game.renderer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.game.engine.GameSimulation
import com.example.game.engine.ParticleKind
import com.example.game.model.HospitalMap
import com.example.game.model.NurseAlertState
import com.example.game.model.SettingsState
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import kotlin.random.Random

private data class Particle3D(
  var x: Float, var y: Float, var z: Float,
  var vx: Float, var vy: Float, var vz: Float,
  var life: Float,
  var maxLife: Float,
  var size: Float,
  var kind: ParticleKind
)

/**
 * OpenGL ES 3.0 PBR-inspired horror renderer with:
 * - Screen-space derivative bump mapping
 * - 2D grid DDA real-time flashlight wall-shadow raycasting via uMapTex
 * - Articulated Matron Lim 3D model + lantern point light
 * - Floating dust motes & glass/spark particles
 * - Dynamic corridor emergency lighting during power restoration
 */
class HospitalGlRenderer(
  private val simProvider: () -> GameSimulation,
  private val settingsProvider: () -> SettingsState,
  private val onFrameTick: (Float) -> Unit
) : GLSurfaceView.Renderer {

  private var program = 0
  private var texArrayId = 0
  private var mapTexId = 0

  private lateinit var staticMesh: GlMesh
  private lateinit var doorMesh: GlMesh
  private lateinit var lockerBodyMesh: GlMesh
  private lateinit var lockerDoorMesh: GlMesh
  private lateinit var fuseMesh: GlMesh
  private lateinit var bottleMesh: GlMesh
  private lateinit var noteMesh: GlMesh
  private lateinit var hazardMesh: GlMesh
  private lateinit var unitBoxGlow: GlMesh
  private lateinit var unitBoxGlass: GlMesh
  private lateinit var nurseTorsoMesh: GlMesh
  private lateinit var nurseHeadMesh: GlMesh
  private lateinit var nurseLegMesh: GlMesh
  private lateinit var nurseArmMesh: GlMesh
  private lateinit var nurseLanternArmMesh: GlMesh

  private val projMatrix = FloatArray(16)
  private val viewMatrix = FloatArray(16)
  private val modelMatrix = FloatArray(16)
  private val mvpMatrix = FloatArray(16)
  private val vpMatrix = FloatArray(16)

  private val mapBytes = ByteBuffer.allocateDirect(HospitalMap.COLS * HospitalMap.ROWS)
  private val particles = ArrayList<Particle3D>(128)
  private val dustMotes = ArrayList<Particle3D>(48)
  private var lastNanoTime = 0L
  private val rng = Random(97)

  // Uniform locations
  private var uMvpLoc = -1
  private var uModelLoc = -1
  private var uTexArrayLoc = -1
  private var uMapTexLoc = -1
  private var uCamPosLoc = -1
  private var uTorchPosLoc = -1
  private var uTorchDirLoc = -1
  private var uTorchPowerLoc = -1
  private var uTorchConeLoc = -1
  private var uNurseLanternLoc = -1
  private var uBreakerPosLoc = -1
  private var uExitPosLoc = -1
  private var uPowerRestoredLoc = -1
  private var uBrightnessLoc = -1
  private var uRedVignetteLoc = -1
  private var uTimeLoc = -1
  private var uOverrideTintLoc = -1
  private var uResolutionLoc = -1
  private var viewWidth = 1280f
  private var viewHeight = 720f

  fun spawnParticles(x: Float, y: Float, z: Float, kind: ParticleKind, count: Int) {
    synchronized(particles) {
      repeat(count) {
        if (particles.size < 120) {
          val ang = rng.nextFloat() * 2f * PI.toFloat()
          val spd = 1.2f + rng.nextFloat() * 3.2f
          particles.add(
            Particle3D(
              x = x, y = y, z = z,
              vx = cos(ang) * spd,
              vy = 1.2f + rng.nextFloat() * 2.8f,
              vz = sin(ang) * spd,
              life = 0f,
              maxLife = if (kind == ParticleKind.SPARK) 0.55f else 1.25f,
              size = if (kind == ParticleKind.SPARK) 0.035f else 0.055f,
              kind = kind
            )
          )
        }
      }
    }
  }

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    GLES30.glClearColor(0.01f, 0.015f, 0.015f, 1.0f)
    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    GLES30.glEnable(GLES30.GL_CULL_FACE)
    GLES30.glCullFace(GLES30.GL_BACK)

    program = buildShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    texArrayId = ProceduralTextures.createTextureArray()
    mapTexId = createMapOccupancyTexture()

    staticMesh = HospitalMeshes.buildStaticHospital()
    doorMesh = HospitalMeshes.buildDoorPanel()
    lockerBodyMesh = HospitalMeshes.buildLockerBody()
    lockerDoorMesh = HospitalMeshes.buildLockerDoor()
    fuseMesh = HospitalMeshes.buildFuseMesh()
    bottleMesh = HospitalMeshes.buildBottleMesh()
    noteMesh = HospitalMeshes.buildNoteMesh()
    hazardMesh = HospitalMeshes.buildHazardMesh()
    unitBoxGlow = HospitalMeshes.buildUnitBox(ProceduralTextures.LAYER_ACCENT, 1f)
    unitBoxGlass = HospitalMeshes.buildUnitBox(ProceduralTextures.LAYER_ACCENT, 3f)
    nurseTorsoMesh = HospitalMeshes.buildNurseTorso()
    nurseHeadMesh = HospitalMeshes.buildNurseHead()
    nurseLegMesh = HospitalMeshes.buildNurseLimb(0.58f, 0.065f)
    nurseArmMesh = HospitalMeshes.buildNurseLimb(0.54f, 0.048f, withLantern = false)
    nurseLanternArmMesh = HospitalMeshes.buildNurseLimb(0.54f, 0.048f, withLantern = true)

    uMvpLoc = GLES30.glGetUniformLocation(program, "uMvp")
    uModelLoc = GLES30.glGetUniformLocation(program, "uModel")
    uTexArrayLoc = GLES30.glGetUniformLocation(program, "uTexArray")
    uMapTexLoc = GLES30.glGetUniformLocation(program, "uMapTex")
    uCamPosLoc = GLES30.glGetUniformLocation(program, "uCamPos")
    uTorchPosLoc = GLES30.glGetUniformLocation(program, "uTorchPos")
    uTorchDirLoc = GLES30.glGetUniformLocation(program, "uTorchDir")
    uTorchPowerLoc = GLES30.glGetUniformLocation(program, "uTorchPower")
    uTorchConeLoc = GLES30.glGetUniformLocation(program, "uTorchCone")
    uNurseLanternLoc = GLES30.glGetUniformLocation(program, "uNurseLanternPos")
    uBreakerPosLoc = GLES30.glGetUniformLocation(program, "uBreakerPos")
    uExitPosLoc = GLES30.glGetUniformLocation(program, "uExitPos")
    uPowerRestoredLoc = GLES30.glGetUniformLocation(program, "uPowerRestored")
    uBrightnessLoc = GLES30.glGetUniformLocation(program, "uBrightness")
    uRedVignetteLoc = GLES30.glGetUniformLocation(program, "uRedVignette")
    uTimeLoc = GLES30.glGetUniformLocation(program, "uTime")
    uOverrideTintLoc = GLES30.glGetUniformLocation(program, "uOverrideTint")
    uResolutionLoc = GLES30.glGetUniformLocation(program, "uResolution")

    lastNanoTime = System.nanoTime()
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    GLES30.glViewport(0, 0, width, height)
    viewWidth = width.coerceAtLeast(1).toFloat()
    viewHeight = height.coerceAtLeast(1).toFloat()
    val aspect = viewWidth / viewHeight
    Matrix.perspectiveM(projMatrix, 0, 68f, aspect, 0.08f, 55f)
  }

  override fun onDrawFrame(gl: GL10?) {
    val now = System.nanoTime()
    val dt = ((now - lastNanoTime) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
    lastNanoTime = now

    onFrameTick(dt)

    val sim = simProvider()
    val settings = settingsProvider()

    updateParticles(dt, sim)
    updateMapOccupancyTexture(sim)

    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    GLES30.glUseProgram(program)

    // Camera with realistic head-bob + screen shake
    val shakeScale = if (settings.reducedScares) 0.25f else 1.0f
    val sx = (rng.nextFloat() - 0.5f) * sim.screenShake * 0.14f * shakeScale
    val sy = (rng.nextFloat() - 0.5f) * sim.screenShake * 0.14f * shakeScale
    val bobY = sin(sim.headBobPhase * 2f) * 0.032f * sim.headBobAmount * shakeScale

    val camX = sim.playerX + sx
    val camY = sim.eyeHeight + bobY + sy
    val camZ = sim.playerZ

    val dirX = sin(sim.playerYaw) * cos(sim.playerPitch)
    val dirY = -sin(sim.playerPitch)
    val dirZ = -cos(sim.playerYaw) * cos(sim.playerPitch)

    Matrix.setLookAtM(
      viewMatrix, 0,
      camX, camY, camZ,
      camX + dirX, camY + dirY, camZ + dirZ,
      0f, 1f, 0f
    )
    Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

    // Bind textures
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, texArrayId)
    GLES30.glUniform1i(uTexArrayLoc, 0)

    GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mapTexId)
    GLES30.glUniform1i(uMapTexLoc, 1)

    // Torch & global lighting uniforms
    GLES30.glUniform3f(uCamPosLoc, camX, camY, camZ)
    // Slight offset to right hand for realistic shadow parallax
    val handOffX = cos(sim.playerYaw) * 0.14f
    val handOffZ = sin(sim.playerYaw) * 0.14f
    GLES30.glUniform3f(uTorchPosLoc, camX + handOffX, camY - 0.14f, camZ + handOffZ)
    GLES30.glUniform3f(uTorchDirLoc, dirX, dirY, dirZ)

    val flicker = if (settings.reducedScares) max(0.75f, sim.torchFlicker) else sim.torchFlicker
    val effectiveTorchPower = sim.torchMode.power * flicker
    GLES30.glUniform1f(uTorchPowerLoc, effectiveTorchPower)

    val innerCos = if (sim.torchMode.power > 0.7f) 0.93f else 0.96f
    val outerCos = if (sim.torchMode.power > 0.7f) 0.74f else 0.84f
    GLES30.glUniform2f(uTorchConeLoc, innerCos, outerCos)

    GLES30.glUniform3f(uNurseLanternLoc, sim.nurseX, 1.05f, sim.nurseZ)
    GLES30.glUniform3f(uBreakerPosLoc, HospitalMap.BREAKER_X, 1.4f, HospitalMap.BREAKER_Z)
    GLES30.glUniform3f(uExitPosLoc, HospitalMap.EXIT_X, 2.4f, HospitalMap.EXIT_Z)
    GLES30.glUniform1f(uPowerRestoredLoc, if (sim.powerRestored) 1f else 0f)
    GLES30.glUniform1f(uBrightnessLoc, settings.brightness)
    GLES30.glUniform1f(uRedVignetteLoc, sim.getRedVignette() * (if (settings.reducedScares) 0.45f else 1f))
    GLES30.glUniform1f(uTimeLoc, sim.elapsedSeconds)
    GLES30.glUniform4f(uOverrideTintLoc, 1f, 1f, 1f, 0f)
    GLES30.glUniform2f(uResolutionLoc, viewWidth, viewHeight)

    // 1. Static hospital architecture
    Matrix.setIdentityM(modelMatrix, 0)
    drawMesh(staticMesh)

    // 2. Doors
    for (door in sim.doors) {
      val x0 = door.col * HospitalMap.CELL_SIZE + 0.03f
      val z0 = door.row * HospitalMap.CELL_SIZE + 0.03f
      val cx = (door.col + 0.5f) * HospitalMap.CELL_SIZE
      val cz = (door.row + 0.5f) * HospitalMap.CELL_SIZE
      Matrix.setIdentityM(modelMatrix, 0)
      if (door.horizontalAxis) {
        Matrix.translateM(modelMatrix, 0, x0, 0f, cz)
      } else {
        Matrix.translateM(modelMatrix, 0, cx, 0f, z0)
        Matrix.rotateM(modelMatrix, 0, -90f, 0f, 1f, 0f)
      }
      Matrix.rotateM(modelMatrix, 0, door.openAmount * 92f, 0f, 1f, 0f)
      drawMesh(doorMesh)
    }

    // 3. Lockers
    for (locker in sim.lockers) {
      val yawDeg = Math.toDegrees(locker.facingYaw.toDouble()).toFloat()
      // Locker body faces -yawDeg (model front is +Z, yaw=0 is -Z, so rotate 180 - yawDeg)
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, locker.x, 0f, locker.z)
      Matrix.rotateM(modelMatrix, 0, 180f - yawDeg, 0f, 1f, 0f)
      drawMesh(lockerBodyMesh)

      // Slatted door pivoted at left edge (-0.45, 0, +0.42)
      Matrix.translateM(modelMatrix, 0, -0.45f, 0f, 0.42f)
      Matrix.rotateM(modelMatrix, 0, -Math.toDegrees(locker.doorOpenAngle.toDouble()).toFloat(), 0f, 1f, 0f)
      drawMesh(lockerDoorMesh)
    }

    // 4. Central Breaker 4 Fuse Indicator LEDs
    val installedCount = sim.fuses.count { it.installed }
    for (i in 0 until 4) {
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(
        modelMatrix, 0,
        HospitalMap.BREAKER_X - 0.04f,
        1.15f + (i / 2) * 0.35f,
        HospitalMap.BREAKER_Z - 0.22f + (i % 2) * 0.44f
      )
      Matrix.scaleM(modelMatrix, 0, 0.06f, 0.14f, 0.08f)
      val isOn = i < installedCount
      GLES30.glUniform4f(
        uOverrideTintLoc,
        if (isOn) 0.25f else 0.95f,
        if (isOn) 1.0f else 0.18f,
        if (isOn) 0.45f else 0.18f,
        1f
      )
      drawMesh(unitBoxGlow)
    }
    GLES30.glUniform4f(uOverrideTintLoc, 1f, 1f, 1f, 0f)

    // 5. Collectible Fuses
    for (fuse in sim.fuses) {
      if (fuse.collected) continue
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, fuse.x, 0.72f + sin(sim.elapsedSeconds * 2.5f + fuse.id) * 0.04f, fuse.z)
      Matrix.rotateM(modelMatrix, 0, sim.elapsedSeconds * 45f, 0f, 1f, 0f)
      GLES30.glUniform4f(uOverrideTintLoc, 1.0f, 0.72f, 0.25f, 0.85f)
      drawMesh(fuseMesh)
    }
    GLES30.glUniform4f(uOverrideTintLoc, 1f, 1f, 1f, 0f)

    // 6. Collectible & Thrown Bottles
    for (b in sim.bottles) {
      if (b.collected) continue
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, b.x, 0.02f, b.z)
      GLES30.glUniform4f(uOverrideTintLoc, 0.35f, 0.82f, 0.55f, 0.75f)
      drawMesh(bottleMesh)
    }
    for (ab in sim.activeBottles) {
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, ab.x, ab.y, ab.z)
      Matrix.rotateM(modelMatrix, 0, ab.spin * 60f, 1f, 0.4f, 0.2f)
      GLES30.glUniform4f(uOverrideTintLoc, 0.45f, 0.95f, 0.65f, 0.85f)
      drawMesh(bottleMesh)
    }
    GLES30.glUniform4f(uOverrideTintLoc, 1f, 1f, 1f, 0f)

    // 7. Lore Notes
    for (n in sim.notes) {
      Matrix.setIdentityM(modelMatrix, 0)
      val ny = if (n.id == 0 || n.id == 7) 0.86f else 0.02f
      Matrix.translateM(modelMatrix, 0, n.x, ny, n.z)
      Matrix.rotateM(modelMatrix, 0, (n.id * 37f) % 360f, 0f, 1f, 0f)
      GLES30.glUniform4f(
        uOverrideTintLoc,
        if (n.read) 0.65f else 0.95f,
        if (n.isSecretLedger) 0.78f else 0.90f,
        if (n.isSecretLedger) 0.50f else 0.80f,
        0.7f
      )
      drawMesh(noteMesh)
    }
    GLES30.glUniform4f(uOverrideTintLoc, 1f, 1f, 1f, 0f)

    // 8. Floor Hazards (Trays / Cans)
    for (h in sim.hazards) {
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, h.x, 0.01f, h.z)
      Matrix.rotateM(modelMatrix, 0, h.id * 51f, 0f, 1f, 0f)
      drawMesh(hazardMesh)
    }

    // 9. Articulated 3D Nurse ("Matron Lim")
    drawNurse(sim)

    // 10. Dust Motes & Glass/Spark Particles
    drawParticles()
  }

  private fun drawNurse(sim: GameSimulation) {
    val yawDeg = -Math.toDegrees(sim.nurseYaw.toDouble()).toFloat()
    val walkAmp = if (sim.nurseSpeed > 0.1f) 28f else 4f
    val legSwing = sin(sim.nurseWalkPhase) * walkAmp
    val bob = abs(sin(sim.nurseWalkPhase)) * (if (sim.nurseSpeed > 0.1f) 0.045f else 0.008f)

    // Root transform
    val base = FloatArray(16)
    Matrix.setIdentityM(base, 0)
    Matrix.translateM(base, 0, sim.nurseX, bob, sim.nurseZ)
    Matrix.rotateM(base, 0, yawDeg, 0f, 1f, 0f)

    // Torso & skirt
    System.arraycopy(base, 0, modelMatrix, 0, 16)
    Matrix.rotateM(modelMatrix, 0, sim.nurseLunge * 14f, -1f, 0f, 0f)
    drawMesh(nurseTorsoMesh)

    // Tilted twitching Head + glowing eyes
    System.arraycopy(base, 0, modelMatrix, 0, 16)
    Matrix.translateM(modelMatrix, 0, 0f, 1.28f, -sim.nurseLunge * 0.08f)
    Matrix.rotateM(modelMatrix, 0, Math.toDegrees(sim.nurseHeadTilt.toDouble()).toFloat(), 0f, 0f, 1f)
    drawMesh(nurseHeadMesh)

    // Left & Right Legs
    System.arraycopy(base, 0, modelMatrix, 0, 16)
    Matrix.translateM(modelMatrix, 0, -0.10f, 0.58f, 0f)
    Matrix.rotateM(modelMatrix, 0, legSwing, 1f, 0f, 0f)
    drawMesh(nurseLegMesh)

    System.arraycopy(base, 0, modelMatrix, 0, 16)
    Matrix.translateM(modelMatrix, 0, 0.10f, 0.58f, 0f)
    Matrix.rotateM(modelMatrix, 0, -legSwing, 1f, 0f, 0f)
    drawMesh(nurseLegMesh)

    // Left Arm (carries dangling lantern)
    System.arraycopy(base, 0, modelMatrix, 0, 16)
    Matrix.translateM(modelMatrix, 0, -0.26f, 1.18f, 0f)
    Matrix.rotateM(modelMatrix, 0, -legSwing * 0.5f + 22f, 1f, 0f, 0f)
    drawMesh(nurseLanternArmMesh)

    // Right Arm (reaches forward during Hunt/Chase!)
    System.arraycopy(base, 0, modelMatrix, 0, 16)
    Matrix.translateM(modelMatrix, 0, 0.26f, 1.18f, 0f)
    val reach = if (sim.nurseAlertState == NurseAlertState.HUNT || sim.nurseAlertState == NurseAlertState.FINAL_CHASE) {
      78f + sin(sim.elapsedSeconds * 9f) * 10f
    } else {
      legSwing * 0.6f + 10f
    }
    Matrix.rotateM(modelMatrix, 0, reach, 1f, 0f, 0f)
    drawMesh(nurseArmMesh)
  }

  private fun updateParticles(dt: Float, sim: GameSimulation) {
    // Keep 36 subtle atmospheric dust motes drifting near player
    while (dustMotes.size < 36) {
      dustMotes.add(
        Particle3D(
          x = sim.playerX + (rng.nextFloat() - 0.5f) * 10f,
          y = 0.4f + rng.nextFloat() * 2.4f,
          z = sim.playerZ + (rng.nextFloat() - 0.5f) * 10f,
          vx = (rng.nextFloat() - 0.5f) * 0.14f,
          vy = 0.04f + rng.nextFloat() * 0.08f,
          vz = (rng.nextFloat() - 0.5f) * 0.14f,
          life = rng.nextFloat() * 4f,
          maxLife = 4f,
          size = 0.018f,
          kind = ParticleKind.DUST_BURST
        )
      )
    }
    for (m in dustMotes) {
      m.x += m.vx * dt
      m.y += m.vy * dt
      m.z += m.vz * dt
      m.life += dt
      if (m.life > m.maxLife || hypot(m.x - sim.playerX, m.z - sim.playerZ) > 9f) {
        m.x = sim.playerX + (rng.nextFloat() - 0.5f) * 8f
        m.y = 0.3f + rng.nextFloat() * 2.4f
        m.z = sim.playerZ + (rng.nextFloat() - 0.5f) * 8f
        m.life = 0f
      }
    }

    synchronized(particles) {
      val iter = particles.iterator()
      while (iter.hasNext()) {
        val p = iter.next()
        p.life += dt
        if (p.life >= p.maxLife) {
          iter.remove()
          continue
        }
        p.vy -= 9.5f * dt
        p.x += p.vx * dt
        p.y = (p.y + p.vy * dt).coerceAtLeast(0.03f)
        p.z += p.vz * dt
      }
    }
  }

  private fun drawParticles() {
    for (m in dustMotes) {
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, m.x, m.y, m.z)
      Matrix.scaleM(modelMatrix, 0, m.size, m.size, m.size)
      GLES30.glUniform4f(uOverrideTintLoc, 0.72f, 0.76f, 0.72f, 0.35f)
      drawMesh(unitBoxGlass)
    }
    synchronized(particles) {
      for (p in particles) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, p.x, p.y, p.z)
        Matrix.rotateM(modelMatrix, 0, p.life * 320f, 1f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, p.size, p.size, p.size)
        if (p.kind == ParticleKind.SPARK) {
          GLES30.glUniform4f(uOverrideTintLoc, 1.0f, 0.78f, 0.25f, 1f)
          drawMesh(unitBoxGlow)
        } else {
          GLES30.glUniform4f(uOverrideTintLoc, 0.55f, 0.95f, 0.70f, 0.9f)
          drawMesh(unitBoxGlass)
        }
      }
    }
    GLES30.glUniform4f(uOverrideTintLoc, 1f, 1f, 1f, 0f)
  }

  private fun drawMesh(mesh: GlMesh) {
    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
    GLES30.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
    GLES30.glUniformMatrix4fv(uModelLoc, 1, false, modelMatrix, 0)
    mesh.draw()
  }

  private fun createMapOccupancyTexture(): Int {
    val tex = IntArray(1)
    GLES30.glGenTextures(1, tex, 0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    return tex[0]
  }

  private fun updateMapOccupancyTexture(sim: GameSimulation) {
    mapBytes.clear()
    for (r in 0 until HospitalMap.ROWS) {
      for (c in 0 until HospitalMap.COLS) {
        val ch = HospitalMap.charAt(c, r)
        val opaque = when (ch) {
          '#', 'E' -> true
          'D' -> {
            val d = sim.doors.find { it.col == c && it.row == r }
            d != null && d.openAmount < 0.45f
          }
          else -> false
        }
        mapBytes.put(if (opaque) 255.toByte() else 0.toByte())
      }
    }
    mapBytes.position(0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mapTexId)
    GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
    GLES30.glTexImage2D(
      GLES30.GL_TEXTURE_2D,
      0,
      GLES30.GL_R8,
      HospitalMap.COLS,
      HospitalMap.ROWS,
      0,
      GLES30.GL_RED,
      GLES30.GL_UNSIGNED_BYTE,
      mapBytes
    )
  }

  private fun buildShaderProgram(vsSource: String, fsSource: String): Int {
    val vs = compileShader(GLES30.GL_VERTEX_SHADER, vsSource)
    val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fsSource)
    val prog = GLES30.glCreateProgram()
    GLES30.glAttachShader(prog, vs)
    GLES30.glAttachShader(prog, fs)
    GLES30.glLinkProgram(prog)
    return prog
  }

  private fun compileShader(type: Int, src: String): Int {
    val shader = GLES30.glCreateShader(type)
    GLES30.glShaderSource(shader, src)
    GLES30.glCompileShader(shader)
    return shader
  }

  companion object {
    private const val VERTEX_SHADER = """#version 300 es
      layout(location = 0) in vec3 aPos;
      layout(location = 1) in vec3 aNormal;
      layout(location = 2) in vec2 aUv;
      layout(location = 3) in vec2 aLayerTint;

      uniform mat4 uMvp;
      uniform mat4 uModel;

      out vec3 vWorldPos;
      out vec3 vNormal;
      out vec2 vUv;
      out float vLayer;
      out float vTintMode;

      void main() {
        vec4 worldPos = uModel * vec4(aPos, 1.0);
        vWorldPos = worldPos.xyz;
        vNormal = normalize(mat3(uModel) * aNormal);
        vUv = aUv;
        vLayer = aLayerTint.x;
        vTintMode = aLayerTint.y;
        gl_Position = uMvp * vec4(aPos, 1.0);
      }
    """

    private const val FRAGMENT_SHADER = """#version 300 es
      precision highp float;
      precision highp sampler2DArray;

      in vec3 vWorldPos;
      in vec3 vNormal;
      in vec2 vUv;
      in float vLayer;
      in float vTintMode;

      uniform sampler2DArray uTexArray;
      uniform sampler2D uMapTex;
      uniform vec3 uCamPos;
      uniform vec3 uTorchPos;
      uniform vec3 uTorchDir;
      uniform float uTorchPower;
      uniform vec2 uTorchCone;
      uniform vec3 uNurseLanternPos;
      uniform vec3 uBreakerPos;
      uniform vec3 uExitPos;
      uniform float uPowerRestored;
      uniform float uBrightness;
      uniform float uRedVignette;
      uniform float uTime;
      uniform vec4 uOverrideTint;
      uniform vec2 uResolution;

      out vec4 fragColor;

      // Real-time 2D grid shadow raycast against hospital walls & closed doors
      float castWallShadow(vec2 startXZ, vec2 targetXZ, vec2 surfNormalXZ) {
        vec2 origin = startXZ + surfNormalXZ * 0.14;
        vec2 delta = targetXZ - origin;
        float dist = length(delta);
        if (dist < 0.35) return 1.0;
        const int STEPS = 12;
        vec2 invGrid = vec2(1.0 / (28.0 * 2.6), 1.0 / (24.0 * 2.6));
        for (int i = 1; i < STEPS; i++) {
          float t = float(i) / float(STEPS);
          vec2 p = origin + delta * t;
          vec2 uv = p * invGrid;
          if (uv.x > 0.0 && uv.x < 1.0 && uv.y > 0.0 && uv.y < 1.0) {
            if (texture(uMapTex, uv).r > 0.5) {
              return 0.05;
            }
          }
        }
        return 1.0;
      }

      void main() {
        vec4 texel = texture(uTexArray, vec3(vUv, floor(vLayer + 0.5)));
        vec3 albedo = texel.rgb;
        float gloss = texel.a;

        if (uOverrideTint.a > 0.01) {
          albedo *= uOverrideTint.rgb;
        }
        if (vTintMode > 1.5 && vTintMode < 2.5) {
          albedo *= vec3(0.18, 0.21, 0.21); // Dark nurse apron
        } else if (vTintMode > 2.5 && vTintMode < 3.5) {
          albedo *= vec3(0.45, 0.75, 0.65); // Glass tint
          gloss = 0.92;
        } else if (vTintMode > 3.5) {
          albedo *= vec3(0.92, 0.88, 0.78); // Paper note
          gloss = 0.1;
        }

        // Screen-space bump perturbation from texture luminance
        vec3 N = normalize(vNormal);
        float lum = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
        vec3 dpdx = dFdx(vWorldPos);
        vec3 dpdy = dFdy(vWorldPos);
        float dldx = dFdx(lum);
        float dldy = dFdy(lum);
        vec3 surfGrad = cross(dpdy, N) * dldx + cross(N, dpdx) * dldy;
        float det = dot(dpdx, cross(dpdy, N));
        if (abs(det) > 1e-4) {
          vec3 bump = clamp((1.15 / det) * surfGrad, vec3(-0.35), vec3(0.35));
          N = normalize(N - bump);
        }

        vec3 V = normalize(uCamPos - vWorldPos);
        float camDist = length(uCamPos - vWorldPos);

        // Ambient dread floor (scaled gently by accessibility brightness)
        vec3 lighting = vec3(0.022, 0.028, 0.028) * (0.75 + uBrightness * 0.45);

        // 1. Dynamic Player Flashlight Spot + Real-Time Wall Shadows
        if (uTorchPower > 0.01) {
          vec3 toLight = uTorchPos - vWorldPos;
          float d = length(toLight);
          vec3 L = toLight / max(d, 0.001);
          float spotCos = dot(-L, normalize(uTorchDir));
          float cone = smoothstep(uTorchCone.y, uTorchCone.x, spotCos);
          if (cone > 0.001 && d < 24.0) {
            float shadow = castWallShadow(vWorldPos.xz, uTorchPos.xz, N.xz);
            float atten = 1.0 / (1.0 + 0.09 * d + 0.032 * d * d);
            float ndotl = max(dot(N, L), 0.0);
            vec3 H = normalize(L + V);
            float spec = pow(max(dot(N, H), 0.0), mix(10.0, 64.0, gloss)) * gloss * 0.85;
            // Warm halogen center with slight cold rim
            vec3 torchCol = mix(vec3(0.85, 0.90, 0.95), vec3(1.0, 0.91, 0.74), cone);
            lighting += torchCol * (ndotl * 1.65 + spec) * cone * atten * uTorchPower * shadow * 1.85;
          }
        }

        // 2. Nurse sickly lantern glow (subtle pale teal-amber aura)
        {
          vec3 toNurse = uNurseLanternPos - vWorldPos;
          float dn = length(toNurse);
          if (dn < 8.5) {
            vec3 Ln = toNurse / max(dn, 0.001);
            float attenN = 1.0 / (1.0 + 0.35 * dn + 0.22 * dn * dn);
            float ndotlN = max(dot(N, Ln), 0.0);
            float shN = castWallShadow(vWorldPos.xz, uNurseLanternPos.xz, N.xz);
            lighting += vec3(0.45, 0.72, 0.58) * ndotlN * attenN * shN * 0.85;
          }
        }

        // 3. Central Breaker faint indicator + South Exit Emergency Beacon
        {
          float db = length(uBreakerPos - vWorldPos);
          if (db < 6.5) {
            float att = 1.0 / (1.0 + 0.5 * db * db);
            vec3 bCol = uPowerRestored > 0.5 ? vec3(0.25, 0.95, 0.45) : vec3(0.95, 0.35, 0.18);
            lighting += bCol * att * 0.65;
          }
          float de = length(uExitPos - vWorldPos);
          if (de < 9.0) {
            float att = 1.0 / (1.0 + 0.32 * de * de);
            float pulse = 0.6 + 0.4 * sin(uTime * (uPowerRestored > 0.5 ? 7.0 : 2.5));
            vec3 eCol = uPowerRestored > 0.5 ? vec3(0.25, 1.0, 0.45) : vec3(0.95, 0.14, 0.14);
            lighting += eCol * att * pulse * 0.85;
          }
        }

        // 4. Powered-up Emergency Strobes & Corridor Lights (Final Escape Sequence!)
        if (uPowerRestored > 0.5) {
          float alarmFlicker = 0.55 + 0.45 * sin(uTime * 8.5 + vWorldPos.x * 0.4);
          lighting += vec3(0.32, 0.16, 0.10) * alarmFlicker;
        }

        vec3 color = albedo * lighting;

        // Emissive items, breaker LEDs, Nurse eyes
        if (vTintMode > 0.5 && vTintMode < 1.5) {
          color += albedo * (uOverrideTint.a > 0.01 ? 1.25 : 0.85);
        }

        // Distance exponential fog into pitch darkness
        float fogDensity = uPowerRestored > 0.5 ? 0.068 : 0.092;
        float fogFactor = exp(-camDist * camDist * fogDensity * fogDensity);
        vec3 fogColor = uPowerRestored > 0.5 ? vec3(0.03, 0.015, 0.015) : vec3(0.005, 0.008, 0.008);
        color = mix(fogColor, color, clamp(fogFactor, 0.0, 1.0));

        // Gamma / Accessibility Brightness & Subtle Vignette + Red Danger Pulse
        color = pow(max(color, vec3(0.0)), vec3(1.0 / (0.85 + uBrightness * 0.35)));
        vec2 ndc = (gl_FragCoord.xy / max(uResolution, vec2(1.0))) * 2.0 - 1.0;
        float edgeDist = length(ndc * vec2(0.75, 0.9));
        color = mix(color, vec3(0.55, 0.02, 0.02), clamp(uRedVignette * smoothstep(0.35, 1.35, edgeDist), 0.0, 0.75));

        fragColor = vec4(color, 1.0);
      }
    """
  }
}
