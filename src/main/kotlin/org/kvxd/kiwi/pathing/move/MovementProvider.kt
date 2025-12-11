package org.kvxd.kiwi.pathing.move

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.pathing.calc.MovementType
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

    fun getStartNode(start: BlockPos, heuristic: Double): Node {
        for (strategy in STRATEGIES) {
            val node = strategy.getStartNode(start)
            if (node != null) {
                node.costH = heuristic
                return node
            }
        }

        return Node(start, null, 0.0, heuristic, MovementType.TRAVEL)
    }
}