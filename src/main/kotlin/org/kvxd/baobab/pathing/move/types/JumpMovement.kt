package org.kvxd.baobab.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.baobab.pathing.calc.MovementType
import org.kvxd.baobab.pathing.calc.Node
import org.kvxd.baobab.pathing.move.MovementStrategy
import org.kvxd.baobab.pathing.move.Physics

object JumpMovement : MovementStrategy {

    private val CARDINALS = Direction.Type.HORIZONTAL.toList()

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos

        if (Physics.isSolid(start.up(2))) return

        for (dir in CARDINALS) {
            val wall = start.offset(dir)
            val dest = wall.up()

            if (Physics.isSolid(wall) && Physics.isWalkable(dest)) {
                output.add(createNode(dest, current, target, MovementType.JUMP, 2.0))
            }
        }
    }
}