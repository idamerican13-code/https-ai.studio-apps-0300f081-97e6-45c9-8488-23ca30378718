package com.example.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.game.engine.SoundType
import com.example.game.model.NurseAlertState
import com.example.game.model.SettingsState
import kotlin.math.*
import kotlin.random.Random

/**
 * Procedural real-time 22.05kHz AudioTrack synthesizer producing:
 * - Eerie original Changi Hospital ambient score & chase pulse
 * - Dynamic proximity heartbeat
 * - Layered horror SFX (footsteps, glass shatter, doors, breath, dynamo, breaker)
 */
class HorrorAudioEngine {
  private val sampleRate = 22050
  private var running = false
  private var thread: Thread? = null

  private data class Voice(
    val type: SoundType,
    var age: Float = 0f,
    val duration: Float,
    val volume: Float,
    val pitch: Float,
    val seed: Int
  )

  private val voices = ArrayList<Voice>(24)
  private val voiceLock = Any()

  @Volatile var masterVol = 0.85f
  @Volatile var musicVol = 0.70f
  @Volatile var sfxVol = 0.90f
  @Volatile var tension = 0f // 0..1 based on nurse proximity & alert
  @Volatile var powerRestored = false
  @Volatile var isPlaying = false
  @Volatile var isHiding = false

  private var phaseDrone1 = 0.0
  private var phaseDrone2 = 0.0
  private var phaseShimmer = 0.0
  private var phasePulse = 0.0
  private var beatAccum = 0f
  private var rng = Random(42)

