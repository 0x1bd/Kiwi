package org.kvxd.kiwi.pathing.move.types

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.kvxd.kiwi.config.ConfigManager
import org.kvxd.kiwi.pathing.cache.CollisionCache
import org.kvxd.kiwi.pathing.calc.MovementType
import org.kvxd.kiwi.pathing.calc.Node
import org.kvxd.kiwi.pathing.move.MovementStrategy

object DropMovement : MovementStrategy {

    const val BASE_COST = 1.5

    override fun getNeighbors(current: Node, target: BlockPos, output: MutableList<Node>) {
        val start = current.pos

        Direction.Type.HORIZONTAL.forEach { dir ->
            val ledge = start.offset(dir)

            if (CollisionCache.isSolid(ledge) || CollisionCache.isSolid(ledge.up())) return@forEach

            for (i in 1..ConfigManager.data.maxFallHeight) {
                val land = ledge.down(i)

                if (CollisionCache.isWalkable(land)) {
                    val cost = BASE_COST + (i * 0.5)
                    output += createNode(land, current, target, MovementType.DROP, cost)
                    break
                }

                if (CollisionCache.isSolid(land)) {
                    break
                }
            }
        }
    }
}