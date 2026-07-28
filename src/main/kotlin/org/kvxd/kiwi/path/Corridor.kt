package org.kvxd.kiwi.path

import net.minecraft.world.phys.AABB
import org.kvxd.kiwi.world.BlockProfile
import org.kvxd.kiwi.world.PlayerBox
import org.kvxd.kiwi.world.ShapeKind
import org.kvxd.kiwi.world.WorldView
import org.kvxd.kiwi.world.collides
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

object Corridor {

    private const val MAX_LENGTH = 20.0
    private const val SAMPLE_STEP = 0.25

    fun isWalkable(view: WorldView, from: PathNode, to: PathNode): Boolean {
        if (from.feetY != to.feetY) return false
        return isWalkable(view, from.x, from.z, to.x, to.z, from.feetY)
    }

    fun isWalkable(view: WorldView, fromX: Int, fromZ: Int, toX: Int, toZ: Int, feetY: Double): Boolean {
        val x0 = fromX + 0.5
        val z0 = fromZ + 0.5
        val dx = (toX - fromX).toDouble()
        val dz = (toZ - fromZ).toDouble()
        val length = sqrt(dx * dx + dz * dz)
        if (length > MAX_LENGTH) return false
        if (length < 1.0E-6) return true

        val bodyTop = feetY + PlayerBox.HEIGHT
        val steps = ceil(length / SAMPLE_STEP).toInt()

        var lastMinX = Int.MIN_VALUE
        var lastMinZ = Int.MIN_VALUE
        var lastMaxX = Int.MIN_VALUE
        var lastMaxZ = Int.MIN_VALUE

        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val px = x0 + dx * t
            val pz = z0 + dz * t

            val minX = floor(px - PlayerBox.HALF_WIDTH + BlockProfile.EPS).toInt()
            val maxX = floor(px + PlayerBox.HALF_WIDTH - BlockProfile.EPS).toInt()
            val minZ = floor(pz - PlayerBox.HALF_WIDTH + BlockProfile.EPS).toInt()
            val maxZ = floor(pz + PlayerBox.HALF_WIDTH - BlockProfile.EPS).toInt()

            if (minX == lastMinX && maxX == lastMaxX && minZ == lastMinZ && maxZ == lastMaxZ) continue
            lastMinX = minX
            lastMaxX = maxX
            lastMinZ = minZ
            lastMaxZ = maxZ

            for (cx in minX..maxX) {
                for (cz in minZ..maxZ) {
                    if (!columnClear(view, cx, cz, feetY, bodyTop)) return false
                    if (!columnSupported(view, cx, cz, feetY)) return false
                }
            }
        }
        return true
    }

    private fun columnClear(view: WorldView, cx: Int, cz: Int, low: Double, high: Double): Boolean {
        var cellY = floor(low).toInt() - 1
        val last = floor(high - BlockProfile.EPS).toInt()
        while (cellY <= last) {
            val profile = view.profile(cx, cellY, cz)
            if (!profile.known) return false
            when (profile.shapeKind) {
                ShapeKind.EMPTY -> Unit
                ShapeKind.FULL_CUBE ->
                    if (cellY + 1.0 > low + BlockProfile.EPS && cellY < high - BlockProfile.EPS) return false

                ShapeKind.PARTIAL -> {
                    val bounds = runCatching { profile.shape.bounds() }.getOrNull() ?: return false
                    if (cellY + bounds.maxY > low + BlockProfile.EPS &&
                        cellY + bounds.minY < high - BlockProfile.EPS
                    ) return false
                }
            }
            cellY++
        }
        return true
    }

    private fun columnSupported(view: WorldView, cx: Int, cz: Int, feetY: Double): Boolean {
        val probe = AABB(
            cx + 0.02,
            feetY - 0.14,
            cz + 0.02,
            cx + 0.98,
            feetY - 0.02,
            cz + 0.98
        )
        return view.collides(probe)
    }
}
