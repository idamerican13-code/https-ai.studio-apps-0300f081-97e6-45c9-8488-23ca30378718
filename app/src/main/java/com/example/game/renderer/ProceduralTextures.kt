package com.example.game.renderer

import android.opengl.GLES30
import java.nio.ByteBuffer
import kotlin.math.*

/**
 * Generates high-detail 256x256 procedural RGBA textures into a GL_TEXTURE_2D_ARRAY (7 layers).
 * RGB = Albedo with grime, cracks, wear, and hospital patina.
 * A   = Specular/Glossiness mask (wet tiles, polished steel, matte plaster).
 */
object ProceduralTextures {
  const val TEX_SIZE = 256
  const val LAYER_WALL_PLASTER = 0
  const val LAYER_FLOOR_TILE = 1
  const val LAYER_CEILING = 2
  const val LAYER_RUST_METAL = 3
  const val LAYER_WOOD_FABRIC = 4
  const val LAYER_NURSE = 5
  const val LAYER_ACCENT = 6
  const val LAYER_COUNT = 7

  fun createTextureArray(): Int {
    val texIds = IntArray(1)
    GLES30.glGenTextures(1, texIds, 0)
    val texId = texIds[0]
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, texId)

    GLES30.glTexImage3D(
      GLES30.GL_TEXTURE_2D_ARRAY,
      0,
      GLES30.GL_RGBA8,
      TEX_SIZE,
      TEX_SIZE,
      LAYER_COUNT,
      0,
      GLES30.GL_RGBA,
      GLES30.GL_UNSIGNED_BYTE,
      null
    )

    val buf = ByteBuffer.allocateDirect(TEX_SIZE * TEX_SIZE * 4)
    for (layer in 0 until LAYER_COUNT) {
      buf.clear()
      fillLayer(layer, buf)
      buf.position(0)
      GLES30.glTexSubImage3D(
        GLES30.GL_TEXTURE_2D_ARRAY,
        0,
        0,
        0,
        layer,
        TEX_SIZE,
        TEX_SIZE,
        1,
        GLES30.GL_RGBA,
        GLES30.GL_UNSIGNED_BYTE,
        buf
      )
    }

    GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D_ARRAY)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
    return texId
  }

  private fun fillLayer(layer: Int, buf: ByteBuffer) {
    val s = TEX_SIZE
    for (y in 0 until s) {
      val v = y.toFloat() / s
      for (x in 0 until s) {
        val u = x.toFloat() / s
        val n1 = fbm(u * 8f, v * 8f, layer * 17 + 3)
        val n2 = fbm(u * 24f, v * 24f, layer * 31 + 11)
        val streak = fbm(u * 18f, v * 2.5f, layer * 7 + 5)

        var r: Float
        var g: Float
        var b: Float
        var gloss: Float

        when (layer) {
          LAYER_WALL_PLASTER -> {
            // Upper peeling Changi seafoam plaster, lower grime tile dado (v > 0.62)
            val isDado = v > 0.62f
            if (!isDado) {
              val peel = smoothstep(0.55f, 0.72f, n1 + n2 * 0.25f)
              val damp = smoothstep(0.45f, 0.85f, streak) * 0.35f
              r = lerp(0.32f, 0.20f, peel) - damp * 0.14f + n2 * 0.06f
              g = lerp(0.43f, 0.25f, peel) - damp * 0.15f + n2 * 0.07f
              b = lerp(0.39f, 0.22f, peel) - damp * 0.12f + n2 * 0.06f
              gloss = 0.18f + damp * 0.35f
            } else {
              // Lower ceramic hospital dado tiles with dark grout
              val tx = (u * 8f) % 1f
              val ty = (v * 8f) % 1f
              val grout = (tx < 0.05f || ty < 0.05f)
              val grime = smoothstep(0.35f, 0.80f, streak) * 0.3f
              if (grout) {
                r = 0.09f; g = 0.10f; b = 0.09f; gloss = 0.08f
              } else {
                r = 0.29f - grime * 0.15f + n2 * 0.05f
                g = 0.34f - grime * 0.14f + n2 * 0.05f
                b = 0.32f - grime * 0.13f + n2 * 0.05f
                gloss = 0.58f - grime * 0.25f
              }
            }
            // Chair-rail border line
            if (abs(v - 0.62f) < 0.018f) {
              r *= 0.45f; g *= 0.45f; b *= 0.45f; gloss = 0.35f
            }
          }

          LAYER_FLOOR_TILE -> {
            // Cracked hospital terrazzo / checkerboard linoleum with wet damp patches
            val cellX = floor(u * 4f).toInt()
            val cellY = floor(v * 4f).toInt()
            val checker = ((cellX + cellY) and 1) == 0
            val fx = (u * 4f) % 1f
            val fy = (v * 4f) % 1f
            val grout = fx < 0.04f || fy < 0.04f
            val puddle = smoothstep(0.56f, 0.72f, n1)
            val crack = if (abs(n2 - 0.5f) < 0.018f) 0.55f else 1.0f

            if (grout) {
              r = 0.07f; g = 0.08f; b = 0.08f; gloss = 0.12f
            } else {
              val base = if (checker) 0.27f else 0.20f
              r = (base + n2 * 0.07f) * crack * (1f - puddle * 0.25f)
              g = (base + 0.02f + n2 * 0.07f) * crack * (1f - puddle * 0.20f)
              b = (base + 0.01f + n2 * 0.06f) * crack * (1f - puddle * 0.15f)
              gloss = 0.28f + puddle * 0.65f
            }
          }

          LAYER_CEILING -> {
            // Water-stained acoustic ceiling grid
            val fx = (u * 2f) % 1f
            val fy = (v * 2f) % 1f
            val seam = fx < 0.03f || fy < 0.03f
            val stain = smoothstep(0.42f, 0.78f, n1)
            r = if (seam) 0.10f else 0.26f - stain * 0.12f + n2 * 0.05f
            g = if (seam) 0.11f else 0.27f - stain * 0.13f + n2 * 0.05f
            b = if (seam) 0.10f else 0.24f - stain * 0.12f + n2 * 0.04f
            gloss = 0.12f
          }

          LAYER_RUST_METAL -> {
            // Rusted brushed hospital locker / shutter / door steel
            val brush = fbm(u * 4f, v * 36f, 99)
            val rust = smoothstep(0.52f, 0.78f, n1 + streak * 0.2f)
            r = lerp(0.24f + brush * 0.06f, 0.36f, rust)
            g = lerp(0.28f + brush * 0.06f, 0.19f, rust)
            b = lerp(0.27f + brush * 0.05f, 0.12f, rust)
            gloss = lerp(0.68f, 0.18f, rust)
          }

          LAYER_WOOD_FABRIC -> {
            // Aged bed mattress / wood desk / stained medical linen
            val weave = sin(u * 120f) * 0.02f + sin(v * 120f) * 0.02f
            val stain = smoothstep(0.60f, 0.85f, n1)
            r = 0.42f - stain * 0.18f + weave + n2 * 0.05f
            g = 0.39f - stain * 0.22f + weave + n2 * 0.04f
            b = 0.34f - stain * 0.21f + weave + n2 * 0.04f
            gloss = 0.16f
          }

          LAYER_NURSE -> {
            // Stained pale vintage nurse coat & ashen skin/apron contrast
            val grime = smoothstep(0.45f, 0.82f, streak + n2 * 0.3f)
            r = 0.62f - grime * 0.28f + n2 * 0.05f
            g = 0.63f - grime * 0.32f + n2 * 0.05f
            b = 0.60f - grime * 0.31f + n2 * 0.05f
            gloss = 0.22f
          }

          else -> {
            // Brass / Glass / Fuse / Paper accent
            r = 0.78f + n2 * 0.10f
            g = 0.66f + n2 * 0.10f
            b = 0.46f + n2 * 0.08f
            gloss = 0.85f
          }
        }

        buf.put((r.coerceIn(0f, 1f) * 255f).toInt().toByte())
        buf.put((g.coerceIn(0f, 1f) * 255f).toInt().toByte())
        buf.put((b.coerceIn(0f, 1f) * 255f).toInt().toByte())
        buf.put((gloss.coerceIn(0f, 1f) * 255f).toInt().toByte())
      }
    }
  }

  private fun hash2(ix: Int, iy: Int, seed: Int): Float {
    var h = ix * 374761393 + iy * 668265263 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return ((h and 0x7FFFFFFF) % 10000) / 10000f
  }

  private fun valueNoise(x: Float, y: Float, seed: Int): Float {
    val ix = floor(x).toInt()
    val iy = floor(y).toInt()
    val fx = x - ix
    val fy = y - iy
    val ux = fx * fx * (3f - 2f * fx)
    val uy = fy * fy * (3f - 2f * fy)
    val a = hash2(ix, iy, seed)
    val b = hash2(ix + 1, iy, seed)
    val c = hash2(ix, iy + 1, seed)
    val d = hash2(ix + 1, iy + 1, seed)
    return lerp(lerp(a, b, ux), lerp(c, d, ux), uy)
  }

  private fun fbm(x: Float, y: Float, seed: Int): Float {
    var sum = 0f
    var amp = 0.5f
    var freq = 1f
    for (i in 0 until 4) {
      sum += valueNoise(x * freq, y * freq, seed + i * 19) * amp
      freq *= 2.02f
      amp *= 0.5f
    }
    return sum
  }

  private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
  private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
  }
}
