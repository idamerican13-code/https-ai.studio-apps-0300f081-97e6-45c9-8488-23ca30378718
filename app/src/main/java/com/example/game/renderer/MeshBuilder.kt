package com.example.game.renderer

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Interleaved vertex builder: [x, y, z, nx, ny, nz, u, v, texLayer, tintMode] (10 floats)
 */
class MeshBuilder(initialFloats: Int = 16384) {
  private var data = FloatArray(initialFloats)
  var floatCount = 0
    private set

  val vertexCount: Int get() = floatCount / STRIDE_FLOATS

  private fun ensure(extra: Int) {
    if (floatCount + extra > data.size) {
      data = data.copyOf((data.size * 2).coerceAtLeast(floatCount + extra))
    }
  }

  fun vertex(
    x: Float, y: Float, z: Float,
    nx: Float, ny: Float, nz: Float,
    u: Float, v: Float,
    layer: Float,
    tintMode: Float = 0f
  ) {
    ensure(STRIDE_FLOATS)
    var i = floatCount
    data[i++] = x; data[i++] = y; data[i++] = z
    data[i++] = nx; data[i++] = ny; data[i++] = nz
    data[i++] = u; data[i++] = v
    data[i++] = layer; data[i++] = tintMode
    floatCount = i
  }

  fun quad(
    x0: Float, y0: Float, z0: Float, u0: Float, v0: Float,
    x1: Float, y1: Float, z1: Float, u1: Float, v1: Float,
    x2: Float, y2: Float, z2: Float, u2: Float, v2: Float,
    x3: Float, y3: Float, z3: Float, u3: Float, v3: Float,
    nx: Float, ny: Float, nz: Float,
    layer: Int,
    tintMode: Float = 0f
  ) {
    val l = layer.toFloat()
    vertex(x0, y0, z0, nx, ny, nz, u0, v0, l, tintMode)
    vertex(x1, y1, z1, nx, ny, nz, u1, v1, l, tintMode)
    vertex(x2, y2, z2, nx, ny, nz, u2, v2, l, tintMode)

    vertex(x0, y0, z0, nx, ny, nz, u0, v0, l, tintMode)
    vertex(x2, y2, z2, nx, ny, nz, u2, v2, l, tintMode)
    vertex(x3, y3, z3, nx, ny, nz, u3, v3, l, tintMode)
  }

  fun box(
    cx: Float, cy: Float, cz: Float,
    hw: Float, hh: Float, hd: Float,
    layer: Int,
    uvScale: Float = 0.5f,
    tintMode: Float = 0f
  ) {
    val x0 = cx - hw; val x1 = cx + hw
    val y0 = cy - hh; val y1 = cy + hh
    val z0 = cz - hd; val z1 = cz + hd
    val uw = hw * 2f * uvScale
    val ud = hd * 2f * uvScale
    val vh = hh * 2f * uvScale

    // Front (+Z)
    quad(x0, y0, z1, 0f, vh, x1, y0, z1, uw, vh, x1, y1, z1, uw, 0f, x0, y1, z1, 0f, 0f, 0f, 0f, 1f, layer, tintMode)
    // Back (-Z)
    quad(x1, y0, z0, 0f, vh, x0, y0, z0, uw, vh, x0, y1, z0, uw, 0f, x1, y1, z0, 0f, 0f, 0f, 0f, -1f, layer, tintMode)
    // Right (+X)
    quad(x1, y0, z1, 0f, vh, x1, y0, z0, ud, vh, x1, y1, z0, ud, 0f, x1, y1, z1, 0f, 0f, 1f, 0f, 0f, layer, tintMode)
    // Left (-X)
    quad(x0, y0, z0, 0f, vh, x0, y0, z1, ud, vh, x0, y1, z1, ud, 0f, x0, y1, z0, 0f, 0f, -1f, 0f, 0f, layer, tintMode)
    // Top (+Y)
    quad(x0, y1, z1, 0f, ud, x1, y1, z1, uw, ud, x1, y1, z0, uw, 0f, x0, y1, z0, 0f, 0f, 0f, 1f, 0f, layer, tintMode)
    // Bottom (-Y)
    quad(x0, y0, z0, 0f, ud, x1, y0, z0, uw, ud, x1, y0, z1, uw, 0f, x0, y0, z1, 0f, 0f, 0f, -1f, 0f, layer, tintMode)
  }

  fun uploadVbo(): GlMesh {
    val byteBuf = ByteBuffer.allocateDirect(floatCount * 4).order(ByteOrder.nativeOrder())
    val floatBuf: FloatBuffer = byteBuf.asFloatBuffer()
    floatBuf.put(data, 0, floatCount)
    floatBuf.position(0)

    val vao = IntArray(1)
    val vbo = IntArray(1)
    GLES30.glGenVertexArrays(1, vao, 0)
    GLES30.glGenBuffers(1, vbo, 0)

    GLES30.glBindVertexArray(vao[0])
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floatCount * 4, floatBuf, GLES30.GL_STATIC_DRAW)

    val strideBytes = STRIDE_FLOATS * 4
    // 0: pos (3)
    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, strideBytes, 0)
    // 1: normal (3)
    GLES30.glEnableVertexAttribArray(1)
    GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, strideBytes, 3 * 4)
    // 2: uv (2)
    GLES30.glEnableVertexAttribArray(2)
    GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, strideBytes, 6 * 4)
    // 3: layer & tintMode (2)
    GLES30.glEnableVertexAttribArray(3)
    GLES30.glVertexAttribPointer(3, 2, GLES30.GL_FLOAT, false, strideBytes, 8 * 4)

    GLES30.glBindVertexArray(0)
    return GlMesh(vao[0], vbo[0], vertexCount)
  }

  companion object {
    const val STRIDE_FLOATS = 10
  }
}

data class GlMesh(val vao: Int, val vbo: Int, val vertexCount: Int) {
  fun draw() {
    if (vertexCount <= 0) return
    GLES30.glBindVertexArray(vao)
    GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
  }
}
