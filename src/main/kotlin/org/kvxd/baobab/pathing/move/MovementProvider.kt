package org.kvxd.baobab.pathing.move

import net.minecraft.util.math.BlockPos
import org.kvxd.baobab.pathing.calc.Node
import org.kvxd.baobab.pathing.move.types.DiagonalMovement
import org.kvxd.baobab.pathing.move.types.DropMovement
import org.kvxd.baobab.pathing.move.types.JumpMovement
import org.kvxd.baobab.pathing.move.types.WalkMovement

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