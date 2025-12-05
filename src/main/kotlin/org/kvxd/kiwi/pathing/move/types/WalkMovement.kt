package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement

object WalkMovement : AbstractMovement(MovementType.WALK) {

    private const val COST = 1.0

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos

        for (dir in Direction.Type.HORIZONTAL) {
            val dest = start.offset(dir)
            addIfValid(current, target, dest, output)
        }
    }

    override fun getCost(current: Node, dest: BlockPos): Double {
        if (CollisionCache.isWalkable(dest)) {
            return COST
        }

        return Double.POSITIVE_INFINITY
    }
}