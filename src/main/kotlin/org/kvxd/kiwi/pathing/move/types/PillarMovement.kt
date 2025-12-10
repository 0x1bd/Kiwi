package org.kvxd.kiwi.pathing.move.types

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.config.ConfigData
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement

object PillarMovement : AbstractMovement(MovementType.PILLAR) {

    private const val COST = 6.0

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        if (!ConfigData.allowPillar) return

        val dest = current.pos.above()

        if (CollisionCache.isPassable(dest) && CollisionCache.isPassable(dest.above())) {
            output.append(dest, current, target, COST)
        }
    }
}