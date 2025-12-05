package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.MovementStrategy

object PillarMovement : MovementStrategy {

    const val COST = JumpMovement.COST + 4.0

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val dest = current.pos.up()

        if (!CollisionCache.isSolid(dest) &&
            !CollisionCache.isSolid(dest.up())
        ) {
            output.add(createNode(dest, current, target, MovementType.PILLAR, COST))
        }
    }
}