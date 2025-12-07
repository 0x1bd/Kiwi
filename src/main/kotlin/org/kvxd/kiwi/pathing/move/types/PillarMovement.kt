package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement

object PillarMovement : AbstractMovement(MovementType.PILLAR) {

    private const val COST = 6.0

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        if (!ConfigManager.data.allowPillar) return

        val dest = current.pos.up()

        if (CollisionCache.isPassable(dest) && CollisionCache.isPassable(dest.up())) {
            output.append(dest, current, target, COST)
        }
    }
}