package org.kvxd.kiwi.pathing.calc

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.kvxd.kiwi.pathing.cache.CollisionCache
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

object LineOfSight {

    private const val PLAYER_WIDTH = 0.8
    private const val RADIUS = PLAYER_WIDTH / 2.0

    fun check(start: Node, end: Node): Boolean {
        if (!end.type.isSmoothable) return false

        val startVec = start.toVec()
        val endVec = end.toVec()

        val dir = endVec.subtract(startVec)
        if (dir.lengthSqr() < 0.0001) return true

        val isWater = end.type == MovementType.WATER_WALK
        val requireGround = !isWater

        if (!isSafeRay(startVec, endVec, requireGround, isWater)) return false

        val perp = Vec3(-dir.z, 0.0, dir.x).normalize().multiply(RADIUS, RADIUS, RADIUS)

        val s1 = startVec.add(perp)
        val e1 = endVec.add(perp)
        val s2 = startVec.subtract(perp)
        val e2 = endVec.subtract(perp)

        if (!isSafeRay(s1, e1, requireGround, isWater)) return false
        if (!isSafeRay(s2, e2, requireGround, isWater)) return false

        return true
    }

    private fun isSafeRay(start: Vec3, end: Vec3, requireGround: Boolean, allowWater: Boolean): Boolean {
        if (start.distanceToSqr(end) > 256.0) return false

        var x = floor(start.x).toInt()
        var y = floor(start.y).toInt()
        var z = floor(start.z).toInt()

        val endX = floor(end.x).toInt()
        val endY = floor(end.y).toInt()
        val endZ = floor(end.z).toInt()

        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z

        val stepX = sign(dx).toInt()
        val stepY = sign(dy).toInt()
        val stepZ = sign(dz).toInt()

        val tDeltaX = if (dx == 0.0) Double.MAX_VALUE else abs(1.0 / dx)
        val tDeltaY = if (dy == 0.0) Double.MAX_VALUE else abs(1.0 / dy)
        val tDeltaZ = if (dz == 0.0) Double.MAX_VALUE else abs(1.0 / dz)

        var tMaxX = if (dx == 0.0) Double.MAX_VALUE else (if (stepX > 0) floor(start.x) + 1.0 - start.x else start.x - floor(start.x)) * tDeltaX
        var tMaxY = if (dy == 0.0) Double.MAX_VALUE else (if (stepY > 0) floor(start.y) + 1.0 - start.y else start.y - floor(start.y)) * tDeltaY
        var tMaxZ = if (dz == 0.0) Double.MAX_VALUE else (if (stepZ > 0) floor(start.z) + 1.0 - start.z else start.z - floor(start.z)) * tDeltaZ

        val mutablePos = BlockPos.MutableBlockPos()

        while (true) {
            mutablePos.set(x, y, z)

            if (!isPassable(mutablePos, allowWater)) return false

            mutablePos.setY(y + 1)
            if (!isPassable(mutablePos, allowWater)) return false

            if (requireGround) {
                mutablePos.setY(y - 1)
                if (!CollisionCache.isSolid(mutablePos)) return false
            }

            mutablePos.setY(y)
            if (CollisionCache.isDangerous(mutablePos)) return false

            mutablePos.setY(y - 1)
            if (CollisionCache.isDangerous(mutablePos)) return false

            if (x == endX && y == endY && z == endZ) break

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX
                    tMaxX += tDeltaX
                } else {
                    z += stepZ
                    tMaxZ += tDeltaZ
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += stepY
                    tMaxY += tDeltaY
                } else {
                    z += stepZ
                    tMaxZ += tDeltaZ
                }
            }
        }

        return true
    }

    private fun isPassable(pos: BlockPos, allowWater: Boolean): Boolean {
        if (CollisionCache.isPassable(pos)) return true
        if (allowWater && CollisionCache.hasState(pos, CollisionCache.WATER)) return true

        return false
    }
}