package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.MovementStrategy

object WalkMovement : MovementStrategy {

    private val CARDINALS = Direction.Type.HORIZONTAL.toList()

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        for (dir in CARDINALS) {
            val dest = current.pos.offset(dir)

            if (CollisionCache.isWalkable(dest)) {
                output.add(createNode(dest, current, target, MovementType.WALK, 1.0))
            }
        }
    }
}