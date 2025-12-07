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
        for (dir in Direction.Type.HORIZONTAL) {
            val dest = current.pos.offset(dir)

            if (CollisionCache.isWalkable(dest)) {
                output.append(dest, current, target, COST)
            }
        }
    }
}