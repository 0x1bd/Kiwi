package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.MovementStrategy

object JumpMovement : MovementStrategy {

    private val CARDINALS = Direction.Type.HORIZONTAL.toList()

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos

        if (CollisionCache.isSolid(start.up(2))) return

        for (dir in CARDINALS) {
            val wall = start.offset(dir)
            val dest = wall.up()

            if (CollisionCache.isSolid(wall) && CollisionCache.isWalkable(dest)) {
                output.add(createNode(dest, current, target, MovementType.JUMP, 2.0))
            }
        }
    }
}