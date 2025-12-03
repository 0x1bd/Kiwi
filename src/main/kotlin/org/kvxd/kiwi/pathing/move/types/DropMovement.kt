package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.MovementStrategy
import org.kvxd.kiwi.pathing.move.Physics

object DropMovement : MovementStrategy {
    private val CARDINALS = Direction.Type.HORIZONTAL.toList()

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos

        for (dir in CARDINALS) {
            val ledge = start.offset(dir)

            if (Physics.isSolid(ledge) || Physics.isSolid(ledge.up())) continue

            for (i in 1..ConfigManager.data.maxFallHeight) {
                val land = ledge.down(i)

                var obstructed = false

                for (j in 1 until i) {
                    if (Physics.isSolid(ledge.down(j))) {
                        obstructed = true
                        break
                    }
                }
                if (obstructed) break

                if (Physics.isWalkable(land)) {
                    val cost = 1.5 + (i * 0.5)
                    output.add(createNode(land, current, target, MovementType.DROP, cost))
                    break
                }
            }
        }
    }
}