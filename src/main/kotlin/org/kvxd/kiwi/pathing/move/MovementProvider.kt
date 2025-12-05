package org.kvxd.kiwi.pathing.move

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.types.DiagonalMovement
import org.kvxd.kiwi.pathing.move.types.DropMovement
import org.kvxd.kiwi.pathing.move.types.JumpMovement
import org.kvxd.kiwi.pathing.move.types.PillarMovement
import org.kvxd.kiwi.pathing.move.types.WalkMovement

object MovementProvider {

    private val STRATEGIES = listOf(
        WalkMovement,
        DiagonalMovement,
        JumpMovement,
        DropMovement,
        PillarMovement
    )

    fun getNeighbors(current: Node, target: BlockPos, buffer: MutableList<Node>) {
        for (i in STRATEGIES.indices) {
            STRATEGIES[i].getNeighbors(current, target, buffer)
        }
    }
}