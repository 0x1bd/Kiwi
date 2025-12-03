package org.kvxd.kiwi.pathing.move

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.types.DiagonalMovement
import org.kvxd.kiwi.pathing.move.types.DropMovement
import org.kvxd.kiwi.pathing.move.types.JumpMovement
import org.kvxd.kiwi.pathing.move.types.WalkMovement

object MovementProvider {

    private val STRATEGIES = listOf(
        WalkMovement,
        DiagonalMovement,
        JumpMovement,
        DropMovement
    )

    fun getNeighbors(current: Node, target: BlockPos): List<Node> {
        val nodes = ArrayList<Node>()

        for (strategy in STRATEGIES) {
            strategy.getNeighbors(current, target, nodes)
        }

        return nodes
    }
}