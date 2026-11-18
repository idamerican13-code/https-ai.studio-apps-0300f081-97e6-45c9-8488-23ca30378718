package com.example.game.engine

import java.util.PriorityQueue
import kotlin.math.abs

data class GridCell(val col: Int, val row: Int)

object Pathfinder {

  fun findPath(
    cols: Int,
    rows: Int,
    isWalkable: (Int, Int) -> Boolean,
    startCol: Int,
    startRow: Int,
    goalCol: Int,
    goalRow: Int
  ): List<GridCell> {
    if (startCol == goalCol && startRow == goalRow) return emptyList()
    if (!isWalkable(goalCol, goalRow)) return emptyList()

    val total = cols * rows
    val gScore = IntArray(total) { Int.MAX_VALUE }
    val cameFrom = IntArray(total) { -1 }
    val closed = BooleanArray(total)

    val startIdx = startRow * cols + startCol
    val goalIdx = goalRow * cols + goalCol
    gScore[startIdx] = 0

    val pq = PriorityQueue<Long>(compareBy { it ushr 32 })
    val h0 = abs(goalCol - startCol) + abs(goalRow - startRow)
    pq.add((h0.toLong() shl 32) or startIdx.toLong())

    val dirs = intArrayOf(1, 0, -1, 0, 0, 1, 0, -1)

    while (pq.isNotEmpty()) {
      val packed = pq.poll() ?: break
      val curr = (packed and 0xFFFFFFFFL).toInt()
      if (curr == goalIdx) {
        val result = ArrayList<GridCell>()
        var trace = curr
        while (trace != -1 && trace != startIdx) {
          result.add(GridCell(trace % cols, trace / cols))
          trace = cameFrom[trace]
        }
        result.reverse()
        return result
      }
      if (closed[curr]) continue
      closed[curr] = true

      val cx = curr % cols
      val cz = curr / cols
      val currG = gScore[curr]

      for (i in 0 until 4) {
        val nx = cx + dirs[i * 2]
        val nz = cz + dirs[i * 2 + 1]
        if (nx !in 0 until cols || nz !in 0 until rows) continue
        if (!isWalkable(nx, nz)) continue
        val nIdx = nz * cols + nx
        if (closed[nIdx]) continue

        val tentativeG = currG + 1
        if (tentativeG < gScore[nIdx]) {
          cameFrom[nIdx] = curr
          gScore[nIdx] = tentativeG
          val f = tentativeG + abs(goalCol - nx) + abs(goalRow - nz)
          pq.add((f.toLong() shl 32) or nIdx.toLong())
        }
      }
    }
    return emptyList()
  }

  /**
   * 2D grid DDA raycast from (x0, z0) to (x1, z1) in grid units.
   * Returns true if unobstructed (no wall cells hit along the segment).
   */
  fun hasLineOfSight(
    x0: Float,
    z0: Float,
    x1: Float,
    z1: Float,
    isOpaque: (Int, Int) -> Boolean
  ): Boolean {
    val dx = x1 - x0
    val dz = z1 - z0
    val dist = kotlin.math.sqrt(dx * dx + dz * dz)
    if (dist < 0.01f) return true
    val steps = (dist * 6f).toInt().coerceAtLeast(2)
    for (s in 1 until steps) {
      val t = s.toFloat() / steps
      val cx = kotlin.math.floor((x0 + dx * t).toDouble()).toInt()
      val cz = kotlin.math.floor((z0 + dz * t).toDouble()).toInt()
      if (isOpaque(cx, cz)) return false
    }
    return true
  }
}
