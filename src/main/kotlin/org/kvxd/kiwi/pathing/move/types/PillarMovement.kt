package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.AbstractMovement

object PillarMovement : AbstractMovement(MovementType.PILLAR) {

    private const val COST = 6.0

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val dest = current.pos.up()

        addIfValid(current, target, dest, output)
    }

    override fun getCost(current: Node, dest: BlockPos): Double {
        if (CollisionCache.isSolid(dest)) return Double.POSITIVE_INFINITY
        if (CollisionCache.isSolid(dest.up())) return Double.POSITIVE_INFINITY

        return COST
    }
}