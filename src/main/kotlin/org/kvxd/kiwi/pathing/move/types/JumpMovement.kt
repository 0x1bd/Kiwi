package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement

object JumpMovement : AbstractMovement(MovementType.JUMP) {

    private const val COST = 2.0

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos

        if (CollisionCache.isSolid(start.up(2))) return

        for (dir in Direction.Type.HORIZONTAL) {
            val dest = start.offset(dir).up()
            addIfValid(current, target, dest, output)
        }
    }

    override fun getCost(current: Node, dest: BlockPos): Double {
        if (!CollisionCache.isWalkable(dest)) return Double.POSITIVE_INFINITY

        if (!CollisionCache.isSolid(dest.down())) return Double.POSITIVE_INFINITY

        return COST
    }
}