package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.MovementStrategy
import org.kvxd.kiwi.pathing.move.Physics

object DiagonalMovement : MovementStrategy {

    private val OFFSETS = listOf(
        Pair(1, 1),   // SE
        Pair(1, -1),  // NE
        Pair(-1, 1),  // SW
        Pair(-1, -1)  // NW
    )

    private const val COST = 1.4142

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos

        for ((dx, dz) in OFFSETS) {
            val dest = start.add(dx, 0, dz)

            if (Physics.isWalkable(dest)) {
                val neighborX = start.add(dx, 0, 0)
                val neighborZ = start.add(0, 0, dz)

                val obstructedX = isObstructed(neighborX)
                val obstructedZ = isObstructed(neighborZ)

                if (!obstructedX && !obstructedZ) {
                    output.add(createNode(dest, current, target, COST))
                }
            }
        }
    }

    private fun isObstructed(pos: BlockPos): Boolean {
        return Physics.isSolid(pos) || Physics.isSolid(pos.up())
    }
}