package org.kvxd.kiwi.pathing.move

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.types.DropMovement
import org.kvxd.kiwi.pathing.move.types.MineMovement
import org.kvxd.kiwi.pathing.move.types.PillarMovement
import org.kvxd.kiwi.pathing.move.types.TravelMovement

object MovementProvider {

    private val STRATEGIES = listOf(
        TravelMovement,
        DropMovement,
        PillarMovement,
        MineMovement
    )

    fun getNeighbors(current: Node, target: BlockPos, buffer: MutableList<Node>) {
        for (strategy in STRATEGIES) {
            strategy.getNeighbors(current, target, buffer)
        }
    }
}