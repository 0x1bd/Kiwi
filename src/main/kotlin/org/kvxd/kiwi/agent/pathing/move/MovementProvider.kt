package org.kvxd.kiwi.agent.pathing.move

import net.minecraft.core.BlockPos
import org.kvxd.kiwi.agent.pathing.calc.MovementType
import org.kvxd.kiwi.agent.pathing.calc.Node
import org.kvxd.kiwi.agent.pathing.move.types.DropMovement
import org.kvxd.kiwi.agent.pathing.move.types.PillarMovement
import org.kvxd.kiwi.agent.pathing.move.types.TravelMovement

object MovementProvider {

    private val STRATEGIES = listOf(
        TravelMovement,
        DropMovement,
        PillarMovement
    )

    fun getNeighbors(current: Node, target: BlockPos, buffer: MutableList<Node>) {
        for (strategy in STRATEGIES) {
            strategy.getNeighbors(current, target, buffer)
        }
    }

    fun getStartNode(start: BlockPos, heuristic: Double): Node {
        val initialPillarBlocks = PathInventoryTracker.initialPillarBlocks()
        for (strategy in STRATEGIES) {
            val node = strategy.getStartNode(start)
            if (node != null) {
                node.costH = heuristic
                applyInitialPillarBlocks(node, initialPillarBlocks)
                return node
            }
        }

        return Node(start, null, 0.0, heuristic, MovementType.TRAVEL, pillarBlocks = initialPillarBlocks)
    }

    private fun applyInitialPillarBlocks(node: Node, pillarBlocks: Int) {
        var current: Node? = node
        while (current != null) {
            current.pillarBlocks = pillarBlocks
            current = current.parent
        }
    }
}
