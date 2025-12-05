package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.MovementStrategy

object JumpMovement : MovementStrategy {

    const val COST = 2.0

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos

        if (CollisionCache.isSolid(start.up(2))) return

        Direction.Type.HORIZONTAL.forEach { dir ->
            val wall = start.offset(dir)

            if (CollisionCache.isSolid(wall)) {
                val dest = wall.up()

                if (CollisionCache.isWalkable(dest)) {
                    output += createNode(dest, current, target, MovementType.JUMP, COST)
                }
            }
        }
    }
}