  fun start() {
    if (running) return
    running = true
    thread = Thread({
      val minBuf = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
      ).coerceAtLeast(2048)

      val track = try {
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_GAME)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(minBuf)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build()
      } catch (_: Throwable) {
        null
      }

      if (track == null) {
        running = false
        return@Thread
      }

      val chunkSamples = 512
      val pcm = ShortArray(chunkSamples)
      val dt = 1f / sampleRate

      try {
        track.play()
        while (running) {
          for (i in 0 until chunkSamples) {
            val s = synthesizeSample(dt)
            val scaled = (s * masterVol * 26000f).toInt().coerceIn(-32767, 32767)
            pcm[i] = scaled.toShort()
          }
          track.write(pcm, 0, chunkSamples)
        }
      } catch (_: Throwable) {
      } finally {
        try {
          track.stop()
          track.release()
        } catch (_: Throwable) {
        }
      }
    }, "WardZeroAudio").apply {
      priority = Thread.MAX_PRIORITY
      start()
    }
  }

  fun stop() {
    running = false
    thread?.interrupt()
    thread = null
  }

  fun applySettings(settings: SettingsState) {
    masterVol = settings.masterVolume
    musicVol = settings.musicVolume
    sfxVol = settings.sfxVolume
  }

  fun updateAtmosphere(
    playing: Boolean,
    nurseDist: Float,
    alertState: NurseAlertState,
    powerOn: Boolean,
    hiding: Boolean
  ) {
    isPlaying = playing
    powerRestored = powerOn
    isHiding = hiding
    val distFactor = (1f - (nurseDist / 18f).coerceIn(0f, 1f))
    val stateBoost = when (alertState) {
      NurseAlertState.PATROL -> 0f
      NurseAlertState.SUSPICIOUS -> 0.25f
      NurseAlertState.INVESTIGATE -> 0.35f
      NurseAlertState.SEARCH_LOCKER -> 0.65f
      NurseAlertState.HUNT, NurseAlertState.FINAL_CHASE -> 0.85f
    }
    tension = (distFactor * 0.65f + stateBoost * 0.55f).coerceIn(0f, 1f)
  }

  fun playSound(type: SoundType, volume: Float = 1f, pitch: Float = 1f) {
    val dur = when (type) {
      SoundType.FOOTSTEP_SOFT -> 0.12f
      SoundType.FOOTSTEP_SPRINT -> 0.16f
      SoundType.NURSE_FOOTSTEP -> 0.22f
      SoundType.NURSE_ALERT_STING -> 1.10f
      SoundType.TORCH_CLICK -> 0.06f
      SoundType.TORCH_DYNAMO -> 0.34f
      SoundType.BOTTLE_THROW -> 0.18f
      SoundType.BOTTLE_SHATTER -> 0.65f
      SoundType.DOOR_CREAK -> 0.55f
      SoundType.DOOR_SLAM -> 0.42f
      SoundType.HAZARD_CLATTER -> 0.50f
      SoundType.LOCKER_OPEN -> 0.28f
      SoundType.LOCKER_CLOSE -> 0.25f
      SoundType.BREATH_INHALE -> 0.35f
      SoundType.BREATH_EXHALE -> 0.38f
      SoundType.BREATH_GASP -> 0.45f
      SoundType.PICKUP_FUSE -> 0.32f
      SoundType.PICKUP_BOTTLE -> 0.24f
      SoundType.NOTE_RUSTLE -> 0.28f
      SoundType.BREAKER_INSTALL -> 0.52f
      SoundType.GENERATOR_POWER_ON -> 1.60f
      SoundType.CAUGHT_IMPACT -> 0.90f
    }
    synchronized(voices) {
      if (voices.size < 20) {
        voices.add(Voice(type, 0f, dur, volume, pitch, rng.nextInt()))
      }
    }
  }

  private fun synthesizeSample(dt: Float): Float {
    // 1. Eerie original horror music bed
    val baseFreq = if (powerRestored) 61.74 else 55.0 // B1 alarm / A1 dread
    phaseDrone1 += baseFreq * dt
    phaseDrone2 += (baseFreq * 1.008) * dt
    val shimmerRatio = if (tension > 0.5f) 1.4142 else 1.1892 // tritone or minor 3rd
    phaseShimmer += (baseFreq * 4.0 * shimmerRatio) * dt

    val drone = (sin(phaseDrone1 * 2.0 * PI) * 0.45 +
      sin(phaseDrone2 * 2.0 * PI) * 0.35 +
      sin(phaseDrone1 * 4.0 * PI) * 0.15).toFloat()

    val shimmerEnv = (0.15f + tension * 0.55f)
    val shimmer = sin(phaseShimmer * 2.0 * PI).toFloat() * shimmerEnv * 0.22f

    // Tension industrial pulse
    var pulse = 0f
    if (isPlaying && (tension > 0.35f || powerRestored)) {
      val pulseHz = 1.8 + tension * 2.4
      phasePulse += pulseHz * dt
      val frac = (phasePulse % 1.0).toFloat()
      val env = exp(-frac * 7.5f)
      pulse = sin(phaseDrone1 * 2.0 * PI).toFloat() * env * (0.18f + tension * 0.25f)
    }

    // Heartbeat thump when tense or hiding
    var heart = 0f
    if (isPlaying && tension > 0.22f) {
      val bpm = 64f + tension * 92f
      val interval = 60f / bpm
      beatAccum += dt
      if (beatAccum >= interval) beatAccum -= interval
      val t1 = beatAccum
      val t2 = beatAccum - 0.16f
      val lub = if (t1 < 0.11f) sin(t1 * 220f) * exp(-t1 * 26f) else 0f
      val dub = if (t2 in 0f..0.11f) sin(t2 * 185f) * exp(-t2 * 30f) * 0.72f else 0f
      heart = (lub + dub) * (0.18f + tension * 0.45f)
    }

    val musicSample = (drone * 0.18f + shimmer + pulse) * musicVol + heart * sfxVol

    // 2. Active one-shot SFX voices
    var sfxSample = 0f
    synchronized(voiceLock) {
      val iter = voices.iterator()
      while (iter.hasNext()) {
        val v = iter.next()
        v.age += dt
        if (v.age >= v.duration) {
          iter.remove()
          continue
        }
        sfxSample += renderVoice(v) * v.volume
      }
    }

    val total = musicSample + sfxSample * sfxVol * 0.55f
    return tanh(total.toDouble()).toFloat()
  }

  private fun renderVoice(v: Voice): Float {
    val t = v.age
    val norm = (t / v.duration).coerceIn(0f, 1f)
    val noise = rng.nextFloat() * 2f - 1f

    return when (v.type) {
      SoundType.FOOTSTEP_SOFT ->
        (sin(t * 280f) * 0.5f + noise * 0.35f) * exp(-t * 36f)

      SoundType.FOOTSTEP_SPRINT ->
        (sin(t * 340f) * 0.6f + noise * 0.5f) * exp(-t * 28f)

      SoundType.NURSE_FOOTSTEP ->
        // Heavy wet heel + metallic key clink
        (sin(t * 165f) * 0.65f + sin(t * 1950f) * 0.25f + noise * 0.3f) * exp(-t * 18f)

      SoundType.NURSE_ALERT_STING ->
        (sin(t * 620f) + sin(t * 658f) * 0.8f + sin(t * 930f) * 0.4f) * (1f - norm) * 0.45f

      SoundType.TORCH_CLICK ->
        (sin(t * 2400f) * 0.5f + noise * 0.5f) * exp(-t * 90f)

      SoundType.TORCH_DYNAMO ->
        sin(t * (420f + norm * 520f)) * sin(norm * PI.toFloat()) * 0.45f

      SoundType.BOTTLE_THROW ->
        noise * sin(norm * PI.toFloat()) * 0.35f

      SoundType.BOTTLE_SHATTER -> {
        val chime = sin(t * 3200f) + sin(t * 4750f) * 0.7f + sin(t * 6100f) * 0.5f
        (noise * 0.65f + chime * 0.35f) * exp(-t * 7.5f)
      }

      SoundType.DOOR_CREAK ->
        sin(t * (290f + sin(t * 18f) * 45f)) * sin(norm * PI.toFloat()) * 0.45f

      SoundType.DOOR_SLAM ->
        (sin(t * 95f) * 0.8f + noise * 0.6f) * exp(-t * 11f)

      SoundType.HAZARD_CLATTER ->
        (sin(t * 1180f) + sin(t * 1790f) * 0.7f + noise * 0.5f) * exp(-t * 9f)

      SoundType.LOCKER_OPEN, SoundType.LOCKER_CLOSE ->
        (sin(t * 540f) * 0.5f + noise * 0.45f) * exp(-t * 19f)

      SoundType.BREATH_INHALE, SoundType.BREATH_EXHALE ->
        noise * sin(norm * PI.toFloat()) * 0.28f

      SoundType.BREATH_GASP ->
        (noise * 0.6f + sin(t * 480f) * 0.35f) * exp(-t * 7f)

      SoundType.PICKUP_FUSE, SoundType.PICKUP_BOTTLE ->
        sin(t * (720f + norm * 280f)) * exp(-t * 12f) * 0.45f

      SoundType.NOTE_RUSTLE ->
        noise * sin(norm * PI.toFloat()) * 0.25f

      SoundType.BREAKER_INSTALL ->
        (sin(t * 140f) + sin(t * 880f) * 0.4f + noise * 0.3f) * exp(-t * 8f)

      SoundType.GENERATOR_POWER_ON -> {
        val rev = sin(t * (70f + norm * 110f) * 2f * PI.toFloat())
        (rev * 0.6f + noise * 0.25f) * sin(norm * PI.toFloat())
      }

      SoundType.CAUGHT_IMPACT ->
        (sin(t * 85f) + sin(t * 311f) * 0.7f + noise * 0.8f) * exp(-t * 3.8f)
    }
  }
}
