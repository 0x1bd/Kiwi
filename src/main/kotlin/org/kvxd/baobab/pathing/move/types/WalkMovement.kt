package org.kvxd.baobab.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.baobab.pathing.calc.Node
import org.kvxd.baobab.pathing.move.MovementStrategy
import org.kvxd.baobab.pathing.move.Physics

object WalkMovement : MovementStrategy {

    private val CARDINALS = Direction.Type.HORIZONTAL.toList()

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        for (dir in CARDINALS) {
            val dest = current.pos.offset(dir)

            if (Physics.isWalkable(dest)) {
                output.add(createNode(dest, current, target, 1.0))
            }
        }
    }
}