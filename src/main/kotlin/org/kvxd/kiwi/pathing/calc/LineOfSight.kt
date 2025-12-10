package org.kvxd.kiwi.pathing.calc

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.pathing.cache.CollisionCache
import kotlin.math.floor
import kotlin.math.max

object LineOfSight {

    //This should be .6 but .8 fixes some weird behaviour
    private const val PLAYER_WIDTH = 0.8
    private const val RADIUS = PLAYER_WIDTH / 2.0

    fun check(start: Node, end: Node): Boolean {
        if (!end.type.isSmoothable) return false

        val startVec = start.toVec()
        val endVec = end.toVec()

        val dir = endVec.subtract(startVec)
        if (dir.horizontalDistanceSqr() < 0.0001) return true

        if (!isWalkableRay(startVec, endVec, requireGround = true)) return false

        val perp = Vec3(-dir.z, 0.0, dir.x).normalize().multiply(RADIUS, RADIUS, RADIUS)

        val s1 = startVec.add(perp)
        val e1 = endVec.add(perp)
        val leftClear = isWalkableRay(s1, e1, requireGround = false)

        val s2 = startVec.subtract(perp)
        val e2 = endVec.subtract(perp)
        val rightClear = isWalkableRay(s2, e2, requireGround = false)

        return !(!leftClear || !rightClear)
    }

    private fun isWalkableRay(start: Vec3, end: Vec3, requireGround: Boolean): Boolean {
        val x1 = start.x
        val y1 = start.y
        val z1 = start.z
        val x2 = end.x
        val y2 = end.y
        val z2 = end.z

        val distSq = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2) + (z1 - z2) * (z1 - z2)
        if (distSq > 256.0) return false

        val steps = max(distSq, 10.0).toInt()

        val dx = (x2 - x1) / steps
        val dy = (y2 - y1) / steps
        val dz = (z2 - z1) / steps

        var cx = x1
        var cy = y1
        var cz = z1

        val mutablePos = BlockPos.MutableBlockPos()

        for (i in 0..steps) {
            mutablePos.set(floor(cx).toInt(), floor(cy).toInt(), floor(cz).toInt())

            if (!CollisionCache.isPassable(mutablePos)) return false

            mutablePos.setY(floor(cy).toInt() + 1)
            if (!CollisionCache.isPassable(mutablePos)) return false

            if (requireGround) {
                mutablePos.setY(floor(cy).toInt() - 1)
                if (!CollisionCache.isSolid(mutablePos)) {
                    mutablePos.setY(floor(cy).toInt() - 2)
                    if (!CollisionCache.isSolid(mutablePos)) return false
                }
            }

            mutablePos.setY(floor(cy).toInt())
            if (CollisionCache.isDangerous(mutablePos)) return false
            mutablePos.setY(floor(cy).toInt() - 1)
            if (CollisionCache.isDangerous(mutablePos)) return false

            cx += dx
            cy += dy
            cz += dz
        }

        return true
    }
}