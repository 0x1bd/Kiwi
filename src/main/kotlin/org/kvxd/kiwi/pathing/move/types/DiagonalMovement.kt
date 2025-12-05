package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement

object DiagonalMovement : AbstractMovement(MovementType.DIAGONAL) {

    private const val COST = 1.4142

    private val OFFSETS = arrayOf(
        1 to 1, 1 to -1, -1 to 1, -1 to -1
    )

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos

        for ((dx, dz) in OFFSETS) {
            val dest = start.add(dx, 0, dz)
            addIfValid(current, target, dest, output)
        }
    }

    override fun getCost(current: Node, dest: BlockPos): Double {
        if (!CollisionCache.isWalkable(dest)) return Double.POSITIVE_INFINITY

        if (CollisionCache.isSolid(current.pos.x, current.pos.y, dest.z) ||
            CollisionCache.isSolid(current.pos.x, current.pos.y + 1, dest.z)) {
            return Double.POSITIVE_INFINITY
        }

        if (CollisionCache.isSolid(dest.x, current.pos.y, current.pos.z) ||
            CollisionCache.isSolid(dest.x, current.pos.y + 1, current.pos.z)) {
            return Double.POSITIVE_INFINITY
        }

        return COST
    }
}